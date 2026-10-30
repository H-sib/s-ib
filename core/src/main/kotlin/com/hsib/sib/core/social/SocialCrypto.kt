package com.hsib.sib.core.social

import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import com.google.crypto.tink.subtle.XChaCha20Poly1305
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64

enum class Purpose(val tag: String) { PAYLOAD("payload"), MESSAGE("message"), ROOM_KEY_WRAP("wrap"), ROOM_KEY_INVITE("invite") }

enum class OpenError { MALFORMED, UNSUPPORTED_VERSION, WRONG_KEY_VERSION, AUTH_FAILED, TOO_LARGE }

sealed interface Opened<out T> {
    class Ok<T>(val value: T) : Opened<T>
    class Err(val error: OpenError) : Opened<Nothing>
}

/**
 * Everything that is bound into a sealed value as associated data. A value sealed for one room, field, sender or message
 * fails to open if it is moved to another, so the server (or a member) cannot replay, relabel or re-home it.
 */
class SealContext(
    val roomId: String,
    val purpose: Purpose,
    val keyVersion: Int,
    /** The message id for chat, or the person a wrapped key is for. */
    val itemId: String = "",
    val senderUid: String = "",
) {
    internal fun aad(): ByteArray {
        val out = ByteArrayOutputStream()
        for (part in listOf("sib/social/v1", roomId, purpose.tag, itemId, senderUid, keyVersion.toString())) {
            val b = part.toByteArray(Charsets.UTF_8)
            require(b.size < 65_536) { "context value too long" }
            out.write(ByteBuffer.allocate(2).putShort(b.size.toShort()).array())
            out.write(b)
        }
        return out.toByteArray()
    }
}

/** The 256 bit key one room's deadline details and messages are sealed with. */
class RoomKey(val bytes: ByteArray) {
    init { require(bytes.size == SIZE) { "a room key is $SIZE bytes" } }

    /** For storage in the encrypted database. */
    fun encode(): String = b64(bytes)

    companion object {
        const val SIZE = 32
        fun generate(random: SecureRandom = SecureRandom()) = RoomKey(ByteArray(SIZE).also { random.nextBytes(it) })
        fun decode(text: String): RoomKey? = unb64(text)?.takeIf { it.size == SIZE }?.let(::RoomKey)
    }
}

private val b64e = Base64.getUrlEncoder().withoutPadding()
private val b64d = Base64.getUrlDecoder()

internal fun b64(bytes: ByteArray): String = b64e.encodeToString(bytes)
internal fun unb64(text: String): ByteArray? = try { b64d.decode(text) } catch (_: IllegalArgumentException) { null }

/**
 * Seals and opens the text a room keeps on the server: the deadline's details and every chat message.
 * XChaCha20-Poly1305 with a fresh random nonce, the room, field, sender and message bound in as associated data, and the
 * plaintext padded to a multiple of 64 bytes so a stored value reveals little about how long it is.
 */
object SocialCrypto {
    private const val VERSION = 1
    private const val BUCKET = 64
    private const val NONCE = 24
    private const val TAG = 16
    private const val HEADER = 1 + 4

    fun seal(key: RoomKey, ctx: SealContext, plaintext: ByteArray): String {
        require(plaintext.size <= SocialLimits.MAX_SEALED_BYTES) { "too large to seal" }
        val framed = ByteBuffer.allocate(4 + plaintext.size).putInt(plaintext.size).put(plaintext).array()
        val padded = framed.copyOf(((framed.size + BUCKET - 1) / BUCKET) * BUCKET)
        val ciphertext = XChaCha20Poly1305(key.bytes).encrypt(padded, ctx.aad())
        return b64(ByteBuffer.allocate(HEADER + ciphertext.size).put(VERSION.toByte()).putInt(ctx.keyVersion).put(ciphertext).array())
    }

    fun sealText(key: RoomKey, ctx: SealContext, text: String): String = seal(key, ctx, text.toByteArray(Charsets.UTF_8))

    /** Which room key version a sealed value needs, so the right key can be picked before opening it. Null if malformed. */
    fun keyVersionOf(envelope: String): Int? {
        if (envelope.length > SocialLimits.MAX_ENVELOPE_CHARS) return null
        val raw = unb64(envelope) ?: return null
        return if (raw.size >= HEADER) ByteBuffer.wrap(raw, 1, 4).int else null
    }

    fun open(key: RoomKey, ctx: SealContext, envelope: String): Opened<ByteArray> {
        if (envelope.length > SocialLimits.MAX_ENVELOPE_CHARS) return Opened.Err(OpenError.TOO_LARGE)
        val raw = unb64(envelope) ?: return Opened.Err(OpenError.MALFORMED)
        if (raw.size < HEADER + NONCE + TAG + BUCKET) return Opened.Err(OpenError.MALFORMED)
        if (raw[0].toInt() != VERSION) return Opened.Err(OpenError.UNSUPPORTED_VERSION)
        if (ByteBuffer.wrap(raw, 1, 4).int != ctx.keyVersion) return Opened.Err(OpenError.WRONG_KEY_VERSION)
        val padded = try {
            XChaCha20Poly1305(key.bytes).decrypt(raw.copyOfRange(HEADER, raw.size), ctx.aad())
        } catch (_: Exception) {
            return Opened.Err(OpenError.AUTH_FAILED)
        }
        if (padded.size < 4 || padded.size % BUCKET != 0) return Opened.Err(OpenError.MALFORMED)
        val len = ByteBuffer.wrap(padded, 0, 4).int
        if (len < 0 || len > padded.size - 4) return Opened.Err(OpenError.MALFORMED)
        for (i in 4 + len until padded.size) if (padded[i].toInt() != 0) return Opened.Err(OpenError.MALFORMED)
        return Opened.Ok(padded.copyOfRange(4, 4 + len))
    }

    fun openText(key: RoomKey, ctx: SealContext, envelope: String): Opened<String> = when (val r = open(key, ctx, envelope)) {
        is Opened.Ok -> Opened.Ok(String(r.value, Charsets.UTF_8))
        is Opened.Err -> r
    }
}

/** A phone's own key pair. The public half is published in the profile; the private half never leaves the phone and the backup. */
class DeviceKeyPair(val privateKey: ByteArray, val publicKey: ByteArray) {
    init { require(privateKey.size == 32 && publicKey.size == 32) }

    fun publicText(): String = b64(publicKey)

    /** For storage in the encrypted database and the encrypted backup. */
    fun encode(): String = b64(privateKey) + "." + b64(publicKey)

    companion object {
        fun generate(): DeviceKeyPair {
            val priv = X25519.generatePrivateKey()
            return DeviceKeyPair(priv, X25519.publicFromPrivate(priv))
        }

        fun decode(text: String): DeviceKeyPair? {
            val parts = text.split(".")
            if (parts.size != 2) return null
            val priv = unb64(parts[0])?.takeIf { it.size == 32 } ?: return null
            val pub = unb64(parts[1])?.takeIf { it.size == 32 } ?: return null
            return if (runCatching { X25519.publicFromPrivate(priv) }.getOrNull()?.contentEquals(pub) == true) DeviceKeyPair(priv, pub) else null
        }

        fun publicFromText(text: String): ByteArray? = unb64(text)?.takeIf { it.size == 32 }
    }
}

/**
 * A room key sealed for one member's public key (an ephemeral X25519 exchange, HKDF, XChaCha20-Poly1305). Only that
 * member's private key opens it. Removing a member means making a new room key and sealing it again for everyone left.
 */
object KeyWrap {
    private const val VERSION = 1
    private val INFO = "sib/social/v1/wrap".toByteArray(Charsets.US_ASCII)

    private fun ctx(roomId: String, keyVersion: Int, memberUid: String) = SealContext(roomId, Purpose.ROOM_KEY_WRAP, keyVersion, itemId = memberUid)

    private fun derive(shared: ByteArray, ephemeralPublic: ByteArray, recipientPublic: ByteArray): ByteArray =
        Hkdf.computeHkdf("HmacSha256", shared, ephemeralPublic + recipientPublic, INFO, 32)

    /** A low order public key makes the shared secret all zeros, which anyone could compute. Never accept that. */
    private fun sharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val shared = X25519.computeSharedSecret(privateKey, publicKey)
        if (shared.all { it.toInt() == 0 }) throw IllegalArgumentException("unusable public key")
        return shared
    }

    /** Null if the recipient's public key is not usable (for example a low order point sent by a hostile profile). */
    fun wrap(roomKey: RoomKey, recipientPublic: ByteArray, roomId: String, keyVersion: Int, memberUid: String): String? {
        if (recipientPublic.size != 32) return null
        return try {
            val eph = X25519.generatePrivateKey()
            val ephPublic = X25519.publicFromPrivate(eph)
            val key = derive(sharedSecret(eph, recipientPublic), ephPublic, recipientPublic)
            val sealed = XChaCha20Poly1305(key).encrypt(roomKey.bytes, ctx(roomId, keyVersion, memberUid).aad())
            b64(ByteBuffer.allocate(1 + 32 + sealed.size).put(VERSION.toByte()).put(ephPublic).put(sealed).array())
        } catch (_: Exception) {
            null
        }
    }

    fun unwrap(envelope: String, mine: DeviceKeyPair, roomId: String, keyVersion: Int, myUid: String): Opened<RoomKey> {
        if (envelope.length > 512) return Opened.Err(OpenError.TOO_LARGE)
        val raw = unb64(envelope) ?: return Opened.Err(OpenError.MALFORMED)
        if (raw.size != 1 + 32 + 24 + RoomKey.SIZE + 16) return Opened.Err(OpenError.MALFORMED)
        if (raw[0].toInt() != VERSION) return Opened.Err(OpenError.UNSUPPORTED_VERSION)
        return try {
            val ephPublic = raw.copyOfRange(1, 33)
            val key = derive(sharedSecret(mine.privateKey, ephPublic), ephPublic, mine.publicKey)
            Opened.Ok(RoomKey(XChaCha20Poly1305(key).decrypt(raw.copyOfRange(33, raw.size), ctx(roomId, keyVersion, myUid).aad())))
        } catch (_: Exception) {
            Opened.Err(OpenError.AUTH_FAILED)
        }
    }

    /** One wrapped key per member whose public key is usable. Members with an unusable key are left out of the result. */
    fun wrapForAll(roomKey: RoomKey, members: Map<String, ByteArray>, roomId: String, keyVersion: Int): Map<String, String> =
        members.mapNotNull { (uid, pub) -> wrap(roomKey, pub, roomId, keyVersion, uid)?.let { uid to it } }.toMap()
}

/** The room key sealed with an invite's secret, stored on the server inside the invite. */
object InviteSeal {
    private const val VERSION = 1

    private fun ctx(code: InviteCode, roomId: String, keyVersion: Int) = SealContext(roomId, Purpose.ROOM_KEY_INVITE, keyVersion, itemId = code.id)

    fun seal(roomKey: RoomKey, code: InviteCode, roomId: String, keyVersion: Int): String {
        val sealed = XChaCha20Poly1305(code.key()).encrypt(roomKey.bytes, ctx(code, roomId, keyVersion).aad())
        return b64(ByteBuffer.allocate(5 + sealed.size).put(VERSION.toByte()).putInt(keyVersion).put(sealed).array())
    }

    /** The room key and the key version it belongs to. */
    fun open(envelope: String, code: InviteCode, roomId: String): Opened<Pair<RoomKey, Int>> {
        if (envelope.length > 512) return Opened.Err(OpenError.TOO_LARGE)
        val raw = unb64(envelope) ?: return Opened.Err(OpenError.MALFORMED)
        if (raw.size != 5 + 24 + RoomKey.SIZE + 16) return Opened.Err(OpenError.MALFORMED)
        if (raw[0].toInt() != VERSION) return Opened.Err(OpenError.UNSUPPORTED_VERSION)
        val keyVersion = ByteBuffer.wrap(raw, 1, 4).int
        return try {
            val key = XChaCha20Poly1305(code.key()).decrypt(raw.copyOfRange(5, raw.size), ctx(code, roomId, keyVersion).aad())
            Opened.Ok(RoomKey(key) to keyVersion)
        } catch (_: Exception) {
            Opened.Err(OpenError.AUTH_FAILED)
        }
    }
}

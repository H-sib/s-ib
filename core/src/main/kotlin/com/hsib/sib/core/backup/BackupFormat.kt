package com.hsib.sib.core.backup

import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.XChaCha20Poly1305
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class BackupError { BAD_MAGIC, UNSUPPORTED_VERSION, TRUNCATED, AUTH_FAILED, MALFORMED, TOO_LARGE }

sealed interface BackupResult<out T> {
    class Ok<T>(val value: T) : BackupResult<T>
    class Err(val error: BackupError) : BackupResult<Nothing>
}

/** What unlocks a backup: the random recovery key, or a passphrase run through PBKDF2. */
sealed interface Secret {
    class Key(val bytes: ByteArray) : Secret
    class Passphrase(val chars: CharArray) : Secret
}

class BackupHeader(
    val version: Int,
    val kdfId: Int,
    val iterations: Int,
    val salt: ByteArray,
    val backupId: ByteArray,
    val seq: Long,
    val createdAtEpochSec: Long,
    val prevHash: ByteArray,
    val chunkSize: Int,
) {
    fun toBytes(): ByteArray {
        val b = ByteBuffer.allocate(SIZE)
        b.put(BackupFormat.MAGIC.toByteArray(Charsets.US_ASCII))
        b.putShort(version.toShort()); b.put(kdfId.toByte()); b.putInt(iterations)
        b.put(salt); b.put(backupId); b.putLong(seq); b.putLong(createdAtEpochSec); b.put(prevHash); b.putInt(chunkSize)
        return b.array()
    }

    companion object {
        const val SIZE = 8 + 2 + 1 + 4 + 16 + 16 + 8 + 8 + 32 + 4

        fun parse(bytes: ByteArray): BackupResult<BackupHeader> {
            if (bytes.size < 8) return BackupResult.Err(BackupError.TRUNCATED)
            if (String(bytes, 0, 8, Charsets.US_ASCII) != BackupFormat.MAGIC) return BackupResult.Err(BackupError.BAD_MAGIC)
            if (bytes.size < SIZE) return BackupResult.Err(BackupError.TRUNCATED)
            val b = ByteBuffer.wrap(bytes, 8, SIZE - 8)
            val version = b.short.toInt() and 0xFFFF
            if (version != 1) return BackupResult.Err(BackupError.UNSUPPORTED_VERSION)
            val kdf = b.get().toInt() and 0xFF
            val iter = b.int
            val salt = ByteArray(16).also { b.get(it) }
            val id = ByteArray(16).also { b.get(it) }
            val seq = b.long
            val at = b.long
            val prev = ByteArray(32).also { b.get(it) }
            val chunk = b.int
            if (kdf !in 0..1 || chunk !in 1024..BackupFormat.MAX_CHUNK) return BackupResult.Err(BackupError.MALFORMED)
            if (kdf == 1 && iter !in BackupFormat.MIN_ITERATIONS..BackupFormat.MAX_ITERATIONS) return BackupResult.Err(BackupError.MALFORMED)
            if (seq < 0) return BackupResult.Err(BackupError.MALFORMED)
            return BackupResult.Ok(BackupHeader(version, kdf, iter, salt, id, seq, at, prev, chunk))
        }
    }
}

class DecodedBackup(val header: BackupHeader, val plaintext: ByteArray)

/**
 * Container for one encrypted snapshot.
 *
 * - Every byte of the header is bound into every chunk's associated data, so a changed header fails authentication.
 * - Each chunk carries its index and a "last chunk" flag in the associated data, so reordering, dropping chunks from
 *   the end, appending chunks and splicing chunks from another file all fail authentication.
 * - XChaCha20-Poly1305 with a fresh random 192 bit nonce per chunk: random nonces are safe at this size.
 * - Plaintext is compressed, then padded to a multiple of 4 KiB, so file size reveals little about the content.
 * - Parsing is bounded: chunk size, iteration count and decompressed size all have hard limits.
 */
object BackupFormat {
    const val MAGIC = "SIBBKUP1"
    const val CHUNK = 64 * 1024
    const val MAX_CHUNK = 1024 * 1024
    const val PAD = 4096
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 5_000_000
    const val DEFAULT_ITERATIONS = 600_000
    private const val MAX_PLAIN = 128L * 1024 * 1024

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ikm(secret: Secret, header: BackupHeader): ByteArray? = when {
        secret is Secret.Key && header.kdfId == 0 && secret.bytes.size == RecoveryKey.SIZE -> secret.bytes
        secret is Secret.Passphrase && header.kdfId == 1 ->
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(secret.chars, header.salt, header.iterations, 256)).encoded
        else -> null
    }

    private fun fileKey(ikm: ByteArray, header: BackupHeader): ByteArray =
        Hkdf.computeHkdf("HmacSha256", ikm, header.backupId, "sib/backup/v1/file".toByteArray(), 32)

    private fun aad(headerHash: ByteArray, index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(headerHash.size + 9).put(headerHash).putLong(index).put(if (last) 1 else 0).array()

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    fun encode(
        plaintext: ByteArray,
        secret: Secret,
        seq: Long,
        createdAtEpochSec: Long,
        prevHash: ByteArray,
        backupId: ByteArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = DEFAULT_ITERATIONS,
        chunkSize: Int = CHUNK,
    ): ByteArray {
        require(prevHash.size == 32 && backupId.size == 16)
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val header = BackupHeader(
            1, if (secret is Secret.Key) 0 else 1, if (secret is Secret.Key) 0 else iterations,
            salt, backupId, seq, createdAtEpochSec, prevHash, chunkSize,
        )
        val ikm = ikm(secret, header) ?: throw IllegalArgumentException("secret does not match the key derivation")
        val aead = XChaCha20Poly1305(fileKey(ikm, header))
        val gz = gzip(plaintext)
        val framed = ByteBuffer.allocate(4 + gz.size).putInt(gz.size).put(gz).array()
        val paddedLen = maxOf(PAD, ((framed.size + PAD - 1) / PAD) * PAD)
        val padded = framed.copyOf(paddedLen)
        val headerBytes = header.toBytes()
        val headerHash = sha256(headerBytes)
        val out = ByteArrayOutputStream()
        out.write(headerBytes)
        var offset = 0
        var index = 0L
        while (offset < padded.size) {
            val end = minOf(offset + chunkSize, padded.size)
            val sealed = aead.encrypt(padded.copyOfRange(offset, end), aad(headerHash, index, end == padded.size))
            out.write(ByteBuffer.allocate(4).putInt(sealed.size).array())
            out.write(sealed)
            offset = end
            index++
        }
        return out.toByteArray()
    }

    /** Reads the unauthenticated header. Useful for showing "backup 12 from Tuesday" before asking for the key. */
    fun peek(bytes: ByteArray): BackupResult<BackupHeader> = BackupHeader.parse(bytes)

    fun decode(bytes: ByteArray, secret: Secret): BackupResult<DecodedBackup> {
        val parsed = BackupHeader.parse(bytes)
        val header = when (parsed) {
            is BackupResult.Err -> return parsed
            is BackupResult.Ok -> parsed.value
        }
        val ikm = ikm(secret, header) ?: return BackupResult.Err(BackupError.AUTH_FAILED)
        val aead = XChaCha20Poly1305(fileKey(ikm, header))
        val headerHash = sha256(bytes.copyOfRange(0, BackupHeader.SIZE))
        var offset = BackupHeader.SIZE
        if (offset >= bytes.size) return BackupResult.Err(BackupError.TRUNCATED)
        val plain = ByteArrayOutputStream()
        var index = 0L
        while (offset < bytes.size) {
            if (bytes.size - offset < 4) return BackupResult.Err(BackupError.TRUNCATED)
            val len = ByteBuffer.wrap(bytes, offset, 4).int
            if (len < 24 + 16 + 1 || len > header.chunkSize + 40) return BackupResult.Err(BackupError.MALFORMED)
            if (bytes.size - offset - 4 < len) return BackupResult.Err(BackupError.TRUNCATED)
            val last = offset + 4 + len == bytes.size
            if (!last && len != header.chunkSize + 40) return BackupResult.Err(BackupError.MALFORMED)
            val chunk = try {
                aead.decrypt(bytes.copyOfRange(offset + 4, offset + 4 + len), aad(headerHash, index, last))
            } catch (_: Exception) {
                return BackupResult.Err(BackupError.AUTH_FAILED)
            }
            plain.write(chunk)
            if (plain.size() > MAX_PLAIN) return BackupResult.Err(BackupError.TOO_LARGE)
            offset += 4 + len
            index++
        }
        val padded = plain.toByteArray()
        if (padded.size < 4) return BackupResult.Err(BackupError.MALFORMED)
        val gzLen = ByteBuffer.wrap(padded, 0, 4).int
        if (gzLen < 0 || gzLen > padded.size - 4) return BackupResult.Err(BackupError.MALFORMED)
        for (i in 4 + gzLen until padded.size) if (padded[i].toInt() != 0) return BackupResult.Err(BackupError.MALFORMED)
        return try {
            val out = ByteArrayOutputStream()
            GZIPInputStream(ByteArrayInputStream(padded, 4, gzLen)).use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_PLAIN) return BackupResult.Err(BackupError.TOO_LARGE)
                }
            }
            BackupResult.Ok(DecodedBackup(header, out.toByteArray()))
        } catch (_: Exception) {
            BackupResult.Err(BackupError.MALFORMED)
        }
    }
}

sealed interface ChainCheck {
    data class Ok(val snapshots: Int, val newestSeq: Long) : ChainCheck
    data class Broken(val atSeq: Long, val why: String) : ChainCheck
}

/** Checks that a folder of snapshots forms an unbroken chain, which exposes deleted, swapped or rolled-back files. */
object BackupChain {
    fun verify(files: List<ByteArray>, expectLineage: ByteArray? = null): ChainCheck {
        val headers = files.map { f ->
            when (val h = BackupHeader.parse(f)) {
                is BackupResult.Ok -> h.value to f
                is BackupResult.Err -> return ChainCheck.Broken(-1, "a file is not a valid S-IB backup (${h.error})")
            }
        }.sortedBy { it.first.seq }
        if (headers.isEmpty()) return ChainCheck.Ok(0, -1)
        val lineage = expectLineage ?: headers.first().first.backupId
        for ((h, _) in headers) if (!h.backupId.contentEquals(lineage)) return ChainCheck.Broken(h.seq, "a snapshot belongs to a different backup lineage")
        for (i in 1 until headers.size) {
            val (h, _) = headers[i]
            val (ph, pf) = headers[i - 1]
            if (h.seq == ph.seq) return ChainCheck.Broken(h.seq, "two snapshots claim the same position")
            // Retention only ever removes the oldest snapshots, so a gap in the middle means one went missing.
            if (h.seq > ph.seq + 1) return ChainCheck.Broken(h.seq, "a snapshot is missing before snapshot ${h.seq}")
            if (h.seq == ph.seq + 1 && !h.prevHash.contentEquals(BackupFormat.sha256(pf))) {
                return ChainCheck.Broken(h.seq, "snapshot ${h.seq} does not follow the snapshot before it")
            }
        }
        return ChainCheck.Ok(headers.size, headers.last().first.seq)
    }
}

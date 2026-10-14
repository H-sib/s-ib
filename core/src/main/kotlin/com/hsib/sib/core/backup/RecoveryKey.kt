package com.hsib.sib.core.backup

import com.google.crypto.tink.subtle.Hkdf
import java.security.MessageDigest
import java.security.SecureRandom

/** Crockford base32: no I, L, O or U, so a written-down key survives being read aloud or copied by hand. */
object Base32 {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    /** Returns null for illegal characters or non-zero trailing bits. Dashes and spaces are ignored. */
    fun decode(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (raw in text) {
            if (raw == '-' || raw.isWhitespace()) continue
            val c = when (val u = raw.uppercaseChar()) {
                'I', 'L' -> '1'
                'O' -> '0'
                else -> u
            }
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        if (bits > 0 && buffer != 0) return null
        return out.toByteArray()
    }
}

sealed interface RecoveryDecode {
    class Ok(val key: ByteArray) : RecoveryDecode
    data object BadFormat : RecoveryDecode
    data object BadChecksum : RecoveryDecode
}

/**
 * The recovery key is 256 random bits shown as 14 groups of four characters. A three byte checksum catches
 * typing mistakes before any decryption is attempted, so a wrong key gives a precise message instead of silence.
 */
object RecoveryKey {
    const val SIZE = 32
    private const val CHECK = 3

    fun generate(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SIZE).also { random.nextBytes(it) }

    private fun checksum(key: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("sib-recovery-v1".toByteArray() + key).copyOf(CHECK)

    fun encode(key: ByteArray): String {
        require(key.size == SIZE)
        return Base32.encode(key + checksum(key)).chunked(4).joinToString("-")
    }

    fun decode(text: String): RecoveryDecode {
        val bytes = Base32.decode(text) ?: return RecoveryDecode.BadFormat
        if (bytes.size != SIZE + CHECK) return RecoveryDecode.BadFormat
        val key = bytes.copyOfRange(0, SIZE)
        val given = bytes.copyOfRange(SIZE, SIZE + CHECK)
        return if (MessageDigest.isEqual(checksum(key), given)) RecoveryDecode.Ok(key) else RecoveryDecode.BadChecksum
    }
}

/** Domain separation: every purpose gets its own key from the vault master, so one key can never stand in for another. */
object KeyHierarchy {
    fun derive(master: ByteArray, label: String, size: Int = 32): ByteArray =
        Hkdf.computeHkdf("HmacSha256", master, ByteArray(0), "sib/v1/$label".toByteArray(), size)

    fun databaseKey(master: ByteArray) = derive(master, "database")
    fun fingerprintKey(master: ByteArray) = derive(master, "dismissed-fingerprint")
    fun sealKey(master: ByteArray) = derive(master, "history-seal")
    fun blobKey(master: ByteArray) = derive(master, "attachment")
}

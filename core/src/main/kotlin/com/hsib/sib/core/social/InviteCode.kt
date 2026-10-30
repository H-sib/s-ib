package com.hsib.sib.core.social

import com.google.crypto.tink.subtle.Hkdf
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Crockford base 32: no I, L, O or U, so a code typed from a screenshot or read out loud is hard to get wrong.
 * A lower case letter is the same as its upper case one, O reads as 0, and I and L read as 1.
 */
object Crockford {
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** Spaces and dashes are dropped. Returns null if any other character is not in the alphabet. */
    fun normalise(text: String): String? {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val c = ch.uppercaseChar()
            when {
                c == ' ' || c == '-' || c == '\t' || c == '\n' || c == '\r' -> Unit
                c == 'O' -> sb.append('0')
                c == 'I' || c == 'L' -> sb.append('1')
                ALPHABET.indexOf(c) >= 0 -> sb.append(c)
                else -> return null
            }
        }
        return sb.toString()
    }

    fun random(chars: Int, random: SecureRandom = SecureRandom()): String {
        val sb = StringBuilder(chars)
        repeat(chars) { sb.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }
}

/**
 * What a person sends to a friend to add them to a shared deadline: "SIB-" and eight groups of four characters.
 *
 * - The first ten characters are the invite's public id. The server stores the invite under it.
 * - The next twenty (100 bits) are a secret that never reaches the server. It seals the room key inside the invite, so the
 *   server cannot read it and the person who made the invite does not have to be online when the friend joins.
 * - The last two are a checksum, so a mistyped character is caught on the phone before anything is sent.
 */
class InviteCode private constructor(val id: String, val secret: String) {
    fun text(): String = PREFIX + (id + secret + checkOf(id, secret)).chunked(4).joinToString("-")

    /** The key that seals the room key inside the invite. */
    fun key(): ByteArray = Hkdf.computeHkdf("HmacSha256", secret.toByteArray(Charsets.US_ASCII), id.toByteArray(Charsets.US_ASCII), INFO, 32)

    override fun toString() = "InviteCode($id)"

    companion object {
        const val ID_CHARS = 10
        const val SECRET_CHARS = 20
        private const val CHECK_CHARS = 2
        private const val PREFIX = "SIB-"
        private val INFO = "sib/social/v1/invite".toByteArray(Charsets.US_ASCII)

        fun generate(random: SecureRandom = SecureRandom()): InviteCode =
            InviteCode(Crockford.random(ID_CHARS, random), Crockford.random(SECRET_CHARS, random))

        private fun checkOf(id: String, secret: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(("sib/invite/check/$id$secret").toByteArray(Charsets.US_ASCII))
            val bits = ((d[0].toInt() and 0xFF) shl 8) or (d[1].toInt() and 0xFF)
            return "" + Crockford.ALPHABET[(bits shr 11) and 31] + Crockford.ALPHABET[(bits shr 6) and 31]
        }

        /** Reads a code as typed or pasted, with or without the prefix and dashes. Null if it is not a well formed code. */
        fun parse(text: String): InviteCode? {
            var t = text.trim().uppercase()
            if (t.length > 120) return null
            if (t.startsWith(PREFIX) || t.startsWith("SIB ")) t = t.substring(PREFIX.length)
            val n = Crockford.normalise(t) ?: return null
            val body = if (n.length == ID_CHARS + SECRET_CHARS + CHECK_CHARS + 3 && n.startsWith("S1B")) n.substring(3) else n
            if (body.length != ID_CHARS + SECRET_CHARS + CHECK_CHARS) return null
            val id = body.substring(0, ID_CHARS)
            val secret = body.substring(ID_CHARS, ID_CHARS + SECRET_CHARS)
            if (body.substring(ID_CHARS + SECRET_CHARS) != checkOf(id, secret)) return null
            return InviteCode(id, secret)
        }
    }
}

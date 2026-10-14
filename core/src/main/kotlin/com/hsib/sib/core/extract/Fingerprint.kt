package com.hsib.sib.core.extract

import java.security.MessageDigest
import java.time.LocalDate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A fingerprint lets the app remember "the user dismissed this" without storing the message text.
 * The keyed variant makes the stored hash useless without the vault key.
 */
interface Fingerprinter {
    fun of(key: String): String
}

object PlainFingerprinter : Fingerprinter {
    override fun of(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}

class HmacFingerprinter(private val key: ByteArray) : Fingerprinter {
    override fun of(key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(this.key, "HmacSHA256"))
        return mac.doFinal(key.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}

/** Normalised identity of a suggestion: the informative words of the title, sorted, plus the day. */
fun fingerprintKey(title: String, day: LocalDate?): String =
    Linker.tokens(title).sorted().joinToString(" ") + "|" + (day?.toString() ?: "none")

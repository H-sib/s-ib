package com.hsib.sib.core

import com.google.crypto.tink.subtle.XChaCha20Poly1305

object Hello {
    fun roundTrip(): Boolean {
        val key = ByteArray(32) { it.toByte() }
        val aead = XChaCha20Poly1305(key)
        val ct = aead.encrypt("hi".toByteArray(), "aad".toByteArray())
        return String(aead.decrypt(ct, "aad".toByteArray())) == "hi"
    }
}

package com.hsib.sib.core.seal

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * One line of a deadline's history. Each entry's hash covers its own content and the previous entry's hash, keyed
 * with a vault-derived key, so editing, removing or reordering an earlier entry breaks every hash after it.
 *
 * Honest scope: this detects accidental corruption and partial or careless tampering. Someone who holds the vault
 * key (the running app itself) can still rewrite the whole chain, which is why backups also carry the chain head.
 */
data class SealEntry(
    val seq: Int,
    val atEpochSec: Long,
    val field: String,
    val oldValue: String,
    val newValue: String,
    val actor: String,
    val reason: String,
    val prevHash: String,
    val hash: String,
)

sealed interface SealCheck {
    data class Ok(val entries: Int, val head: String) : SealCheck
    data class Broken(val atSeq: Int, val why: String) : SealCheck
}

object Seal {
    val GENESIS: String = "0".repeat(64)

    private fun field(s: String) = "${s.length}:$s"

    private fun canonical(seq: Int, at: Long, field: String, old: String, new: String, actor: String, reason: String, prev: String): ByteArray =
        listOf(seq.toString(), at.toString(), field(field), field(old), field(new), field(actor), field(reason), prev)
            .joinToString("|").toByteArray(Charsets.UTF_8)

    private fun mac(key: ByteArray, data: ByteArray): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    fun append(
        key: ByteArray,
        previous: SealEntry?,
        atEpochSec: Long,
        field: String,
        oldValue: String,
        newValue: String,
        actor: String,
        reason: String,
    ): SealEntry {
        val seq = (previous?.seq ?: -1) + 1
        val prev = previous?.hash ?: GENESIS
        val hash = mac(key, canonical(seq, atEpochSec, field, oldValue, newValue, actor, reason, prev))
        return SealEntry(seq, atEpochSec, field, oldValue, newValue, actor, reason, prev, hash)
    }

    fun verify(key: ByteArray, entries: List<SealEntry>): SealCheck {
        var prevHash = GENESIS
        entries.sortedBy { it.seq }.forEachIndexed { i, e ->
            if (e.seq != i) return SealCheck.Broken(e.seq, "an entry is missing or out of order")
            if (e.prevHash != prevHash) return SealCheck.Broken(e.seq, "the chain does not link to the entry before it")
            val expected = mac(key, canonical(e.seq, e.atEpochSec, e.field, e.oldValue, e.newValue, e.actor, e.reason, e.prevHash))
            if (!constantTimeEquals(expected, e.hash)) return SealCheck.Broken(e.seq, "the entry was changed after it was written")
            prevHash = e.hash
        }
        return SealCheck.Ok(entries.size, prevHash)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }
}

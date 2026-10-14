package com.hsib.sib.core.backup

import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.seal.Seal
import com.hsib.sib.core.seal.SealCheck
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random

class BackupTest {
    private val key = ByteArray(32) { (it * 7 + 3).toByte() }
    private val id = ByteArray(16) { (it + 1).toByte() }
    private val zero = ByteArray(32)
    private val plain = ("S-IB backup test payload " + "x".repeat(300)).toByteArray()

    private fun encode(p: ByteArray = plain, seq: Long = 0, prev: ByteArray = zero, chunk: Int = BackupFormat.CHUNK, secret: Secret = Secret.Key(key), backupId: ByteArray = id) =
        BackupFormat.encode(p, secret, seq, 1_700_000_000L, prev, backupId, SecureRandom(), BackupFormat.MIN_ITERATIONS, chunk)

    private fun decode(b: ByteArray, secret: Secret = Secret.Key(key)) = BackupFormat.decode(b, secret)

    private fun ok(r: BackupResult<DecodedBackup>) = (r as BackupResult.Ok).value

    private fun error(r: BackupResult<DecodedBackup>) = (r as BackupResult.Err).error

    // ---- round trips --------------------------------------------------------------------------

    @Test fun roundTripWithRecoveryKey() {
        val enc = encode(seq = 7)
        val dec = ok(decode(enc))
        assertArrayEquals(plain, dec.plaintext)
        assertEquals(7L, dec.header.seq)
        assertEquals(1_700_000_000L, dec.header.createdAtEpochSec)
    }

    @Test fun roundTripWithPassphrase() {
        val enc = encode(secret = Secret.Passphrase("correct horse battery staple".toCharArray()))
        assertArrayEquals(plain, ok(decode(enc, Secret.Passphrase("correct horse battery staple".toCharArray()))).plaintext)
    }

    @Test fun emptyAndLargePayloads() {
        assertArrayEquals(ByteArray(0), ok(decode(encode(p = ByteArray(0)))).plaintext)
        val big = ByteArray(300_000).also { Random(1).nextBytes(it) }
        assertArrayEquals(big, ok(decode(encode(p = big))).plaintext)
    }

    @Test fun fileSizeIsPaddedToBuckets() {
        val a = encode(p = ByteArray(10))
        val b = encode(p = ByteArray(1000) { (it % 7).toByte() })
        assertEquals("small payloads of different sizes must look the same size", a.size, b.size)
    }

    @Test fun eachBackupUsesFreshNonces() {
        val a = encode()
        val b = encode()
        assertFalse(a.contentEquals(b))
        assertNotEquals(a.copyOfRange(BackupHeader.SIZE + 4, BackupHeader.SIZE + 28).toList(), b.copyOfRange(BackupHeader.SIZE + 4, BackupHeader.SIZE + 28).toList())
    }

    // ---- wrong secrets ------------------------------------------------------------------------

    @Test fun wrongKeyIsRejected() {
        val other = key.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertEquals(BackupError.AUTH_FAILED, error(decode(encode(), Secret.Key(other))))
    }

    @Test fun wrongPassphraseAndMismatchedSecretTypeAreRejected() {
        val enc = encode(secret = Secret.Passphrase("one".toCharArray()))
        assertEquals(BackupError.AUTH_FAILED, error(decode(enc, Secret.Passphrase("two".toCharArray()))))
        assertEquals(BackupError.AUTH_FAILED, error(decode(enc, Secret.Key(key))))
        assertEquals(BackupError.AUTH_FAILED, error(decode(encode(), Secret.Passphrase("one".toCharArray()))))
    }

    // ---- tampering ----------------------------------------------------------------------------

    @Test fun everySingleByteChangeIsRejected() {
        val enc = encode()
        for (i in enc.indices) {
            val m = enc.copyOf()
            m[i] = (m[i].toInt() xor 0x01).toByte()
            assertTrue("flipping byte $i of ${enc.size} was accepted", decode(m) is BackupResult.Err)
        }
    }

    @Test fun everyTruncationIsRejected() {
        val enc = encode()
        for (n in 0 until enc.size) assertTrue("truncation to $n bytes was accepted", decode(enc.copyOf(n)) is BackupResult.Err)
    }

    @Test fun appendedBytesAreRejected() {
        val enc = encode()
        assertTrue(decode(enc + byteArrayOf(0)) is BackupResult.Err)
        assertTrue(decode(enc + enc.copyOfRange(BackupHeader.SIZE, enc.size)) is BackupResult.Err)
    }

    private fun chunks(enc: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var off = BackupHeader.SIZE
        while (off < enc.size) {
            val len = ByteBuffer.wrap(enc, off, 4).int
            out += enc.copyOfRange(off, off + 4 + len)
            off += 4 + len
        }
        return out
    }

    private fun assemble(header: ByteArray, cs: List<ByteArray>): ByteArray = cs.fold(header) { acc, c -> acc + c }

    @Test fun reorderingDroppingAndDuplicatingChunksIsRejected() {
        val data = ByteArray(5000).also { Random(3).nextBytes(it) }
        val enc = encode(p = data, chunk = 1024)
        val header = enc.copyOfRange(0, BackupHeader.SIZE)
        val cs = chunks(enc)
        assertTrue("need several chunks, got ${cs.size}", cs.size >= 4)
        assertArrayEquals(data, ok(decode(enc)).plaintext)
        assertTrue(decode(assemble(header, listOf(cs[1], cs[0]) + cs.drop(2))) is BackupResult.Err)
        assertTrue(decode(assemble(header, cs.filterIndexed { i, _ -> i != 1 })) is BackupResult.Err)
        assertTrue(decode(assemble(header, cs + listOf(cs[0]))) is BackupResult.Err)
        assertTrue(decode(assemble(header, cs.dropLast(1))) is BackupResult.Err)
    }

    @Test fun chunksSplicedFromAnotherBackupAreRejected() {
        val data = ByteArray(5000).also { Random(4).nextBytes(it) }
        val a = encode(p = data, chunk = 1024)
        val b = encode(p = data, chunk = 1024, backupId = ByteArray(16) { 9 })
        val ca = chunks(a)
        val cb = chunks(b)
        val spliced = assemble(a.copyOfRange(0, BackupHeader.SIZE), listOf(ca[0], cb[1]) + ca.drop(2))
        assertTrue(decode(spliced) is BackupResult.Err)
    }

    @Test fun maliciousHeaderValuesAreRefusedBeforeAnyWork() {
        val enc = encode(secret = Secret.Passphrase("x".toCharArray()))
        fun patch(offset: Int, bytes: ByteArray) = enc.copyOf().also { System.arraycopy(bytes, 0, it, offset, bytes.size) }
        // Header layout: magic 8, version 2, kdf 1, iterations 4, salt 16, id 16, seq 8, time 8, prev 32, chunk 4.
        val hugeIterations = patch(11, ByteBuffer.allocate(4).putInt(Int.MAX_VALUE).array())
        val started = System.nanoTime()
        assertEquals(BackupError.MALFORMED, error(decode(hugeIterations, Secret.Passphrase("x".toCharArray()))))
        assertTrue("must refuse instantly, took ${(System.nanoTime() - started) / 1_000_000} ms", (System.nanoTime() - started) < 500_000_000)
        assertEquals(BackupError.MALFORMED, error(decode(patch(11, ByteBuffer.allocate(4).putInt(5).array()), Secret.Passphrase("x".toCharArray()))))
        assertEquals(BackupError.MALFORMED, error(decode(patch(BackupHeader.SIZE - 4, ByteBuffer.allocate(4).putInt(Int.MAX_VALUE).array()))))
        assertEquals(BackupError.MALFORMED, error(decode(patch(BackupHeader.SIZE - 4, ByteBuffer.allocate(4).putInt(7).array()))))
        assertEquals(BackupError.BAD_MAGIC, error(decode(patch(0, "NOTAKEY!".toByteArray()))))
        assertEquals(BackupError.UNSUPPORTED_VERSION, error(decode(patch(8, byteArrayOf(0, 9)))))
        // A negative or enormous sealed-chunk length must not allocate or loop.
        val keyed = encode()
        fun patchKeyed(offset: Int, bytes: ByteArray) = keyed.copyOf().also { System.arraycopy(bytes, 0, it, offset, bytes.size) }
        assertEquals(BackupError.MALFORMED, error(decode(patchKeyed(BackupHeader.SIZE, ByteBuffer.allocate(4).putInt(-1).array()))))
        assertEquals(BackupError.MALFORMED, error(decode(patchKeyed(BackupHeader.SIZE, ByteBuffer.allocate(4).putInt(Int.MAX_VALUE).array()))))
    }

    @Test fun randomGarbageNeverCrashesTheDecoder() {
        val rnd = Random(11)
        repeat(2000) {
            val b = ByteArray(rnd.nextInt(300)).also { rnd.nextBytes(it) }
            if (it % 2 == 0 && b.size > 8) System.arraycopy(BackupFormat.MAGIC.toByteArray(), 0, b, 0, 8)
            assertTrue(decode(b) is BackupResult.Err)
        }
    }

    // ---- snapshot chain -----------------------------------------------------------------------

    private fun chain(n: Int): List<ByteArray> {
        val files = ArrayList<ByteArray>()
        var prev = zero
        repeat(n) { i ->
            val f = encode(seq = i.toLong(), prev = prev)
            files += f
            prev = BackupFormat.sha256(f)
        }
        return files
    }

    @Test fun anUnbrokenChainVerifies() {
        val r = BackupChain.verify(chain(4))
        assertEquals(ChainCheck.Ok(4, 3), r)
    }

    @Test fun aMissingMiddleSnapshotIsDetected() {
        val c = chain(4)
        assertTrue(BackupChain.verify(listOf(c[0], c[1], c[3])) is ChainCheck.Broken)
    }

    @Test fun aReplacedSnapshotIsDetected() {
        val c = chain(4)
        val impostor = encode(p = "other".toByteArray(), seq = 2, prev = BackupFormat.sha256(c[1]))
        assertTrue(BackupChain.verify(listOf(c[0], c[1], impostor, c[3])) is ChainCheck.Broken)
    }

    @Test fun snapshotsFromAnotherLineageAreDetected() {
        val c = chain(2)
        val foreign = encode(seq = 2, prev = BackupFormat.sha256(c[1]), backupId = ByteArray(16) { 5 })
        assertTrue(BackupChain.verify(c + foreign) is ChainCheck.Broken)
    }

    @Test fun pruningTheOldestSnapshotsKeepsTheRestVerifiable() {
        val c = chain(5)
        assertEquals(ChainCheck.Ok(3, 4), BackupChain.verify(c.drop(2)))
    }

    // ---- recovery key -------------------------------------------------------------------------

    @Test fun recoveryKeyFormatsAndRoundTrips() {
        repeat(200) {
            val k = RecoveryKey.generate()
            val text = RecoveryKey.encode(k)
            assertEquals(14, text.split("-").size)
            assertTrue(text.split("-").all { it.length == 4 })
            assertArrayEquals(k, (RecoveryKey.decode(text) as RecoveryDecode.Ok).key)
        }
    }

    @Test fun recoveryKeyToleratesHumanFormatting() {
        val k = RecoveryKey.generate()
        val text = RecoveryKey.encode(k)
        assertArrayEquals(k, (RecoveryKey.decode(text.lowercase().replace("-", " ")) as RecoveryDecode.Ok).key)
        assertArrayEquals(k, (RecoveryKey.decode("  " + text.replace("-", "").chunked(8).joinToString("\n")) as RecoveryDecode.Ok).key)
    }

    @Test fun recoveryKeyTypingMistakesAreCaught() {
        val rnd = Random(77)
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        var detected = 0
        val trials = 5000
        repeat(trials) {
            val k = RecoveryKey.generate()
            val chars = RecoveryKey.encode(k).toCharArray()
            var pos: Int
            do { pos = rnd.nextInt(chars.size) } while (chars[pos] == '-')
            val original = chars[pos]
            var replacement: Char
            do { replacement = alphabet[rnd.nextInt(alphabet.length)] } while (replacement == original)
            chars[pos] = replacement
            val r = RecoveryKey.decode(String(chars))
            if (r !is RecoveryDecode.Ok || !r.key.contentEquals(k)) detected++
        }
        assertEquals("a single wrong character must never decode to the original key", trials, detected)
        assertTrue(RecoveryKey.decode("ABCD") is RecoveryDecode.BadFormat)
        assertTrue(RecoveryKey.decode("U".repeat(56)) is RecoveryDecode.BadFormat)
    }

    @Test fun keyHierarchyKeepsPurposesApart() {
        val master = ByteArray(32) { it.toByte() }
        val keys = listOf(KeyHierarchy.databaseKey(master), KeyHierarchy.fingerprintKey(master), KeyHierarchy.sealKey(master), KeyHierarchy.blobKey(master))
        assertEquals(4, keys.map { it.toList() }.distinct().size)
        assertArrayEquals(keys[0], KeyHierarchy.databaseKey(master))
        assertNotEquals(keys[0].toList(), KeyHierarchy.databaseKey(ByteArray(32) { 1 }).toList())
    }

    // ---- history seal --------------------------------------------------------------------------

    private fun sealChain(k: ByteArray, n: Int) = (0 until n).fold(emptyList<com.hsib.sib.core.seal.SealEntry>()) { acc, i ->
        acc + Seal.append(k, acc.lastOrNull(), 1_000L + i, "due", "2026-11-1$i", "2026-11-2$i", "USER", "edit $i")
    }

    @Test fun anUntouchedHistoryVerifies() {
        val k = KeyHierarchy.sealKey(key)
        val chain = sealChain(k, 5)
        val r = Seal.verify(k, chain) as SealCheck.Ok
        assertEquals(5, r.entries)
        assertEquals(chain.last().hash, r.head)
    }

    @Test fun anyEditToTheHistoryBreaksTheSeal() {
        val k = KeyHierarchy.sealKey(key)
        val chain = sealChain(k, 5)
        val edited = chain.toMutableList().also { it[2] = it[2].copy(newValue = "2026-12-31") }
        assertEquals(2, (Seal.verify(k, edited) as SealCheck.Broken).atSeq)
        assertTrue(Seal.verify(k, chain.filterIndexed { i, _ -> i != 2 }) is SealCheck.Broken)
        assertTrue(Seal.verify(k, chain.reversed().mapIndexed { i, e -> e.copy(seq = i) }) is SealCheck.Broken)
        assertTrue(Seal.verify(KeyHierarchy.sealKey(ByteArray(32) { 9 }), chain) is SealCheck.Broken)
        // Appending a forged entry without the key does not verify.
        val forged = chain + chain.last().copy(seq = 5, hash = "0".repeat(64), prevHash = chain.last().hash)
        assertTrue(Seal.verify(k, forged) is SealCheck.Broken)
    }

    // ---- snapshot ------------------------------------------------------------------------------

    private fun obligation(id: String, title: String, updated: Long) = Obligation(
        id = id, title = title, category = Category.ASSIGNMENT, importance = Importance.HIGH,
        due = DueSpec(LocalDateTime.of(2026, 11, 18, 23, 59), ZoneId.of("Europe/Berlin"), Precision.DATE_TIME),
        createdAt = Instant.ofEpochMilli(1000), updatedAt = Instant.ofEpochMilli(updated),
    )

    @Test fun snapshotSurvivesSerializationAndEncryption() {
        val o = obligation("a", "Report", 5000)
        val snap = BackupSnapshot(createdAtEpochSec = 1, obligations = listOf(ObligationDto.from(o)), dismissed = listOf("abc"), settings = mapOf("theme" to "dark"))
        val back = BackupSnapshot.fromBytes(ok(decode(encode(p = snap.toBytes()))).plaintext)
        assertEquals(o, back.obligations.single().toModel())
        assertEquals(listOf("abc"), back.dismissed)
        assertEquals("dark", back.settings["theme"])
    }

    @Test fun mergeKeepsTheNewerEditPerItem() {
        val local = BackupSnapshot(createdAtEpochSec = 1, obligations = listOf(ObligationDto.from(obligation("a", "Local newer", 9000)), ObligationDto.from(obligation("b", "Local older", 1000))))
        val remote = BackupSnapshot(createdAtEpochSec = 2, obligations = listOf(ObligationDto.from(obligation("a", "Remote older", 2000)), ObligationDto.from(obligation("b", "Remote newer", 5000)), ObligationDto.from(obligation("c", "Only remote", 3000))))
        val m = SnapshotMerge.merge(local, remote)
        val byId = m.snapshot.obligations.associateBy { it.id }
        assertEquals("Local newer", byId["a"]!!.title)
        assertEquals("Remote newer", byId["b"]!!.title)
        assertEquals("Only remote", byId["c"]!!.title)
        assertEquals(1, m.added)
        assertEquals(1, m.updated)
        assertEquals(0, m.conflicts)
    }

    @Test fun mergeCountsSimultaneousDifferentEditsAsConflicts() {
        val local = BackupSnapshot(createdAtEpochSec = 1, obligations = listOf(ObligationDto.from(obligation("a", "Mine", 5000))))
        val remote = BackupSnapshot(createdAtEpochSec = 1, obligations = listOf(ObligationDto.from(obligation("a", "Theirs", 5000))))
        val m = SnapshotMerge.merge(local, remote)
        assertEquals("Mine", m.snapshot.obligations.single().title)
        assertEquals(1, m.conflicts)
    }
}

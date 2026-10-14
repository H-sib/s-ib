package com.hsib.sib.platform

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.hsib.sib.core.backup.BackupChain
import com.hsib.sib.core.backup.BackupError
import com.hsib.sib.core.backup.BackupFormat
import com.hsib.sib.core.backup.BackupHeader
import com.hsib.sib.core.backup.BackupResult
import com.hsib.sib.core.backup.BackupSnapshot
import com.hsib.sib.core.backup.ChainCheck
import com.hsib.sib.core.backup.RecoveryDecode
import com.hsib.sib.core.backup.RecoveryKey
import com.hsib.sib.core.backup.Secret
import com.hsib.sib.core.ingest.IcsWriter
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Space
import com.hsib.sib.data.HistoryKeys
import com.hsib.sib.data.KeystoreBox
import com.hsib.sib.data.SettingsCodec
import com.hsib.sib.data.SettingsStore
import com.hsib.sib.data.Store
import com.hsib.sib.data.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import java.time.ZoneId

data class BackupOutcome(val ok: Boolean, val message: String, val seq: Long = -1)

data class VerifyReport(val ok: Boolean, val headline: String, val details: List<String>)

data class RestoreOutcome(val ok: Boolean, val message: String, val restoredObligations: Int = 0, val notes: List<String> = emptyList())

/**
 * End-to-end encrypted backup to a folder the user chooses with the system file picker.
 *
 * Whatever storage app owns that folder (a local directory, Google Drive, a sync client) only ever receives
 * ciphertext. The key never leaves the phone except as the recovery key the user writes down. S-IB needs no network
 * permission for this: the system file picker does the transport.
 */
class BackupService(
    private val context: Context,
    private val store: Store,
    private val settings: SettingsStore,
    private val keys: HistoryKeys,
) {
    private val box = KeystoreBox("sib_backup_key_v1")
    private val keyFile get() = File(context.filesDir, "backup_key.bin")
    private val aad = "sib-backup-key-v1".toByteArray()
    private val prefix = "sib-backup-"
    private val keep = 8

    fun hasKey() = keyFile.exists()

    private fun loadKey(): ByteArray? = if (keyFile.exists()) box.open(keyFile.readBytes(), aad) else null

    /** Stores the recovery key wrapped by the Keystore so scheduled backups can run without asking. */
    fun saveKey(key: ByteArray) = keyFile.writeBytes(box.seal(key, aad))

    fun forgetKey() { keyFile.delete() }

    /** For the screen that lets the user write the key down again. */
    fun recoveryKeyText(): String? = loadKey()?.let(RecoveryKey::encode)

    private fun fail(message: String) = BackupOutcome(false, message)

    private fun readAll(uri: Uri, limit: Int = 64 * 1024 * 1024): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (out.size() + n > limit) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }.getOrNull()

    private fun folder(uriText: String?): DocumentFile? = uriText?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }

    private fun backupFiles(root: DocumentFile): List<DocumentFile> =
        root.listFiles().filter { it.isFile && (it.name ?: "").startsWith(prefix) }

    suspend fun backupNow(): BackupOutcome = withContext(Dispatchers.IO) {
        val s = settings.current()
        val root = folder(s.backupFolder) ?: return@withContext fail("No backup folder is chosen.")
        if (!root.canWrite()) return@withContext fail("S-IB can no longer write to that folder. Choose it again in Backup settings.")
        val key = loadKey() ?: return@withContext fail("The recovery key is missing. Set up backup again.")
        val seq = s.backupSeq + 1
        val prev = s.backupHead?.let(HistoryKeys::unhex)?.takeIf { it.size == 32 } ?: ByteArray(32)
        val lineage = s.backupLineage?.let(HistoryKeys::unhex)?.takeIf { it.size == 16 } ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
        val snapshot = store.exportSnapshot(SettingsCodec.portable(s))
        val bytes = BackupFormat.encode(snapshot.toBytes(), Secret.Key(key), seq, System.currentTimeMillis() / 1000, prev, lineage)
        val name = "%s%05d.sibbak".format(prefix, seq)
        root.findFile(name)?.delete()
        val file = root.createFile("application/octet-stream", name) ?: return@withContext fail("The folder would not accept a new file.")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) } ?: return@withContext fail("The file could not be written.")
        // Never trust a write blindly: read it back and compare before the old state is considered safe.
        val back = readAll(file.uri)
        if (back == null || !back.contentEquals(bytes)) {
            file.delete()
            return@withContext fail("The copy in the folder did not match. Try a different folder.")
        }
        settings.update {
            it.copy(
                backupSeq = seq, backupHead = BackupFormat.sha256(bytes).toHex(), backupLineage = lineage.toHex(),
                backupLastAt = System.currentTimeMillis(),
            )
        }
        backupFiles(root).mapNotNull { f -> readAll(f.uri, 4096)?.let { b -> (BackupFormat.peek(b) as? BackupResult.Ok)?.value?.seq?.let { sq -> sq to f } } }
            .sortedByDescending { it.first }.drop(keep).forEach { runCatching { it.second.delete() } }
        BackupOutcome(true, "Backup $seq saved and checked.", seq)
    }

    /** Dry run: checks the chain, then decrypts the newest snapshot completely without touching any data. */
    suspend fun verify(): VerifyReport = withContext(Dispatchers.IO) {
        val s = settings.current()
        val root = folder(s.backupFolder) ?: return@withContext VerifyReport(false, "No backup folder is chosen.", emptyList())
        val key = loadKey() ?: return@withContext VerifyReport(false, "The recovery key is missing.", emptyList())
        val files = backupFiles(root).mapNotNull { readAll(it.uri) }
        if (files.isEmpty()) return@withContext VerifyReport(false, "The folder has no S-IB backups.", emptyList())
        val details = ArrayList<String>()
        var ok = true
        val chain = BackupChain.verify(files, s.backupLineage?.let(HistoryKeys::unhex))
        when (chain) {
            is ChainCheck.Ok -> details += "${chain.snapshots} snapshots form an unbroken chain, newest is number ${chain.newestSeq}."
            is ChainCheck.Broken -> { ok = false; details += "Chain problem: ${chain.why}" }
        }
        val newest = files.maxByOrNull { (BackupFormat.peek(it) as? BackupResult.Ok)?.value?.seq ?: -1 }!!
        val header = (BackupFormat.peek(newest) as? BackupResult.Ok)?.value
        if (header != null && s.backupSeq >= 0 && header.seq < s.backupSeq) {
            ok = false
            details += "The newest backup in the folder (${header.seq}) is older than the last one this phone made (${s.backupSeq}). Newer files may have been deleted or replaced."
        }
        when (val r = BackupFormat.decode(newest, Secret.Key(key))) {
            is BackupResult.Ok -> {
                val snap = runCatching { BackupSnapshot.fromBytes(r.value.plaintext) }.getOrNull()
                if (snap == null) { ok = false; details += "The newest backup decrypts but its contents are unreadable." }
                else details += "The newest backup decrypts and holds ${snap.obligations.size} deadlines."
            }
            is BackupResult.Err -> { ok = false; details += "The newest backup could not be opened (${describe(r.error)})." }
        }
        VerifyReport(ok, if (ok) "Backup verified" else "Backup needs attention", details)
    }

    private fun describe(e: BackupError) = when (e) {
        BackupError.AUTH_FAILED -> "wrong key, or the file was changed"
        BackupError.TRUNCATED -> "the file is incomplete"
        BackupError.BAD_MAGIC -> "not a S-IB backup"
        BackupError.UNSUPPORTED_VERSION -> "made by a newer version of S-IB"
        BackupError.MALFORMED -> "damaged"
        BackupError.TOO_LARGE -> "unreasonably large"
    }

    /** Restores from the newest readable snapshot in a folder, using the recovery key the user typed. */
    suspend fun restoreFromFolder(folderUri: String, keyText: String, merge: Boolean): RestoreOutcome = withContext(Dispatchers.IO) {
        val key = when (val d = RecoveryKey.decode(keyText)) {
            is RecoveryDecode.Ok -> d.key
            RecoveryDecode.BadFormat -> return@withContext RestoreOutcome(false, "That is not a recovery key. It has 14 groups of 4 letters and numbers.")
            RecoveryDecode.BadChecksum -> return@withContext RestoreOutcome(false, "One character is wrong. Check the key against what you wrote down.")
        }
        val root = folder(folderUri) ?: return@withContext RestoreOutcome(false, "That folder could not be opened.")
        val files = backupFiles(root).mapNotNull { f -> readAll(f.uri)?.let { b -> (BackupFormat.peek(b) as? BackupResult.Ok)?.value?.let { h -> h to b } } }
            .sortedByDescending { it.first.seq }
        if (files.isEmpty()) return@withContext RestoreOutcome(false, "No S-IB backups were found in that folder.")
        val notes = ArrayList<String>()
        when (val c = BackupChain.verify(files.map { it.second })) {
            is ChainCheck.Broken -> notes += "Warning: ${c.why}."
            is ChainCheck.Ok -> {}
        }
        for ((idx, pair) in files.withIndex()) {
            val (header, bytes) = pair
            when (val r = BackupFormat.decode(bytes, Secret.Key(key))) {
                is BackupResult.Ok -> {
                    val snap = runCatching { BackupSnapshot.fromBytes(r.value.plaintext) }.getOrNull() ?: continue
                    if (idx > 0) notes += "The newest backup could not be read, so this is the one before it (number ${header.seq})."
                    return@withContext applyRestore(snap, header, bytes, key, folderUri, merge, notes)
                }
                is BackupResult.Err -> if (r.error == BackupError.AUTH_FAILED && idx == files.lastIndex && idx == 0) {
                    return@withContext RestoreOutcome(false, "The recovery key does not open this backup, or the file was changed.")
                }
            }
        }
        RestoreOutcome(false, "None of the backups in that folder could be opened with this key.")
    }

    /** Restores from a single exported file with a passphrase or recovery key. */
    suspend fun restoreFromFile(uri: Uri, secret: Secret, merge: Boolean): RestoreOutcome = withContext(Dispatchers.IO) {
        val bytes = readAll(uri) ?: return@withContext RestoreOutcome(false, "That file could not be read.")
        when (val r = BackupFormat.decode(bytes, secret)) {
            is BackupResult.Ok -> {
                val snap = runCatching { BackupSnapshot.fromBytes(r.value.plaintext) }.getOrNull() ?: return@withContext RestoreOutcome(false, "That backup is unreadable.")
                val stats = store.importSnapshot(snap, merge, SettingsCodec.encode(settings.current()))
                applySettings(snap)
                RestoreOutcome(true, "Restored ${snap.obligations.size} deadlines.", snap.obligations.size, listOfNotNull(stats?.let { "${it.added} added, ${it.updated} updated, ${it.conflicts} kept as they were" }))
            }
            is BackupResult.Err -> RestoreOutcome(false, "That file could not be opened: ${describe(r.error)}.")
        }
    }

    private suspend fun applyRestore(snap: BackupSnapshot, header: BackupHeader, bytes: ByteArray, key: ByteArray, folderUri: String, merge: Boolean, notes: MutableList<String>): RestoreOutcome {
        val stats = store.importSnapshot(snap, merge, SettingsCodec.encode(settings.current()))
        applySettings(snap)
        // Continue the same backup chain from this phone, with the same key and folder.
        saveKey(key)
        settings.update {
            it.copy(
                backupFolder = folderUri, backupLineage = header.backupId.toHex(), backupSeq = header.seq,
                backupHead = BackupFormat.sha256(bytes).toHex(), backupLastAt = System.currentTimeMillis(), onboardingDone = true,
            )
        }
        stats?.let { notes += "${it.added} added, ${it.updated} updated, ${it.conflicts} kept as they were." }
        return RestoreOutcome(true, "Restored ${snap.obligations.size} deadlines from backup ${header.seq}.", snap.obligations.size, notes)
    }

    private suspend fun applySettings(snap: BackupSnapshot) {
        settings.update { current -> SettingsCodec.decode(SettingsCodec.encode(current) + snap.settings.filterKeys { it != "historyKeys" }) }
    }

    // ---- single-file export and calendar export ---------------------------------------------

    suspend fun exportFile(uri: Uri, secret: Secret): BackupOutcome = withContext(Dispatchers.IO) {
        val snapshot = store.exportSnapshot(SettingsCodec.portable(settings.current()))
        val bytes = BackupFormat.encode(snapshot.toBytes(), secret, 0, System.currentTimeMillis() / 1000, ByteArray(32), ByteArray(16).also { SecureRandom().nextBytes(it) })
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: return@withContext fail("The file could not be written.")
        BackupOutcome(true, "Encrypted export saved.")
    }

    suspend fun exportIcs(uri: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        val items = store.allObligations().filter { it.lifecycle == Lifecycle.ACTIVE && it.space == Space.WORK }
        val text = IcsWriter.write(items, ZoneId.systemDefault())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: return@withContext fail("The file could not be written.")
        BackupOutcome(true, "Calendar file saved. It holds your work deadlines only, and it is not encrypted: anyone who opens it can read them.")
    }
}

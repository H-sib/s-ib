package com.hsib.sib.data

import androidx.room.withTransaction
import com.google.crypto.tink.subtle.XChaCha20Poly1305
import com.hsib.sib.core.backup.BackupSnapshot
import com.hsib.sib.core.backup.EvidenceDto
import com.hsib.sib.core.backup.MergeResult
import com.hsib.sib.core.backup.ObligationDto
import com.hsib.sib.core.backup.ReceiptDto
import com.hsib.sib.core.backup.SealDto
import com.hsib.sib.core.backup.SnapshotMerge
import com.hsib.sib.core.backup.StepDto
import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Limits
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Recurrence
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.Repeat
import com.hsib.sib.core.model.clamped
import com.hsib.sib.core.plan.Defaults
import com.hsib.sib.core.seal.Seal
import com.hsib.sib.core.seal.SealCheck
import com.hsib.sib.core.seal.SealEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

/** Encrypted files for receipt images. File names are random and carry no meaning. */
class BlobFiles(private val dir: File, private val key: ByteArray) {
    init { dir.mkdirs() }

    fun write(bytes: ByteArray): String {
        val name = UUID.randomUUID().toString().replace("-", "")
        File(dir, name).writeBytes(XChaCha20Poly1305(key).encrypt(bytes, name.toByteArray()))
        return name
    }

    fun read(name: String): ByteArray? = runCatching {
        val f = File(dir, name)
        if (!f.isFile || f.length() > 8_000_000) null else XChaCha20Poly1305(key).decrypt(f.readBytes(), name.toByteArray())
    }.getOrNull()

    fun delete(name: String) { File(dir, name).delete() }

    fun deleteAll() { dir.listFiles()?.forEach { it.delete() } }
}

/**
 * Keys that seal each deadline's history. A user's keys travel with their backups so the history still verifies on
 * a new phone; verification tries every key this vault has ever held.
 */
class HistoryKeys(private val settings: SettingsStore) {
    private var keys: List<ByteArray> = emptyList()

    suspend fun load() {
        keys = settings.raw("historyKeys")?.split(",")?.mapNotNull(::unhex)?.filter { it.size == 32 }.orEmpty()
        if (keys.isEmpty()) {
            keys = listOf(ByteArray(32).also { SecureRandom().nextBytes(it) })
            persist()
        }
    }

    val current: ByteArray get() = keys.first()
    val all: List<ByteArray> get() = keys

    /** Keeps this vault's own current key first and remembers any others so their entries stay verifiable. */
    suspend fun adopt(others: List<ByteArray>) {
        val merged = (keys + others).distinctBy { it.toList() }
        if (merged.size != keys.size) { keys = merged; persist() }
    }

    fun serialized(): String = keys.joinToString(",") { it.toHex() }

    private suspend fun persist() = settings.putRaw("historyKeys", serialized())

    companion object {
        fun unhex(s: String): ByteArray? = runCatching { ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }
}

fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

data class DataCounts(val obligations: Int, val candidates: Int, val steps: Int, val receipts: Int, val excerpts: Int, val historyEntries: Int, val dismissed: Int)

data class CandidateEdit(
    val title: String? = null,
    val due: DueSpec? = null,
    val importance: Importance? = null,
    val effort: Effort? = null,
    val keepExcerpt: Boolean = false,
)

class Store(
    private val db: SibDatabase,
    private val keys: HistoryKeys,
    private val blobs: BlobFiles,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Instant = { Instant.now() },
) {
    val obligations: Flow<List<Obligation>> = db.obligations().observeAll().map { l -> l.map { it.toModel() } }
    val candidates: Flow<List<StoredCandidate>> = db.candidates().observeAll().map { l -> l.map { it.toStored() } }
    val steps: Flow<List<UserStep>> = db.steps().observeAll().map { l -> l.map { it.toModel() } }
    fun receipts(obligationId: String): Flow<List<Receipt>> = db.receipts().observeFor(obligationId).map { l -> l.map { it.toModel() } }
    fun excerpts(obligationId: String): Flow<List<Evidence>> = db.evidence().observeFor(obligationId).map { l -> l.map { it.toModel() } }
    fun history(obligationId: String): Flow<List<SealEntry>> = db.seals().observeFor(obligationId).map { l -> l.map { it.toEntry() } }

    suspend fun obligation(id: String): Obligation? = db.obligations().get(id)?.toModel()
    suspend fun allObligations(): List<Obligation> = db.obligations().all().map { it.toModel() }
    suspend fun storedCandidates(): List<StoredCandidate> = db.candidates().all().map { it.toStored() }
    suspend fun allSteps(): List<UserStep> = db.steps().all().map { it.toModel() }
    suspend fun dismissedFingerprints(): Set<String> = db.dismissed().fingerprints().toSet()

    // ---- obligations ------------------------------------------------------------------------

    private fun DueSpec?.describe() = this?.let { "${it.local} ${it.zone.id} ${it.precision.name.lowercase()}" } ?: "none"

    private fun diffs(old: Obligation?, new: Obligation): List<Triple<String, String, String>> {
        if (old == null) return listOf(Triple("created", "", "${new.title} | ${new.due.describe()}"))
        val out = ArrayList<Triple<String, String, String>>()
        fun add(f: String, a: String, b: String) { if (a != b) out += Triple(f, a, b) }
        add("title", old.title, new.title)
        add("due", old.due.describe(), new.due.describe())
        add("window", old.fog?.let { "${it.earliest}..${it.latest}" } ?: "none", new.fog?.let { "${it.earliest}..${it.latest}" } ?: "none")
        add("category", old.category.name, new.category.name)
        add("importance", old.importance.name, new.importance.name)
        add("effort", old.effort?.let { "${it.optimisticMin}/${it.likelyMin}/${it.pessimisticMin}" } ?: "none", new.effort?.let { "${it.optimisticMin}/${it.likelyMin}/${it.pessimisticMin}" } ?: "none")
        add("status", old.lifecycle.name, new.lifecycle.name)
        add("postponed", old.postponedUntil?.toString() ?: "none", new.postponedUntil?.toString() ?: "none")
        add("repeat", old.repeat.name, new.repeat.name)
        add("person", old.person, new.person)
        add("relation", old.relation, new.relation)
        add("lead days", old.leadDays.joinToString(","), new.leadDays.joinToString(","))
        add("together since", old.since?.toString() ?: "none", new.since?.toString() ?: "none")
        return out
    }

    private suspend fun appendSeal(id: String, field: String, old: String, new: String, actor: String, reason: String) {
        val last = db.seals().forObligation(id).lastOrNull()?.toEntry()
        db.seals().upsert(Seal.append(keys.current, last, now().epochSecond, field, old, new, actor, reason).toEntity(id))
    }

    suspend fun save(o: Obligation, actor: String = "USER", reason: String = "edited"): Obligation = db.withTransaction {
        val old = db.obligations().get(o.id)?.toModel()
        val ts = now()
        val saved = o.clamped().copy(createdAt = old?.createdAt ?: ts, updatedAt = ts)
        db.obligations().upsert(saved.toEntity())
        for ((f, a, b) in diffs(old, saved)) appendSeal(saved.id, f, a, b, actor, if (old == null) "created" else reason)
        saved
    }

    suspend fun create(o: Obligation, actor: String = "USER", reason: String = "created"): Obligation =
        save(o.copy(id = o.id.ifEmpty { newId() }), actor, reason)

    suspend fun addProgress(id: String, minutes: Int) = db.withTransaction {
        val o = db.obligations().get(id)?.toModel() ?: return@withTransaction
        db.obligations().upsert(o.copy(progressMin = (o.progressMin + minutes).coerceAtLeast(0), updatedAt = now()).toEntity())
    }

    /** The same occasion on its next date, counted from the original day so it never drifts. Null if it does not repeat. */
    private fun advanced(o: Obligation, from: LocalDate): Obligation? {
        val anchor = o.repeatAnchor ?: return null
        val due = o.due ?: return null
        if (o.repeat == Repeat.NONE) return null
        val next = Recurrence.nextOnOrAfter(anchor, o.repeat, from)
        return o.copy(due = due.copy(local = next.atTime(due.local.toLocalTime())), postponedUntil = null)
    }

    /** Moves repeating occasions whose day has passed on to their next date. Returns how many moved. */
    suspend fun rollRepeats(today: LocalDate, zone: ZoneId): Int = db.withTransaction {
        var moved = 0
        for (entity in db.obligations().all()) {
            val o = entity.toModel()
            val due = o.due ?: continue
            if (o.lifecycle != Lifecycle.ACTIVE || !due.dayIn(zone).isBefore(today)) continue
            val next = advanced(o, today) ?: continue
            save(next, "SYSTEM", "moved to the next date")
            moved++
        }
        moved
    }

    suspend fun complete(id: String, note: String?, image: ByteArray?) = db.withTransaction {
        val o = db.obligations().get(id)?.toModel() ?: return@withTransaction
        val ts = now()
        // A repeating occasion is never finished, it just moves on: you did this year's, next year's is already waiting.
        val next = o.due?.let { d -> advanced(o, d.local.toLocalDate().plusDays(1)) }
        if (next != null) {
            save(next, "USER", "marked done, moved to the next date")
            return@withTransaction
        }
        db.obligations().upsert(o.copy(lifecycle = Lifecycle.COMPLETED, completedAt = ts, updatedAt = ts).toEntity())
        appendSeal(id, "status", o.lifecycle.name, Lifecycle.COMPLETED.name, "USER", "marked done")
        if (!note.isNullOrBlank() || image != null) {
            val file = image?.let { blobs.write(it) }
            db.receipts().upsert(ReceiptEntity(newId(), id, note.orEmpty().take(2000), file, ts.toEpochMilli()))
        }
    }

    suspend fun reopen(id: String) = db.withTransaction {
        val o = db.obligations().get(id)?.toModel() ?: return@withTransaction
        db.obligations().upsert(o.copy(lifecycle = Lifecycle.ACTIVE, completedAt = null, updatedAt = now()).toEntity())
        appendSeal(id, "status", o.lifecycle.name, Lifecycle.ACTIVE.name, "USER", "reopened")
    }

    suspend fun postpone(id: String, until: LocalDate?) = db.withTransaction {
        val o = db.obligations().get(id)?.toModel() ?: return@withTransaction
        db.obligations().upsert(o.copy(postponedUntil = until, updatedAt = now()).toEntity())
        appendSeal(id, "postponed", o.postponedUntil?.toString() ?: "none", until?.toString() ?: "none", "USER", "postponed")
    }

    suspend fun delete(id: String) = db.withTransaction {
        db.receipts().all().filter { it.obligationId == id }.forEach { r -> r.imageFile?.let(blobs::delete) }
        db.receipts().deleteFor(id); db.evidence().deleteFor(id); db.steps().deleteFor(id)
        db.reminders().deleteFor(id); db.seals().deleteFor(id); db.obligations().delete(id)
    }

    suspend fun completedEstimates(): List<Pair<Int, Int>> =
        allObligations().filter { it.lifecycle == Lifecycle.COMPLETED && (it.effort?.likelyMin ?: 0) > 0 && it.progressMin > 0 }
            .map { it.effort!!.likelyMin to it.progressMin }

    // ---- candidates -------------------------------------------------------------------------

    suspend fun saveCandidates(list: List<Candidate>, ttlDays: Int) = db.withTransaction {
        val ts = now()
        for (c in list) db.candidates().upsert(c.toEntity(ts, ttlDays))
        // Keep the most likely ones if a flood of suggestions arrives (a huge chat export, repeated shares).
        val all = db.candidates().all()
        if (all.size > Limits.MAX_PENDING) {
            all.sortedWith(compareByDescending<CandidateEntity> { it.confidence }.thenByDescending { it.createdAt })
                .drop(Limits.MAX_PENDING).forEach { db.candidates().delete(it.id) }
        }
    }

    suspend fun accept(candidateId: String, edit: CandidateEdit = CandidateEdit()): Obligation? = db.withTransaction {
        val stored = db.candidates().get(candidateId)?.toStored() ?: return@withTransaction null
        val c = stored.candidate
        val result: Obligation? = when (c.relation) {
            Relation.CANCEL -> c.relatedId?.let { rid ->
                val o = db.obligations().get(rid)?.toModel()
                if (o != null) save(o.copy(lifecycle = Lifecycle.CANCELLED), "SUGGESTION", "cancelled after you accepted a suggestion") else null
            }
            Relation.UPDATE -> c.relatedId?.let { rid ->
                val o = db.obligations().get(rid)?.toModel()
                if (o != null) save(o.copy(due = edit.due ?: c.due ?: o.due, fog = c.fog, title = edit.title ?: o.title), "SUGGESTION", "changed after you accepted a suggestion") else null
            }
            else -> create(
                Obligation(
                    id = newId(), title = edit.title ?: c.title, category = c.category, kind = c.kind,
                    due = edit.due ?: c.due, fog = c.fog,
                    importance = edit.importance ?: Defaults.importance(c.category),
                    effort = edit.effort ?: Defaults.effort(c.category),
                ),
                "SUGGESTION", "accepted from a ${c.sourceType.name.lowercase().replace('_', ' ')} suggestion",
            )
        }
        if (result != null && edit.keepExcerpt && c.excerpt.isNotBlank()) {
            db.evidence().upsert(EvidenceEntity(newId(), result.id, c.excerpt.take(300), now().toEpochMilli()))
        }
        db.candidates().delete(candidateId)
        result
    }

    /** Dismissal keeps only a keyed fingerprint, never the text, so the same thing is not suggested again. */
    suspend fun dismiss(candidateId: String) = db.withTransaction {
        val c = db.candidates().get(candidateId) ?: return@withTransaction
        if (c.fingerprint.isNotEmpty()) db.dismissed().upsert(DismissedEntity(c.fingerprint, now().toEpochMilli()))
        db.candidates().delete(candidateId)
    }

    suspend fun snooze(candidateId: String, until: Instant) {
        val c = db.candidates().get(candidateId) ?: return
        db.candidates().upsert(c.copy(status = "SNOOZED", snoozeUntil = until.toEpochMilli()))
    }

    suspend fun unsnoozeDue() {
        val ts = now().toEpochMilli()
        db.candidates().all().filter { it.status == "SNOOZED" && (it.snoozeUntil ?: 0) <= ts }
            .forEach { db.candidates().upsert(it.copy(status = "PENDING", snoozeUntil = null)) }
    }

    suspend fun expireCandidates(): Int = db.candidates().deleteExpired(now().toEpochMilli())

    suspend fun undismiss(fingerprint: String) = db.dismissed().delete(fingerprint)

    // ---- steps, excerpts, receipts ----------------------------------------------------------

    suspend fun saveSteps(obligationId: String, list: List<UserStep>) = db.withTransaction {
        db.steps().deleteFor(obligationId)
        list.forEachIndexed { i, s -> db.steps().upsert(s.copy(obligationId = obligationId, position = i, updatedAt = now()).toEntity()) }
    }

    suspend fun toggleStep(step: UserStep) = db.steps().upsert(step.copy(done = !step.done, updatedAt = now()).toEntity())

    suspend fun removeStep(id: String) = db.steps().delete(id)

    suspend fun addStep(obligationId: String, title: String, minutes: Int, day: LocalDate?, position: Int) =
        db.steps().upsert(UserStep(newId(), obligationId, title.take(120), minutes, day, false, false, position, now()).toEntity())

    suspend fun keepExcerpt(obligationId: String, text: String) =
        db.evidence().upsert(EvidenceEntity(newId(), obligationId, text.take(300), now().toEpochMilli()))

    suspend fun deleteExcerpt(id: String) = db.evidence().delete(id)

    fun receiptImage(r: Receipt): ByteArray? = r.imageFile?.let(blobs::read)

    // ---- history ---------------------------------------------------------------------------

    suspend fun verifyHistory(id: String): SealCheck {
        val entries = db.seals().forObligation(id).map { it.toEntry() }
        if (entries.isEmpty()) return SealCheck.Ok(0, Seal.GENESIS)
        var firstBroken: SealCheck = SealCheck.Broken(0, "unknown")
        for (k in keys.all) {
            val r = Seal.verify(k, entries)
            if (r is SealCheck.Ok) return r
            firstBroken = r
        }
        return firstBroken
    }

    // ---- bulk: snapshot, wipe, counts ----------------------------------------------------

    suspend fun counts() = DataCounts(
        obligations = db.obligations().all().size, candidates = db.candidates().all().size, steps = db.steps().all().size,
        receipts = db.receipts().count(), excerpts = db.evidence().count(), historyEntries = db.seals().all().size, dismissed = db.dismissed().count(),
    )

    suspend fun exportSnapshot(settings: Map<String, String>): BackupSnapshot {
        val receipts = db.receipts().all().map { r ->
            val bytes = r.imageFile?.let(blobs::read)?.takeIf { it.size <= 1_000_000 }
            ReceiptDto(r.id, r.obligationId, r.note, bytes?.let { Base64.getEncoder().encodeToString(it) }, r.at)
        }
        return BackupSnapshot(
            createdAtEpochSec = now().epochSecond,
            obligations = db.obligations().all().map { ObligationDto.from(it.toModel()) },
            steps = db.steps().all().map { StepDto(it.id, it.obligationId, it.title, it.minutes, it.targetDay, it.done, it.updatedAt) },
            receipts = receipts,
            evidence = db.evidence().all().map { EvidenceDto(it.id, it.obligationId, it.excerpt, it.at) },
            seal = db.seals().all().map { SealDto.from(it.obligationId, it.toEntry()) },
            dismissed = db.dismissed().fingerprints(),
            settings = settings + ("historyKeys" to keys.serialized()),
        )
    }

    /** Replaces or merges. Returns merge statistics when merging. Settings are handled by the caller. */
    suspend fun importSnapshot(snap: BackupSnapshot, merge: Boolean, localSettings: Map<String, String>): MergeResult? {
        snap.settings["historyKeys"]?.split(",")?.mapNotNull(HistoryKeys::unhex)?.let { keys.adopt(it) }
        val target: BackupSnapshot
        var stats: MergeResult? = null
        if (merge) {
            stats = SnapshotMerge.merge(exportSnapshot(localSettings), snap)
            target = stats.snapshot
        } else {
            target = snap
        }
        db.withTransaction {
            if (!merge) wipeRows()
            for (o in target.obligations) db.obligations().upsert(o.toModel().toEntity())
            for (s in target.steps) db.steps().upsert(StepEntity(s.id, s.obligationId, s.title, s.minutes, s.targetDay, s.done, "USER", 0, s.updatedAt))
            for (r in target.receipts) {
                val existing = db.receipts().all().firstOrNull { it.id == r.id }
                val file = existing?.imageFile ?: r.imageBase64?.let { runCatching { blobs.write(Base64.getDecoder().decode(it)) }.getOrNull() }
                db.receipts().upsert(ReceiptEntity(r.id, r.obligationId, r.note, file, r.at))
            }
            for (e in target.evidence) db.evidence().upsert(EvidenceEntity(e.id, e.obligationId, e.excerpt, e.at))
            for (s in target.seal) db.seals().upsert(SealEntity(s.obligationId, s.seq, s.at, s.field, s.oldValue, s.newValue, s.actor, s.reason, s.prevHash, s.hash))
            for (f in target.dismissed) db.dismissed().upsert(DismissedEntity(f, now().toEpochMilli()))
        }
        return stats
    }

    private suspend fun wipeRows() {
        db.obligations().clear(); db.candidates().clear(); db.steps().clear(); db.receipts().clear()
        db.evidence().clear(); db.seals().clear(); db.reminders().clear(); db.dismissed().clear()
    }

    /** Deletes everything the app holds except the vault itself. */
    suspend fun wipeAll() = db.withTransaction {
        wipeRows()
        db.settings().all().filter { it.key != "historyKeys" }.forEach { db.settings().delete(it.key) }
        blobs.deleteAll()
    }
}

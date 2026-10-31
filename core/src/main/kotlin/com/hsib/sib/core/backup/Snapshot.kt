package com.hsib.sib.core.backup

import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.FileLink
import com.hsib.sib.core.model.FogWindow
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.ManualPlan
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Part
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Repeat
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.seal.SealEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Serializable
data class ObligationDto(
    val id: String,
    val title: String,
    val notes: String = "",
    val category: String,
    val kind: String,
    val dueLocal: String? = null,
    val dueZone: String? = null,
    val duePrecision: String? = null,
    val dueFloating: Boolean = false,
    val fogFrom: String? = null,
    val fogTo: String? = null,
    val importance: String,
    val effortO: Int? = null,
    val effortL: Int? = null,
    val effortP: Int? = null,
    val lifecycle: String,
    val postponedUntil: String? = null,
    val progressMin: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val space: String = "WORK",
    val repeat: String = "NONE",
    val repeatAnchor: String? = null,
    val leadDays: String = "",
    val person: String = "",
    val relation: String = "",
    val since: String? = null,
    val parts: String = "",
    val manualPlan: String = "",
    val links: String = "",
) {
    fun toModel(): Obligation = Obligation(
        id = id, title = title, notes = notes,
        category = runCatching { Category.valueOf(category) }.getOrDefault(Category.OTHER),
        kind = runCatching { Kind.valueOf(kind) }.getOrDefault(Kind.DEADLINE),
        due = dueLocal?.let { DueSpec(LocalDateTime.parse(it), ZoneId.of(dueZone ?: "UTC"), Precision.valueOf(duePrecision ?: "DATE_TIME"), dueFloating) },
        fog = if (fogFrom != null && fogTo != null) FogWindow(LocalDate.parse(fogFrom), LocalDate.parse(fogTo)) else null,
        importance = runCatching { Importance.valueOf(importance) }.getOrDefault(Importance.NORMAL),
        effort = if (effortO != null && effortL != null && effortP != null) Effort(effortO, effortL, effortP) else null,
        lifecycle = runCatching { Lifecycle.valueOf(lifecycle) }.getOrDefault(Lifecycle.ACTIVE),
        postponedUntil = postponedUntil?.let(LocalDate::parse),
        progressMin = progressMin,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        completedAt = completedAt?.let(Instant::ofEpochMilli),
        space = runCatching { Space.valueOf(space) }.getOrDefault(Space.WORK),
        repeat = runCatching { Repeat.valueOf(repeat) }.getOrDefault(Repeat.NONE),
        repeatAnchor = repeatAnchor?.let(LocalDate::parse),
        leadDays = leadDays.split(",").mapNotNull { it.toIntOrNull() },
        person = person, relation = relation, since = since?.let(LocalDate::parse), parts = Part.decode(parts),
        manualPlan = ManualPlan.decode(manualPlan), links = FileLink.decode(links),
    )

    companion object {
        fun from(o: Obligation) = ObligationDto(
            id = o.id, title = o.title, notes = o.notes, category = o.category.name, kind = o.kind.name,
            dueLocal = o.due?.local?.toString(), dueZone = o.due?.zone?.id, duePrecision = o.due?.precision?.name, dueFloating = o.due?.floating ?: false,
            fogFrom = o.fog?.earliest?.toString(), fogTo = o.fog?.latest?.toString(),
            importance = o.importance.name,
            effortO = o.effort?.optimisticMin, effortL = o.effort?.likelyMin, effortP = o.effort?.pessimisticMin,
            lifecycle = o.lifecycle.name, postponedUntil = o.postponedUntil?.toString(), progressMin = o.progressMin,
            createdAt = o.createdAt.toEpochMilli(), updatedAt = o.updatedAt.toEpochMilli(), completedAt = o.completedAt?.toEpochMilli(),
            space = o.space.name, repeat = o.repeat.name, repeatAnchor = o.repeatAnchor?.toString(), leadDays = o.leadDays.joinToString(","),
            person = o.person, relation = o.relation, since = o.since?.toString(), parts = Part.encode(o.parts),
            manualPlan = ManualPlan.encode(o.manualPlan), links = FileLink.encode(o.links),
        )
    }
}

@Serializable
data class StepDto(val id: String, val obligationId: String, val title: String, val minutes: Int, val targetDay: String?, val done: Boolean, val updatedAt: Long)

@Serializable
data class ReceiptDto(val id: String, val obligationId: String, val note: String, val imageBase64: String? = null, val at: Long)

@Serializable
data class EvidenceDto(val id: String, val obligationId: String, val excerpt: String, val at: Long)

@Serializable
data class SealDto(
    val obligationId: String, val seq: Int, val at: Long, val field: String, val oldValue: String, val newValue: String,
    val actor: String, val reason: String, val prevHash: String, val hash: String,
) {
    fun toEntry() = SealEntry(seq, at, field, oldValue, newValue, actor, reason, prevHash, hash)

    companion object {
        fun from(obligationId: String, e: SealEntry) = SealDto(obligationId, e.seq, e.atEpochSec, e.field, e.oldValue, e.newValue, e.actor, e.reason, e.prevHash, e.hash)
    }
}

/** One class in the diary, for backups. */
@Serializable
data class ClassNoteDto(val id: String, val day: String, val subject: String, val topic: String, val faculty: String, val kind: String)

/** One class in the weekly routine, for backups. */
@Serializable
data class RoutineDto(val id: String, val day: Int, val kind: String, val shift: String, val subject: String, val room: String, val startMin: Int, val endMin: Int)

/** Everything a restore needs, and nothing it does not. Raw messages and documents are never part of it. */
@Serializable
data class BackupSnapshot(
    val format: Int = 1,
    val createdAtEpochSec: Long,
    val obligations: List<ObligationDto> = emptyList(),
    val steps: List<StepDto> = emptyList(),
    val receipts: List<ReceiptDto> = emptyList(),
    val evidence: List<EvidenceDto> = emptyList(),
    val seal: List<SealDto> = emptyList(),
    val dismissed: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val classNotes: List<ClassNoteDto> = emptyList(),
    val routine: List<RoutineDto> = emptyList(),
) {
    fun toBytes(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromBytes(bytes: ByteArray): BackupSnapshot = json.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
    }
}

data class MergeResult(val snapshot: BackupSnapshot, val added: Int, val updated: Int, val kept: Int, val conflicts: Int)

/**
 * Restoring into a phone that already has data must never lose a newer local edit. The newer edit wins per item;
 * when both sides changed at the very same instant but differ, the local copy is kept and counted as a conflict.
 */
object SnapshotMerge {
    fun merge(local: BackupSnapshot, remote: BackupSnapshot): MergeResult {
        var added = 0
        var updated = 0
        var kept = 0
        var conflicts = 0
        val byId = local.obligations.associateBy { it.id }.toMutableMap()
        for (r in remote.obligations) {
            val l = byId[r.id]
            when {
                l == null -> { byId[r.id] = r; added++ }
                r.updatedAt > l.updatedAt -> { byId[r.id] = r; updated++ }
                r.updatedAt == l.updatedAt && r != l -> { conflicts++; kept++ }
                else -> kept++
            }
        }
        fun <T> union(a: List<T>, b: List<T>, key: (T) -> String): List<T> {
            val m = LinkedHashMap<String, T>()
            a.forEach { m[key(it)] = it }
            b.forEach { if (key(it) !in m) m[key(it)] = it }
            return m.values.toList()
        }
        val seal = run {
            val m = LinkedHashMap<String, SealDto>()
            local.seal.forEach { m["${it.obligationId}#${it.seq}"] = it }
            for (r in remote.seal) {
                val k = "${r.obligationId}#${r.seq}"
                val l = m[k]
                if (l == null) m[k] = r else if (l.hash != r.hash) conflicts++
            }
            m.values.toList()
        }
        val steps = run {
            val m = LinkedHashMap<String, StepDto>()
            local.steps.forEach { m[it.id] = it }
            remote.steps.forEach { r -> val l = m[r.id]; if (l == null || r.updatedAt > l.updatedAt) m[r.id] = r }
            m.values.toList()
        }
        val merged = BackupSnapshot(
            createdAtEpochSec = maxOf(local.createdAtEpochSec, remote.createdAtEpochSec),
            obligations = byId.values.toList(),
            steps = steps,
            receipts = union(local.receipts, remote.receipts) { it.id },
            evidence = union(local.evidence, remote.evidence) { it.id },
            seal = seal,
            dismissed = (local.dismissed + remote.dismissed).distinct(),
            settings = remote.settings + local.settings,
        )
        return MergeResult(merged, added, updated, kept, conflicts)
    }
}

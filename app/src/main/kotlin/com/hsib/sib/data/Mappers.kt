package com.hsib.sib.data

import com.hsib.sib.core.model.Candidate
import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.Effort
import com.hsib.sib.core.model.FileLink
import com.hsib.sib.core.model.Flag
import com.hsib.sib.core.model.FogWindow
import com.hsib.sib.core.model.Importance
import com.hsib.sib.core.model.Kind
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.ManualPlan
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Part
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Reason
import com.hsib.sib.core.model.Relation
import com.hsib.sib.core.model.Repeat
import com.hsib.sib.core.model.Space
import com.hsib.sib.core.model.SourceType
import com.hsib.sib.core.model.Tier
import com.hsib.sib.core.seal.SealEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val json = Json { ignoreUnknownKeys = true }

fun ObligationEntity.toModel(): Obligation = Obligation(
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
    createdAt = Instant.ofEpochMilli(createdAt), updatedAt = Instant.ofEpochMilli(updatedAt),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    space = runCatching { Space.valueOf(space) }.getOrDefault(Space.WORK),
    repeat = runCatching { Repeat.valueOf(repeat) }.getOrDefault(Repeat.NONE),
    repeatAnchor = repeatAnchor?.let(LocalDate::parse),
    leadDays = leadDays.split(",").mapNotNull { it.toIntOrNull() },
    person = person, relation = relation,
    since = since?.let(LocalDate::parse),
    parts = Part.decode(parts),
    manualPlan = ManualPlan.decode(manualPlan),
    links = FileLink.decode(links),
)

fun Obligation.toEntity(): ObligationEntity = ObligationEntity(
    id = id, title = title, notes = notes, category = category.name, kind = kind.name,
    dueLocal = due?.local?.toString(), dueZone = due?.zone?.id, duePrecision = due?.precision?.name, dueFloating = due?.floating ?: false,
    fogFrom = fog?.earliest?.toString(), fogTo = fog?.latest?.toString(),
    importance = importance.name, effortO = effort?.optimisticMin, effortL = effort?.likelyMin, effortP = effort?.pessimisticMin,
    lifecycle = lifecycle.name, postponedUntil = postponedUntil?.toString(), progressMin = progressMin,
    createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(), completedAt = completedAt?.toEpochMilli(),
    space = space.name, repeat = repeat.name, repeatAnchor = repeatAnchor?.toString(), leadDays = leadDays.joinToString(","),
    person = person, relation = relation, since = since?.toString(),
    parts = Part.encode(parts), manualPlan = ManualPlan.encode(manualPlan), links = FileLink.encode(links),
)

@Serializable
private data class ReasonJson(val t: String, val w: Double)

@Serializable
private data class AltJson(val l: String, val z: String, val p: String)

data class StoredCandidate(val candidate: Candidate, val status: String, val snoozeUntil: Instant?, val createdAt: Instant, val expiresAt: Instant)

fun Candidate.toEntity(now: Instant, ttlDays: Int): CandidateEntity = CandidateEntity(
    id = id, title = title, category = category.name, kind = kind.name,
    dueLocal = due?.local?.toString(), dueZone = due?.zone?.id, duePrecision = due?.precision?.name,
    fogFrom = fog?.earliest?.toString(), fogTo = fog?.latest?.toString(),
    confidence = confidence, tier = tier.name, relation = relation.name, relatedId = relatedId,
    reasonsJson = json.encodeToString(ListSerializer(ReasonJson.serializer()), reasons.map { ReasonJson(it.text, it.weight) }),
    flagsCsv = flags.joinToString(",") { it.name }, source = sourceType.name, excerpt = excerpt,
    alternativesJson = json.encodeToString(ListSerializer(AltJson.serializer()), alternatives.map { AltJson(it.local.toString(), it.zone.id, it.precision.name) }),
    fingerprint = fingerprint, status = "PENDING", snoozeUntil = null,
    createdAt = now.toEpochMilli(), expiresAt = now.plusSeconds(ttlDays * 86_400L).toEpochMilli(),
)

fun CandidateEntity.toStored(): StoredCandidate {
    val due = dueLocal?.let { DueSpec(LocalDateTime.parse(it), ZoneId.of(dueZone ?: "UTC"), Precision.valueOf(duePrecision ?: "DATE_TIME")) }
    val alts = runCatching { json.decodeFromString(ListSerializer(AltJson.serializer()), alternativesJson) }.getOrDefault(emptyList())
        .map { DueSpec(LocalDateTime.parse(it.l), ZoneId.of(it.z), Precision.valueOf(it.p)) }
    val reasons = runCatching { json.decodeFromString(ListSerializer(ReasonJson.serializer()), reasonsJson) }.getOrDefault(emptyList()).map { Reason(it.t, it.w) }
    val c = Candidate(
        id = id, title = title, category = runCatching { Category.valueOf(category) }.getOrDefault(Category.OTHER),
        kind = runCatching { Kind.valueOf(kind) }.getOrDefault(Kind.DEADLINE), due = due,
        fog = if (fogFrom != null && fogTo != null) FogWindow(LocalDate.parse(fogFrom), LocalDate.parse(fogTo)) else null,
        confidence = confidence, tier = Tier.valueOf(tier), relation = Relation.valueOf(relation), relatedId = relatedId,
        reasons = reasons, flags = flagsCsv.split(",").mapNotNull { n -> Flag.values().firstOrNull { it.name == n } }.toSet(),
        sourceType = runCatching { SourceType.valueOf(source) }.getOrDefault(SourceType.MANUAL), excerpt = excerpt,
        alternatives = alts, fingerprint = fingerprint,
    )
    return StoredCandidate(c, status, snoozeUntil?.let(Instant::ofEpochMilli), Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(expiresAt))
}

data class UserStep(
    val id: String,
    val obligationId: String,
    val title: String,
    val minutes: Int,
    val targetDay: LocalDate?,
    val done: Boolean,
    val suggested: Boolean,
    val position: Int,
    val updatedAt: Instant,
)

fun StepEntity.toModel() = UserStep(id, obligationId, title, minutes, targetDay?.let(LocalDate::parse), done, origin == "SUGGESTED", position, Instant.ofEpochMilli(updatedAt))

fun UserStep.toEntity() = StepEntity(id, obligationId, title, minutes, targetDay?.toString(), done, if (suggested) "SUGGESTED" else "USER", position, updatedAt.toEpochMilli())

data class Receipt(val id: String, val obligationId: String, val note: String, val imageFile: String?, val at: Instant)

fun ReceiptEntity.toModel() = Receipt(id, obligationId, note, imageFile, Instant.ofEpochMilli(at))

data class Evidence(val id: String, val obligationId: String, val excerpt: String, val at: Instant)

fun EvidenceEntity.toModel() = Evidence(id, obligationId, excerpt, Instant.ofEpochMilli(at))

fun SealEntity.toEntry() = SealEntry(seq, at, field, oldValue, newValue, actor, reason, prevHash, hash)

fun SealEntry.toEntity(obligationId: String) = SealEntity(obligationId, seq, atEpochSec, field, oldValue, newValue, actor, reason, prevHash, hash)

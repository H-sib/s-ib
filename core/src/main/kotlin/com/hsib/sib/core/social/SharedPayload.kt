package com.hsib.sib.core.social

import com.hsib.sib.core.model.Category
import com.hsib.sib.core.model.DueSpec
import com.hsib.sib.core.model.FogWindow
import com.hsib.sib.core.model.Lifecycle
import com.hsib.sib.core.model.Limits
import com.hsib.sib.core.model.Obligation
import com.hsib.sib.core.model.Precision
import com.hsib.sib.core.model.Space
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The part of a deadline that everyone in a room shares: what it is and when it is due. Effort, importance, progress, steps,
 * lead days and reminders stay personal, so each person plans their own way to the same date. Close ones items are never
 * shared. This is what gets sealed with the room key; the server only ever holds the sealed text.
 *
 * Everything in here arrives from another phone, so it is treated as hostile: parsed defensively, cleaned and bounded.
 */
@Serializable
data class SharedPayload(
    val title: String,
    val notes: String = "",
    val category: String = Category.OTHER.name,
    val dueLocal: String? = null,
    val dueZone: String? = null,
    val duePrecision: String? = null,
    val fogFrom: String? = null,
    val fogTo: String? = null,
    val cancelled: Boolean = false,
    /** Goes up by one each time the owner changes the deadline. Only the owner can write it. */
    val rev: Long = 1,
) {
    /** The same payload with every text cleaned and cut to its limit. */
    fun cleaned(): SharedPayload = copy(
        title = Limits.line(title, SocialLimits.TITLE_CHARS).ifEmpty { "Shared deadline" },
        notes = Limits.text(notes, SocialLimits.NOTES_CHARS),
        category = Category.values().firstOrNull { it.name == category && !it.close }?.name ?: Category.OTHER.name,
        rev = rev.coerceAtLeast(1),
    )

    /** Null when the date is missing or cannot be read, so a damaged payload never produces a wrong date. */
    fun dueSpec(): DueSpec? = runCatching {
        val local = LocalDateTime.parse(dueLocal ?: return null)
        if (local.year !in 2000..2200) return null
        DueSpec(local, ZoneId.of(dueZone ?: return null), Precision.valueOf(duePrecision ?: return null))
    }.getOrNull()

    fun fogWindow(): FogWindow? = runCatching {
        val from = LocalDate.parse(fogFrom ?: return null)
        val to = LocalDate.parse(fogTo ?: return null)
        if (from.year !in 2000..2200 || to.year !in 2000..2200) null else FogWindow(from, to)
    }.getOrNull()

    /** A new local deadline for someone who has just joined. Their own importance, effort and reminders start at the defaults. */
    fun toNewObligation(id: String): Obligation {
        val p = cleaned()
        return Obligation(
            id = id, title = p.title, notes = p.notes,
            category = Category.values().first { it.name == p.category },
            due = p.dueSpec(), fog = p.fogWindow(),
            lifecycle = if (p.cancelled) Lifecycle.CANCELLED else Lifecycle.ACTIVE,
            space = Space.WORK,
        )
    }

    /** The local deadline with the shared fields replaced and the personal fields kept. A finished deadline stays finished. */
    fun applyTo(local: Obligation): Obligation {
        val p = cleaned()
        return local.copy(
            title = p.title, notes = p.notes,
            category = Category.values().first { it.name == p.category },
            due = p.dueSpec(), fog = p.fogWindow(),
            lifecycle = if (p.cancelled && local.lifecycle == Lifecycle.ACTIVE) Lifecycle.CANCELLED else local.lifecycle,
        )
    }

    fun toBytes(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Null for a Close ones item, which can never be shared. */
        fun from(o: Obligation, rev: Long): SharedPayload? {
            if (o.space != Space.WORK || o.category.close) return null
            return SharedPayload(
                title = o.title, notes = o.notes, category = o.category.name,
                dueLocal = o.due?.local?.toString(), dueZone = o.due?.zone?.id, duePrecision = o.due?.precision?.name,
                fogFrom = o.fog?.earliest?.toString(), fogTo = o.fog?.latest?.toString(),
                cancelled = o.lifecycle == Lifecycle.CANCELLED, rev = rev,
            ).cleaned()
        }

        /** Null if the bytes are too large or are not a payload. The result is already cleaned. */
        fun fromBytes(bytes: ByteArray): SharedPayload? {
            if (bytes.size > SocialLimits.MAX_SEALED_BYTES) return null
            return try {
                json.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8)).cleaned()
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

package com.hsib.sib.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.hsib.sib.core.backup.KeyHierarchy
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "obligation")
data class ObligationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val category: String,
    val kind: String,
    val dueLocal: String?,
    val dueZone: String?,
    val duePrecision: String?,
    val dueFloating: Boolean,
    val fogFrom: String?,
    val fogTo: String?,
    val importance: String,
    val effortO: Int?,
    val effortL: Int?,
    val effortP: Int?,
    val lifecycle: String,
    val postponedUntil: String?,
    val progressMin: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val space: String,
    val repeat: String,
    val repeatAnchor: String?,
    val leadDays: String,
    val person: String,
    val relation: String,
    val since: String?,
    @ColumnInfo(defaultValue = "") val parts: String = "",
)

@Entity(tableName = "candidate")
data class CandidateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val kind: String,
    val dueLocal: String?,
    val dueZone: String?,
    val duePrecision: String?,
    val fogFrom: String?,
    val fogTo: String?,
    val confidence: Double,
    val tier: String,
    val relation: String,
    val relatedId: String?,
    val reasonsJson: String,
    val flagsCsv: String,
    val source: String,
    val excerpt: String,
    val alternativesJson: String,
    val fingerprint: String,
    /** PENDING or SNOOZED. Accepted and dismissed candidates are deleted; only a fingerprint of a dismissal is kept. */
    val status: String,
    val snoozeUntil: Long?,
    val createdAt: Long,
    val expiresAt: Long,
)

@Entity(tableName = "step", indices = [Index("obligationId")])
data class StepEntity(
    @PrimaryKey val id: String,
    val obligationId: String,
    val title: String,
    val minutes: Int,
    val targetDay: String?,
    val done: Boolean,
    /** USER or SUGGESTED. A suggested step only exists here once the user accepted the suggested plan. */
    val origin: String,
    val position: Int,
    val updatedAt: Long,
)

@Entity(tableName = "receipt", indices = [Index("obligationId")])
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val obligationId: String,
    val note: String,
    /** Name of an encrypted file under files/receipts, or null for a text-only receipt. */
    val imageFile: String?,
    val at: Long,
)

@Entity(tableName = "evidence", indices = [Index("obligationId")])
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val obligationId: String,
    val excerpt: String,
    val at: Long,
)

@Entity(tableName = "seal", primaryKeys = ["obligationId", "seq"])
data class SealEntity(
    val obligationId: String,
    val seq: Int,
    val at: Long,
    val field: String,
    val oldValue: String,
    val newValue: String,
    val actor: String,
    val reason: String,
    val prevHash: String,
    val hash: String,
)

@Entity(tableName = "reminder", indices = [Index("obligationId"), Index("fireAt")])
data class ReminderEntity(
    @PrimaryKey val key: String,
    val obligationId: String,
    val kind: String,
    val fireAt: Long,
    /** SCHEDULED, FIRED, SNOOZED, CANCELLED or MISSED. */
    val state: String,
    val digestOnly: Boolean,
)

@Entity(tableName = "dismissed")
data class DismissedEntity(@PrimaryKey val fingerprint: String, val at: Long)

@Entity(tableName = "setting")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface ObligationDao {
    @Query("SELECT * FROM obligation ORDER BY createdAt")
    fun observeAll(): Flow<List<ObligationEntity>>

    @Query("SELECT * FROM obligation")
    suspend fun all(): List<ObligationEntity>

    @Query("SELECT * FROM obligation WHERE id = :id")
    suspend fun get(id: String): ObligationEntity?

    @Upsert suspend fun upsert(e: ObligationEntity)

    @Query("DELETE FROM obligation WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM obligation")
    suspend fun clear()
}

@Dao
interface CandidateDao {
    @Query("SELECT * FROM candidate ORDER BY confidence DESC")
    fun observeAll(): Flow<List<CandidateEntity>>

    @Query("SELECT * FROM candidate")
    suspend fun all(): List<CandidateEntity>

    @Query("SELECT * FROM candidate WHERE id = :id")
    suspend fun get(id: String): CandidateEntity?

    @Upsert suspend fun upsert(e: CandidateEntity)

    @Query("DELETE FROM candidate WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM candidate WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("DELETE FROM candidate")
    suspend fun clear()
}

@Dao
interface StepDao {
    @Query("SELECT * FROM step ORDER BY obligationId, position")
    fun observeAll(): Flow<List<StepEntity>>

    @Query("SELECT * FROM step")
    suspend fun all(): List<StepEntity>

    @Upsert suspend fun upsert(e: StepEntity)

    @Query("DELETE FROM step WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM step WHERE obligationId = :obligationId")
    suspend fun deleteFor(obligationId: String)

    @Query("DELETE FROM step")
    suspend fun clear()
}

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipt WHERE obligationId = :obligationId ORDER BY at DESC")
    fun observeFor(obligationId: String): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipt")
    suspend fun all(): List<ReceiptEntity>

    @Query("SELECT COUNT(*) FROM receipt")
    suspend fun count(): Int

    @Upsert suspend fun upsert(e: ReceiptEntity)

    @Query("DELETE FROM receipt WHERE obligationId = :obligationId")
    suspend fun deleteFor(obligationId: String)

    @Query("DELETE FROM receipt")
    suspend fun clear()
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence WHERE obligationId = :obligationId ORDER BY at DESC")
    fun observeFor(obligationId: String): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence")
    suspend fun all(): List<EvidenceEntity>

    @Query("SELECT COUNT(*) FROM evidence")
    suspend fun count(): Int

    @Upsert suspend fun upsert(e: EvidenceEntity)

    @Query("DELETE FROM evidence WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM evidence WHERE obligationId = :obligationId")
    suspend fun deleteFor(obligationId: String)

    @Query("DELETE FROM evidence")
    suspend fun clear()
}

@Dao
interface SealDao {
    @Query("SELECT * FROM seal WHERE obligationId = :obligationId ORDER BY seq")
    suspend fun forObligation(obligationId: String): List<SealEntity>

    @Query("SELECT * FROM seal WHERE obligationId = :obligationId ORDER BY seq")
    fun observeFor(obligationId: String): Flow<List<SealEntity>>

    @Query("SELECT * FROM seal")
    suspend fun all(): List<SealEntity>

    @Upsert suspend fun upsert(e: SealEntity)

    @Query("DELETE FROM seal WHERE obligationId = :obligationId")
    suspend fun deleteFor(obligationId: String)

    @Query("DELETE FROM seal")
    suspend fun clear()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder WHERE state = 'SCHEDULED' ORDER BY fireAt")
    suspend fun scheduled(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE key = :key")
    suspend fun get(key: String): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE state IN ('MISSED') ORDER BY fireAt DESC")
    fun observeMissed(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder")
    suspend fun all(): List<ReminderEntity>

    @Upsert suspend fun upsert(e: ReminderEntity)

    @Upsert suspend fun upsertAll(list: List<ReminderEntity>)

    @Query("DELETE FROM reminder WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM reminder WHERE obligationId = :obligationId")
    suspend fun deleteFor(obligationId: String)

    @Query("DELETE FROM reminder WHERE state = 'SCHEDULED'")
    suspend fun deleteScheduled()

    @Query("DELETE FROM reminder WHERE state IN ('FIRED','CANCELLED','MISSED') AND fireAt < :before")
    suspend fun pruneOld(before: Long)

    @Query("DELETE FROM reminder")
    suspend fun clear()
}

@Dao
interface DismissedDao {
    @Query("SELECT fingerprint FROM dismissed")
    suspend fun fingerprints(): List<String>

    @Query("SELECT COUNT(*) FROM dismissed")
    suspend fun count(): Int

    @Upsert suspend fun upsert(e: DismissedEntity)

    @Query("DELETE FROM dismissed WHERE fingerprint = :fingerprint")
    suspend fun delete(fingerprint: String)

    @Query("DELETE FROM dismissed")
    suspend fun clear()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM setting")
    suspend fun all(): List<SettingEntity>

    @Upsert suspend fun upsert(e: SettingEntity)

    @Query("DELETE FROM setting WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM setting")
    suspend fun clear()
}

@Database(
    entities = [
        ObligationEntity::class, CandidateEntity::class, StepEntity::class, ReceiptEntity::class, EvidenceEntity::class,
        SealEntity::class, ReminderEntity::class, DismissedEntity::class, SettingEntity::class,
        RoomEntity::class, RoomKeyEntity::class, RoomMemberEntity::class, RoomMessageEntity::class, BlockedEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class SibDatabase : RoomDatabase() {
    abstract fun obligations(): ObligationDao
    abstract fun candidates(): CandidateDao
    abstract fun steps(): StepDao
    abstract fun receipts(): ReceiptDao
    abstract fun evidence(): EvidenceDao
    abstract fun seals(): SealDao
    abstract fun reminders(): ReminderDao
    abstract fun dismissed(): DismissedDao
    abstract fun settings(): SettingDao
    abstract fun rooms(): RoomDao
    abstract fun roomKeys(): RoomKeyDao
    abstract fun roomMembers(): RoomMemberDao
    abstract fun roomMessages(): RoomMessageDao
    abstract fun blocked(): BlockedDao

    companion object {
        /** Opens the encrypted database. The SQLCipher key is derived from the vault master and given as a raw key. */
        fun open(context: Context, master: ByteArray): SibDatabase {
            System.loadLibrary("sqlcipher")
            val raw = KeyHierarchy.databaseKey(master).joinToString("") { "%02x".format(it) }
            val factory = SupportOpenHelperFactory("x'$raw'".toByteArray())
            return Room.databaseBuilder(context.applicationContext, SibDatabase::class.java, Vault.DB_NAME)
                .openHelperFactory(factory)
                .build()
        }
    }
}

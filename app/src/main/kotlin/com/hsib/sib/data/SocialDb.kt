package com.hsib.sib.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A shared deadline's link to the local deadline it is shown as, plus what this phone knows about it.
 * Everything here lives in the same encrypted database as the rest of the app and never goes into a backup,
 * except the device key (see Store.exportSnapshot), because rooms are fetched again from the server.
 */
@Entity(tableName = "room", indices = [Index(value = ["obligationId"], unique = true)])
data class RoomEntity(
    @PrimaryKey val roomId: String,
    val obligationId: String,
    val ownerUid: String,
    val keyVersion: Int,
    /** The newest payload revision already applied to the local deadline. */
    val appliedRev: Long,
    val memberCount: Int,
    /** Newest message time fetched, so the next sync asks only for what is newer. */
    val messageCursor: Long,
    val lastReadAt: Long,
    val lastSyncAt: Long,
    /** My own last message time, for the one a second rule. */
    val myLastMessageAt: Long,
    /** Set when the server says I am no longer a member, so the screen can say so instead of failing. */
    val removed: Boolean,
    /** Fingerprint of the shared fields as last sent or received, so edits to personal fields never bump the revision. */
    val payloadHash: String,
    /** False while this phone holds no key for the room's current version, so details and chat cannot be opened. */
    val canRead: Boolean,
)

/** A room key this phone holds, one row per version, so older messages stay readable after a key change. */
@Entity(tableName = "room_key", primaryKeys = ["roomId", "version"])
data class RoomKeyEntity(val roomId: String, val version: Int, val keyText: String)

@Entity(tableName = "room_member", primaryKeys = ["roomId", "uid"])
data class RoomMemberEntity(
    val roomId: String,
    val uid: String,
    val role: String,
    val displayName: String,
    val joinedAt: Long,
    val doneAt: Long?,
    val publicKey: String?,
)

/** A chat message after it was opened on this phone. Stored decrypted because the whole database is encrypted. */
@Entity(tableName = "room_message", primaryKeys = ["roomId", "id"], indices = [Index("roomId", "createdAt")])
data class RoomMessageEntity(
    val roomId: String,
    val id: String,
    val senderUid: String,
    val createdAt: Long,
    val kind: String,
    val text: String,
)

@Entity(tableName = "blocked_person")
data class BlockedEntity(@PrimaryKey val uid: String, val name: String, val at: Long)

@Dao
interface RoomDao {
    @Query("SELECT * FROM room ORDER BY lastSyncAt DESC")
    fun observeAll(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM room")
    suspend fun all(): List<RoomEntity>

    @Query("SELECT * FROM room WHERE roomId = :roomId")
    suspend fun get(roomId: String): RoomEntity?

    @Query("SELECT * FROM room WHERE obligationId = :obligationId")
    suspend fun forObligation(obligationId: String): RoomEntity?

    @Upsert suspend fun upsert(e: RoomEntity)

    @Query("DELETE FROM room WHERE roomId = :roomId")
    suspend fun delete(roomId: String)

    @Query("DELETE FROM room")
    suspend fun clear()
}

@Dao
interface RoomKeyDao {
    @Query("SELECT * FROM room_key WHERE roomId = :roomId")
    suspend fun forRoom(roomId: String): List<RoomKeyEntity>

    @Upsert suspend fun upsert(e: RoomKeyEntity)

    @Query("DELETE FROM room_key WHERE roomId = :roomId")
    suspend fun deleteFor(roomId: String)

    @Query("DELETE FROM room_key")
    suspend fun clear()
}

@Dao
interface RoomMemberDao {
    @Query("SELECT * FROM room_member ORDER BY roomId, joinedAt")
    fun observeAll(): Flow<List<RoomMemberEntity>>

    @Query("SELECT * FROM room_member WHERE roomId = :roomId ORDER BY joinedAt")
    suspend fun forRoom(roomId: String): List<RoomMemberEntity>

    @Upsert suspend fun upsertAll(list: List<RoomMemberEntity>)

    @Query("DELETE FROM room_member WHERE roomId = :roomId")
    suspend fun deleteFor(roomId: String)

    @Query("DELETE FROM room_member")
    suspend fun clear()
}

@Dao
interface RoomMessageDao {
    @Query("SELECT * FROM room_message WHERE roomId = :roomId ORDER BY createdAt, id")
    fun observeFor(roomId: String): Flow<List<RoomMessageEntity>>

    @Query("SELECT roomId, COUNT(*) AS unread FROM room_message m WHERE m.createdAt > (SELECT lastReadAt FROM room WHERE room.roomId = m.roomId) AND m.senderUid != :me AND m.kind = 'TEXT' GROUP BY roomId")
    fun observeUnread(me: String): Flow<List<UnreadRow>>

    @Query("SELECT * FROM room_message WHERE roomId = :roomId")
    suspend fun forRoom(roomId: String): List<RoomMessageEntity>

    @Upsert suspend fun upsertAll(list: List<RoomMessageEntity>)

    @Query("DELETE FROM room_message WHERE roomId = :roomId AND id = :id")
    suspend fun delete(roomId: String, id: String)

    @Query("DELETE FROM room_message WHERE roomId = :roomId")
    suspend fun deleteFor(roomId: String)

    @Query("DELETE FROM room_message")
    suspend fun clear()
}

data class UnreadRow(val roomId: String, val unread: Int)

@Dao
interface BlockedDao {
    @Query("SELECT * FROM blocked_person ORDER BY at DESC")
    fun observeAll(): Flow<List<BlockedEntity>>

    @Query("SELECT * FROM blocked_person")
    suspend fun all(): List<BlockedEntity>

    @Upsert suspend fun upsert(e: BlockedEntity)

    @Query("DELETE FROM blocked_person WHERE uid = :uid")
    suspend fun delete(uid: String)

    @Query("DELETE FROM blocked_person")
    suspend fun clear()
}

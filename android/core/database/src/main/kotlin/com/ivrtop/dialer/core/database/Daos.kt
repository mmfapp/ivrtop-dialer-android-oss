package com.ivrtop.dialer.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, rowid")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, rowid")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE username = :username AND domain = :domain LIMIT 1")
    suspend fun findByIdentity(username: String, domain: String): AccountEntity?

    @Upsert suspend fun upsert(entity: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM accounts")
    suspend fun nextSortOrder(): Int
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_log ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 1000): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_log WHERE matchKey = :matchKey ORDER BY startedAt DESC")
    fun observeForNumber(matchKey: String): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_log WHERE id = :id")
    suspend fun get(id: Long): CallLogEntity?

    @Query("SELECT * FROM call_log ORDER BY startedAt DESC LIMIT 1")
    suspend fun last(): CallLogEntity?

    @Query("SELECT * FROM call_log WHERE type = 'outgoing' ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastOutgoing(): CallLogEntity?

    @Query("SELECT COUNT(*) FROM call_log WHERE matchKey = :matchKey AND startedAt >= :since AND type IN ('missed','rejected')")
    suspend fun recentMissedFrom(matchKey: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM call_log WHERE type = 'missed' AND isRead = 0")
    fun observeUnreadMissed(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CallLogEntity): Long

    @Query("UPDATE call_log SET isRead = 1 WHERE type = 'missed' AND isRead = 0")
    suspend fun markMissedRead()

    @Query("DELETE FROM call_log WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM call_log")
    suspend fun clear()

    @Query("DELETE FROM call_log WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
abstract class FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY position")
    abstract fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE speedDialDigit = :digit LIMIT 1")
    abstract suspend fun bySpeedDial(digit: Int): FavoriteEntity?

    @Query("SELECT * FROM favorites")
    abstract suspend fun getAll(): List<FavoriteEntity>

    @Upsert abstract suspend fun upsert(entity: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE favorites SET position = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Int)

    /** Abstract class (not interface) so Room wraps this body in a single transaction. */
    @Transaction
    open suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setPosition(id, index) }
    }

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM favorites")
    abstract suspend fun nextPosition(): Int
}

@Dao
interface BlockDao {
    @Query("SELECT * FROM block_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules")
    suspend fun getAll(): List<BlockRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BlockRuleEntity): Long

    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LocalContactDao {
    @Query("SELECT * FROM local_contacts ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<LocalContactEntity>>

    @Upsert suspend fun upsert(entity: LocalContactEntity)

    @Query("DELETE FROM local_contacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM local_contacts WHERE source = :source")
    suspend fun deleteBySource(source: String)
}

@Dao
interface PushSeenDao {
    /** Returns -1 when the call id was already seen (IGNORE) → caller drops the duplicate push. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PushSeenEntity): Long

    @Query("SELECT * FROM push_seen WHERE callId = :callId")
    suspend fun get(callId: String): PushSeenEntity?

    @Query("UPDATE push_seen SET state = :state WHERE callId = :callId")
    suspend fun setState(callId: String, state: String)

    @Query("DELETE FROM push_seen WHERE receivedAt < :before")
    suspend fun prune(before: Long)
}

@Dao
interface VoicemailDao {
    @Query("SELECT * FROM voicemail ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<VoicemailEntity>>

    @Upsert suspend fun upsertAll(items: List<VoicemailEntity>)

    @Query("UPDATE voicemail SET isNew = 0 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("DELETE FROM voicemail WHERE id = :id")
    suspend fun delete(id: String)
}

package com.ivrtop.dialer.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Account row. `configJson` = serialized SipAccount (without password). */
@Entity(tableName = "accounts", indices = [Index(value = ["username", "domain"], unique = true)])
data class AccountEntity(
    @PrimaryKey val id: String,
    val username: String,
    val domain: String,
    val sortOrder: Int,
    val configJson: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "call_log",
    indices = [Index("startedAt"), Index("matchKey"), Index("accountId"), Index(value = ["callId"], unique = true)],
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callId: String,
    val accountId: String,
    val type: String,
    val number: String,
    val normalizedNumber: String?,
    val matchKey: String,
    val displayName: String?,
    val startedAt: Long,
    val durationSec: Int,
    val endReason: String?,
    val sipStatus: Int?,
    val isRead: Boolean,
)

@Entity(tableName = "favorites", indices = [Index("position")])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: String?,
    val displayName: String,
    val number: String,
    val photoUri: String?,
    val preferredAccountId: String?,
    val position: Int,
    val speedDialDigit: Int?,
    val blfEnabled: Boolean,
)

@Entity(tableName = "block_rules", indices = [Index(value = ["pattern", "kind", "list"], unique = true)])
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val kind: String,
    val list: String,
    val label: String?,
    val accountId: String?,
    val syncToServer: Boolean,
    val createdAt: Long,
)

/** App-local contacts (not in the system address book). */
@Entity(tableName = "local_contacts")
data class LocalContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val phonesJson: String,
    val source: String,
    val updatedAt: Long,
)

/** Push de-duplication: one row per IVRTOP call id, pruned after 24h. */
@Entity(tableName = "push_seen")
data class PushSeenEntity(
    @PrimaryKey val callId: String,
    val receivedAt: Long,
    val state: String,
)

@Entity(tableName = "voicemail", indices = [Index("accountId")])
data class VoicemailEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val fromNumber: String,
    val fromName: String?,
    val receivedAt: Long,
    val durationSec: Int,
    val isNew: Boolean,
    val audioUrl: String?,
    val transcript: String?,
)

package com.ivrtop.dialer.core.data

import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.database.CallLogDao
import com.ivrtop.dialer.core.database.CallLogEntity
import com.ivrtop.dialer.core.models.CallDirection
import com.ivrtop.dialer.core.models.CallEndReason
import com.ivrtop.dialer.core.models.CallLogEntry
import com.ivrtop.dialer.core.models.CallLogType
import com.ivrtop.dialer.core.models.CallSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A row in the Recents list: consecutive calls with the same number/type on the same day collapse. */
data class CallLogGroup(
    val key: String,
    val latest: CallLogEntry,
    val entries: List<CallLogEntry>,
) {
    val count: Int get() = entries.size
}

/**
 * App-private call history (Room). We deliberately do NOT write to the system CallLog provider:
 * READ/WRITE_CALL_LOG are restricted permissions under Google Play policy and are not required
 * for a VoIP app that is not the default dialer.
 */
@Singleton
class CallHistoryRepository @Inject constructor(private val dao: CallLogDao) {

    val entries: Flow<List<CallLogEntry>> = dao.observeRecent().map { list -> list.map { it.toModel() } }

    val groups: Flow<List<CallLogGroup>> = entries.map(::group)

    val unreadMissed: Flow<Int> = dao.observeUnreadMissed()

    fun forNumber(number: String): Flow<List<CallLogEntry>> =
        dao.observeForNumber(PhoneNumbers.matchKey(number)).map { l -> l.map { it.toModel() } }

    suspend fun get(id: Long): CallLogEntry? = dao.get(id)?.toModel()

    suspend fun lastDialed(): CallLogEntry? = dao.lastOutgoing()?.toModel()

    suspend fun recentMissedCount(number: String, sinceMillis: Long): Int =
        dao.recentMissedFrom(PhoneNumbers.matchKey(number), sinceMillis)

    suspend fun record(snapshot: CallSnapshot, blocked: Boolean = false) {
        val duration = snapshot.connectedAtMillis?.let { c -> ((snapshot.endedAtMillis ?: c) - c) / 1000 }?.toInt() ?: 0
        val type = when {
            blocked -> CallLogType.BLOCKED
            snapshot.direction == CallDirection.OUTGOING -> when {
                snapshot.connectedAtMillis != null -> CallLogType.OUTGOING
                snapshot.endReason == CallEndReason.CANCELLED -> CallLogType.CANCELLED
                else -> CallLogType.FAILED
            }
            snapshot.connectedAtMillis != null -> CallLogType.INCOMING
            snapshot.endReason == CallEndReason.DECLINED || snapshot.endReason == CallEndReason.REJECTED_DND -> CallLogType.REJECTED
            snapshot.endReason == CallEndReason.ANSWERED_ELSEWHERE -> CallLogType.INCOMING
            else -> CallLogType.MISSED
        }
        dao.insert(
            CallLogEntity(
                callId = snapshot.callId,
                accountId = snapshot.accountId,
                type = type.name.lowercase(),
                number = snapshot.remoteNumber,
                normalizedNumber = PhoneNumbers.normalize(snapshot.remoteNumber),
                matchKey = PhoneNumbers.matchKey(snapshot.remoteNumber),
                displayName = snapshot.remoteDisplayName,
                startedAt = snapshot.startedAtMillis,
                durationSec = duration,
                endReason = snapshot.endReason?.name?.lowercase(),
                sipStatus = snapshot.sipStatus,
                isRead = type != CallLogType.MISSED,
            ),
        )
    }

    suspend fun markMissedRead() = dao.markMissedRead()
    suspend fun delete(ids: List<Long>) = dao.delete(ids)
    suspend fun clear() = dao.clear()

    /** CSV export (user initiated, Settings › History › Export). */
    fun toCsv(list: List<CallLogEntry>): String = buildString {
        appendLine("date,type,number,name,duration_sec,account,end_reason,sip_status")
        list.forEach {
            appendLine(listOf(Instant.ofEpochMilli(it.startedAtMillis).toString(), it.type.name.lowercase(), it.number,
                (it.displayName ?: "").replace(",", " "), it.durationSec, it.accountId, it.endReason?.name?.lowercase().orEmpty(),
                it.sipStatus?.toString().orEmpty()).joinToString(","))
        }
    }

    private fun group(list: List<CallLogEntry>): List<CallLogGroup> {
        val out = mutableListOf<CallLogGroup>()
        var bucket = mutableListOf<CallLogEntry>()
        fun flush() {
            if (bucket.isNotEmpty()) out += CallLogGroup("${bucket.first().id}", bucket.first(), bucket.toList())
            bucket = mutableListOf()
        }
        val zone = ZoneId.systemDefault()
        list.forEach { e ->
            val prev = bucket.lastOrNull()
            val sameGroup = prev != null &&
                PhoneNumbers.matchKey(prev.number) == PhoneNumbers.matchKey(e.number) &&
                (prev.type == CallLogType.MISSED) == (e.type == CallLogType.MISSED) &&
                Instant.ofEpochMilli(prev.startedAtMillis).atZone(zone).toLocalDate() ==
                Instant.ofEpochMilli(e.startedAtMillis).atZone(zone).toLocalDate()
            if (!sameGroup) flush()
            bucket += e
        }
        flush()
        return out
    }

    private fun CallLogEntity.toModel() = CallLogEntry(
        id = id, callId = callId, accountId = accountId,
        type = CallLogType.entries.firstOrNull { it.name.equals(type, true) } ?: CallLogType.FAILED,
        number = number, normalizedNumber = normalizedNumber, displayName = displayName,
        startedAtMillis = startedAt, durationSec = durationSec,
        endReason = CallEndReason.entries.firstOrNull { it.name.equals(endReason, true) },
        sipStatus = sipStatus, isRead = isRead,
    )
}

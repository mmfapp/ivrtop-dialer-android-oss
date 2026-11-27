package com.ivrtop.dialer.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BlockKind { @SerialName("exact") EXACT, @SerialName("prefix") PREFIX, @SerialName("anonymous") ANONYMOUS }

@Serializable
enum class BlockListType { @SerialName("deny") DENY, @SerialName("allow") ALLOW }

@Serializable
data class BlockRule(
    val id: Long = 0,
    val pattern: String,
    val kind: BlockKind,
    val list: BlockListType = BlockListType.DENY,
    val label: String? = null,
    val accountId: String? = null,
    /** Also pushed to the PBX blacklist when the account is IVRTOP-managed. */
    val syncToServer: Boolean = false,
    val createdAtMillis: Long,
)

@Serializable
enum class DndAction {
    /** Ring silently, show notification, log as missed. */
    @SerialName("silence") SILENCE,
    /** Respond 486 Busy Here / 603 Decline. */
    @SerialName("reject") REJECT,
    /** Respond 486 – PBX routes busy to voicemail. */
    @SerialName("voicemail") VOICEMAIL,
    /** 302 Moved Temporarily to [DndConfig.forwardTo] (requires PBX support). */
    @SerialName("forward") FORWARD,
}

@Serializable
data class DndSchedule(
    val id: Long = 0,
    val enabled: Boolean = true,
    /** ISO day numbers 1 = Monday .. 7 = Sunday */
    val days: Set<Int>,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val accountId: String? = null,
)

@Serializable
data class DndConfig(
    val enabledGlobally: Boolean = false,
    /** epoch millis; null = until turned off manually */
    val until: Long? = null,
    val action: DndAction = DndAction.SILENCE,
    val forwardTo: String? = null,
    val allowFavorites: Boolean = true,
    val allowRepeatCallers: Boolean = false,
    val repeatCallerWindowMin: Int = 15,
    val schedules: List<DndSchedule> = emptyList(),
)

/** Outcome of evaluating an incoming call against block list + DND. */
@Serializable
sealed interface IncomingDecision {
    @Serializable data object Ring : IncomingDecision
    @Serializable data object RingSilently : IncomingDecision
    @Serializable data class Reject(val sipStatus: Int, val reason: CallEndReason) : IncomingDecision
    @Serializable data class Forward(val target: String) : IncomingDecision
}

@Serializable
data class VoicemailMessage(
    val id: String,
    val accountId: String,
    val from: String,
    val fromName: String? = null,
    val receivedAtMillis: Long,
    val durationSec: Int,
    val isNew: Boolean,
    val audioUrl: String? = null,
    val transcript: String? = null,
)

@Serializable
data class MessageWaiting(val accountId: String, val newMessages: Int, val oldMessages: Int)

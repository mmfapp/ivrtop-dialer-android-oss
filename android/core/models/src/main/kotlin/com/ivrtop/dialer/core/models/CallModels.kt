package com.ivrtop.dialer.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CallDirection { @SerialName("incoming") INCOMING, @SerialName("outgoing") OUTGOING }

/**
 * Call states – see docs/CALL_STATE_MACHINE.md. Identical names are used on iOS.
 */
@Serializable
enum class CallPhase {
    /** Outgoing: user tapped call, SIP not yet sent (DNS / registration wait). */
    DIALING,
    /** Outgoing: INVITE sent, no provisional response yet. */
    CONNECTING,
    /** Outgoing: 180/183 received. */
    RINGING_REMOTE,
    /** Incoming: push received, INVITE not yet arrived. */
    INCOMING_PENDING,
    /** Incoming: INVITE received, user is being alerted. */
    RINGING_LOCAL,
    /** Incoming: user answered, waiting for media/ACK. */
    ANSWERING,
    ACTIVE,
    HOLDING,
    REMOTE_HELD,
    TRANSFERRING,
    /** Terminal states */
    ENDED;

    val isTerminal get() = this == ENDED
    val isRinging get() = this == RINGING_LOCAL || this == INCOMING_PENDING
    val isConnected get() = this == ACTIVE || this == HOLDING || this == REMOTE_HELD || this == TRANSFERRING
}

@Serializable
enum class CallEndReason {
    @SerialName("local_hangup") LOCAL_HANGUP,
    @SerialName("remote_hangup") REMOTE_HANGUP,
    @SerialName("declined") DECLINED,
    @SerialName("busy") BUSY,
    @SerialName("no_answer") NO_ANSWER,
    @SerialName("unavailable") UNAVAILABLE,
    @SerialName("not_found") NOT_FOUND,
    @SerialName("cancelled") CANCELLED,
    @SerialName("answered_elsewhere") ANSWERED_ELSEWHERE,
    @SerialName("missed") MISSED,
    @SerialName("rejected_dnd") REJECTED_DND,
    @SerialName("blocked") BLOCKED,
    @SerialName("network_lost") NETWORK_LOST,
    @SerialName("server_error") SERVER_ERROR,
    @SerialName("auth_failed") AUTH_FAILED,
    @SerialName("transferred") TRANSFERRED,
    @SerialName("failed") FAILED,
}

@Serializable
enum class AudioRoute { EARPIECE, SPEAKER, WIRED_HEADSET, BLUETOOTH, STREAMING }

@Serializable
data class AudioEndpoint(val id: String, val name: String, val route: AudioRoute)

@Serializable
data class CallQuality(
    val codec: String? = null,
    val rttMs: Int? = null,
    val jitterMs: Int? = null,
    val lossPercent: Float? = null,
    /** Estimated MOS 1.0 – 4.5 (E-model simplified). */
    val mos: Float? = null,
) {
    val level: QualityLevel
        get() = when {
            mos == null -> QualityLevel.UNKNOWN
            mos >= 4.0f -> QualityLevel.EXCELLENT
            mos >= 3.6f -> QualityLevel.GOOD
            mos >= 3.1f -> QualityLevel.FAIR
            else -> QualityLevel.POOR
        }
}

enum class QualityLevel { UNKNOWN, EXCELLENT, GOOD, FAIR, POOR }

/** What the engine + PBX can actually do for this call. UI hides anything false. */
@Serializable
data class CallCapabilities(
    val canHold: Boolean = true,
    val canMute: Boolean = true,
    val canDtmf: Boolean = true,
    val canBlindTransfer: Boolean = true,
    val canAttendedTransfer: Boolean = true,
    val canAddCall: Boolean = true,
    val canMerge: Boolean = false,
    val canSwap: Boolean = false,
    val canRecord: Boolean = false,
)

/** Snapshot of one call exposed to UI. Produced exclusively by the CallStateMachine. */
@Serializable
data class CallSnapshot(
    val callId: String,
    val accountId: String,
    val direction: CallDirection,
    val phase: CallPhase,
    val remoteNumber: String,
    val remoteDisplayName: String? = null,
    val remoteUri: String? = null,
    val startedAtMillis: Long,
    val connectedAtMillis: Long? = null,
    val endedAtMillis: Long? = null,
    val endReason: CallEndReason? = null,
    val sipStatus: Int? = null,
    val muted: Boolean = false,
    val onHold: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.EARPIECE,
    val availableRoutes: List<AudioEndpoint> = emptyList(),
    val quality: CallQuality = CallQuality(),
    val capabilities: CallCapabilities = CallCapabilities(),
    val conferenceIds: List<String> = emptyList(),
    /** Push correlation id from IVRTOP backend (X-IVRTOP-Call-ID header). */
    val pushCallId: String? = null,
    val recording: Boolean = false,
)

@Serializable
enum class CallLogType {
    @SerialName("incoming") INCOMING,
    @SerialName("outgoing") OUTGOING,
    @SerialName("missed") MISSED,
    @SerialName("rejected") REJECTED,
    @SerialName("failed") FAILED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("blocked") BLOCKED,
}

/** Mirrors contracts/schemas/call-history.schema.json */
@Serializable
data class CallLogEntry(
    val id: Long = 0,
    val callId: String,
    val accountId: String,
    val type: CallLogType,
    val number: String,
    val normalizedNumber: String?,
    val displayName: String?,
    val startedAtMillis: Long,
    val durationSec: Int,
    val endReason: CallEndReason?,
    val sipStatus: Int?,
    val isRead: Boolean = true,
)

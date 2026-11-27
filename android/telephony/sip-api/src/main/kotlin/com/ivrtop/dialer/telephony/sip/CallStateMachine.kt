package com.ivrtop.dialer.telephony.sip

import com.ivrtop.dialer.core.models.AudioEndpoint
import com.ivrtop.dialer.core.models.AudioRoute
import com.ivrtop.dialer.core.models.CallCapabilities
import com.ivrtop.dialer.core.models.CallDirection
import com.ivrtop.dialer.core.models.CallEndReason
import com.ivrtop.dialer.core.models.CallPhase
import com.ivrtop.dialer.core.models.CallQuality
import com.ivrtop.dialer.core.models.CallSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic reducer for all calls. Every state change in the app goes through [dispatch];
 * the result is published as an immutable list. Callers must invoke [dispatch] from a single
 * serialized context (the CallManager's actor) – the machine itself is not thread-safe by design,
 * which keeps it trivially testable and identical to the iOS implementation (docs/CALL_STATE_MACHINE.md).
 *
 * Invalid transitions are ignored and reported through [onIgnored] (never crash a call).
 */
class CallStateMachine(
    private val now: () -> Long,
    private val onIgnored: (String) -> Unit = {},
) {
    private val _calls = MutableStateFlow<Map<String, CallSnapshot>>(emptyMap())
    val calls: StateFlow<Map<String, CallSnapshot>> = _calls.asStateFlow()

    operator fun get(callId: String): CallSnapshot? = _calls.value[callId]

    fun findByPushId(pushCallId: String): CallSnapshot? = _calls.value.values.firstOrNull { it.pushCallId == pushCallId }

    fun active(): List<CallSnapshot> = _calls.value.values.filter { !it.phase.isTerminal }

    sealed interface Event {
        val callId: String

        data class OutgoingRequested(
            override val callId: String, val accountId: String, val number: String,
            val displayName: String?, val remoteUri: String,
        ) : Event
        data class OutgoingSent(override val callId: String) : Event
        data class RemoteRinging(override val callId: String, val earlyMedia: Boolean) : Event

        data class IncomingPush(
            override val callId: String, val accountId: String, val number: String,
            val displayName: String?, val pushCallId: String,
        ) : Event
        data class IncomingInvite(
            override val callId: String, val accountId: String, val number: String,
            val displayName: String?, val remoteUri: String, val pushCallId: String?,
        ) : Event
        data class AnswerRequested(override val callId: String) : Event
        data class Connected(override val callId: String) : Event

        data class HoldChanged(override val callId: String, val localHold: Boolean, val remoteHold: Boolean) : Event
        data class MuteChanged(override val callId: String, val muted: Boolean) : Event
        data class AudioChanged(override val callId: String, val route: AudioRoute, val available: List<AudioEndpoint>) : Event
        data class QualityUpdated(override val callId: String, val quality: CallQuality) : Event
        data class CapabilitiesChanged(override val callId: String, val capabilities: CallCapabilities) : Event
        data class TransferStarted(override val callId: String) : Event
        data class TransferFailed(override val callId: String) : Event
        data class ConferenceChanged(override val callId: String, val members: List<String>) : Event

        data class Ended(
            override val callId: String, val reason: CallEndReason, val sipStatus: Int?,
        ) : Event
        data class Removed(override val callId: String) : Event
    }

    fun dispatch(event: Event): CallSnapshot? {
        val current = _calls.value[event.callId]
        val next: CallSnapshot? = reduce(current, event)
        if (next == null) {
            if (event is Event.Removed) {
                _calls.value = _calls.value - event.callId
            } else {
                onIgnored("ignored ${event::class.simpleName} in ${current?.phase}")
            }
            return null
        }
        _calls.value = _calls.value + (next.callId to next)
        return next
    }

    private fun reduce(c: CallSnapshot?, e: Event): CallSnapshot? = when (e) {
        is Event.OutgoingRequested -> if (c != null) null else CallSnapshot(
            callId = e.callId, accountId = e.accountId, direction = CallDirection.OUTGOING,
            phase = CallPhase.DIALING, remoteNumber = e.number, remoteDisplayName = e.displayName,
            remoteUri = e.remoteUri, startedAtMillis = now(),
        )

        is Event.OutgoingSent -> c?.takeIf { it.phase == CallPhase.DIALING }?.copy(phase = CallPhase.CONNECTING)

        is Event.RemoteRinging -> c?.takeIf { it.direction == CallDirection.OUTGOING && it.phase in setOf(CallPhase.DIALING, CallPhase.CONNECTING) }
            ?.copy(phase = CallPhase.RINGING_REMOTE)

        is Event.IncomingPush -> if (c != null) null else CallSnapshot(
            callId = e.callId, accountId = e.accountId, direction = CallDirection.INCOMING,
            phase = CallPhase.INCOMING_PENDING, remoteNumber = e.number, remoteDisplayName = e.displayName,
            startedAtMillis = now(), pushCallId = e.pushCallId,
        )

        is Event.IncomingInvite -> when {
            c == null -> CallSnapshot(
                callId = e.callId, accountId = e.accountId, direction = CallDirection.INCOMING,
                phase = CallPhase.RINGING_LOCAL, remoteNumber = e.number, remoteDisplayName = e.displayName,
                remoteUri = e.remoteUri, startedAtMillis = now(), pushCallId = e.pushCallId,
            )
            // Push arrived first: upgrade the pending call. If the user already tapped
            // "answer" while pending, keep ANSWERING – the manager answers immediately.
            c.phase == CallPhase.INCOMING_PENDING -> c.copy(
                phase = CallPhase.RINGING_LOCAL, remoteUri = e.remoteUri,
                remoteDisplayName = e.displayName ?: c.remoteDisplayName,
                remoteNumber = e.number.ifBlank { c.remoteNumber },
            )
            c.phase == CallPhase.ANSWERING -> c.copy(remoteUri = e.remoteUri)
            else -> null
        }

        is Event.AnswerRequested -> c?.takeIf { it.phase == CallPhase.RINGING_LOCAL || it.phase == CallPhase.INCOMING_PENDING }
            ?.copy(phase = CallPhase.ANSWERING)

        is Event.Connected -> c?.takeIf { !it.phase.isTerminal && it.phase != CallPhase.ACTIVE }?.copy(
            phase = if (c.onHold) CallPhase.HOLDING else CallPhase.ACTIVE,
            connectedAtMillis = c.connectedAtMillis ?: now(),
        )

        is Event.HoldChanged -> c?.takeIf { it.phase.isConnected }?.copy(
            onHold = e.localHold,
            phase = when {
                e.localHold -> CallPhase.HOLDING
                e.remoteHold -> CallPhase.REMOTE_HELD
                c.phase == CallPhase.TRANSFERRING -> CallPhase.TRANSFERRING
                else -> CallPhase.ACTIVE
            },
        )

        is Event.MuteChanged -> c?.takeIf { !it.phase.isTerminal }?.copy(muted = e.muted)
        is Event.AudioChanged -> c?.takeIf { !it.phase.isTerminal }?.copy(audioRoute = e.route, availableRoutes = e.available)
        is Event.QualityUpdated -> c?.takeIf { it.phase.isConnected }?.copy(quality = e.quality)
        is Event.CapabilitiesChanged -> c?.takeIf { !it.phase.isTerminal }?.copy(capabilities = e.capabilities)
        is Event.TransferStarted -> c?.takeIf { it.phase.isConnected }?.copy(phase = CallPhase.TRANSFERRING)
        is Event.TransferFailed -> c?.takeIf { it.phase == CallPhase.TRANSFERRING }?.copy(phase = if (c.onHold) CallPhase.HOLDING else CallPhase.ACTIVE)
        is Event.ConferenceChanged -> c?.takeIf { it.phase.isConnected }?.copy(conferenceIds = e.members)

        is Event.Ended -> c?.takeIf { !it.phase.isTerminal }?.copy(
            phase = CallPhase.ENDED, endedAtMillis = now(), endReason = e.reason, sipStatus = e.sipStatus,
        )

        is Event.Removed -> null
    }
}

package com.ivrtop.dialer.telephony.telecom

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import com.ivrtop.dialer.core.common.ApplicationScope
import com.ivrtop.dialer.core.common.Clock
import com.ivrtop.dialer.core.common.phone.EmergencyNumbers
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.data.CallHistoryRepository
import com.ivrtop.dialer.core.data.ContactsRepository
import com.ivrtop.dialer.core.data.IncomingCallFilter
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.AudioEndpoint
import com.ivrtop.dialer.core.models.AudioRoute
import com.ivrtop.dialer.core.models.CallCapabilities
import com.ivrtop.dialer.core.models.CallDirection
import com.ivrtop.dialer.core.models.CallEndReason
import com.ivrtop.dialer.core.models.CallPhase
import com.ivrtop.dialer.core.models.CallSnapshot
import com.ivrtop.dialer.core.models.IncomingDecision
import com.ivrtop.dialer.core.models.PushMessage
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.models.SipTransport
import com.ivrtop.dialer.telephony.audio.AudioRouteState
import com.ivrtop.dialer.telephony.audio.RingtonePlayer
import com.ivrtop.dialer.telephony.audio.TonePlayer
import com.ivrtop.dialer.telephony.sip.CallManager
import com.ivrtop.dialer.telephony.sip.CallStateMachine
import com.ivrtop.dialer.telephony.sip.CallStateMachine.Event
import com.ivrtop.dialer.telephony.sip.EngineCallHandle
import com.ivrtop.dialer.telephony.sip.EngineCallState
import com.ivrtop.dialer.telephony.sip.SipEngine
import com.ivrtop.dialer.telephony.sip.SipEvent
import com.ivrtop.dialer.telephony.sip.SipStatusMapper
import com.ivrtop.dialer.telephony.telecom.service.CallService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates every call:
 *   SipEngine events ─┐
 *   UI / notification ─┼─► serialized actor ─► CallStateMachine (single source of truth)
 *   Telecom callbacks ─┘                     └─► Telecom, audio, history, push ack, CallService
 *
 * All mutations run on [actor] (a single-threaded dispatcher) – no locks, no races between
 * "remote hangup" and "user pressed answer".
 */
@Singleton
class TelecomCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineStarter: EngineStarter,
    private val engine: SipEngine,
    private val registration: DefaultRegistrationManager,
    private val accounts: AccountRepository,
    private val history: CallHistoryRepository,
    private val contacts: ContactsRepository,
    private val filter: IncomingCallFilter,
    private val settings: SettingsStore,
    private val emergency: EmergencyNumbers,
    private val ringtone: RingtonePlayer,
    private val tones: TonePlayer,
    private val audioState: AudioRouteState,
    private val notifier: com.ivrtop.dialer.telephony.telecom.service.CallNotifier,
    private val clock: Clock,
    @ApplicationScope appScope: CoroutineScope,
) : CallManager {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val actor = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + actor)

    private val machine = CallStateMachine(now = clock::now, onIgnored = { Timber.d("CSM: $it") })
    private val callsManager = CallsManager(context)

    private val handleByCall = ConcurrentHashMap<String, EngineCallHandle>()
    private val callByHandle = ConcurrentHashMap<Int, String>()
    private val telecomActions = ConcurrentHashMap<String, Channel<TelecomAction>>()
    private val pendingPushTimeouts = ConcurrentHashMap<String, Job>()
    private val postDial = ConcurrentHashMap<String, String>()
    private val localHangup = ConcurrentHashMap.newKeySet<String>()
    private val silenced = ConcurrentHashMap.newKeySet<String>()
    private val telecomEndpoints = ConcurrentHashMap<String, List<CallEndpointCompat>>()
    /** CallState events that raced ahead of makeCall() returning the handle. */
    private val orphanStates = ConcurrentHashMap<Int, SipEvent.CallState>()

    override val calls: StateFlow<List<CallSnapshot>> = machine.calls
        .map { m -> m.values.sortedBy { it.startedAtMillis } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    override val foregroundCall: StateFlow<CallSnapshot?> = machine.calls.map { m ->
        val live = m.values.filter { !it.phase.isTerminal }
        live.firstOrNull { it.phase.isRinging } ?: live.firstOrNull { it.phase == CallPhase.ACTIVE }
            ?: live.firstOrNull() ?: m.values.maxByOrNull { it.endedAtMillis ?: 0 }
    }.stateIn(appScope, SharingStarted.Eagerly, null)

    private sealed interface TelecomAction {
        data object Answer : TelecomAction
        data class Disconnect(val cause: DisconnectCause) : TelecomAction
        data object SetActive : TelecomAction
        data object SetInactive : TelecomAction
        data class Endpoint(val endpoint: CallEndpointCompat) : TelecomAction
    }

    init {
        try {
            callsManager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
        } catch (e: Exception) {
            Timber.e(e, "Telecom registration failed – calls will run without system integration")
        }
        scope.launch { engine.events.collect { onEngineEvent(it) } }
        scope.launch { qualityLoop() }
        scope.launch {
            calls.collect { list ->
                registration.setActiveCallCount(list.count { !it.phase.isTerminal })
            }
        }
    }

    // ================================================================= outgoing

    override suspend fun placeCall(number: String, accountId: String?, displayName: String?): String = withContext(actor) {
        val dial = PhoneNumbers.toDialString(number)
        if (dial.target.isBlank()) throw AppException(AppErrorCode.INVALID_NUMBER)
        if (!PhoneNumbers.isSipUri(dial.target) && emergency.isEmergency(dial.target) && settings.current().routeEmergencyViaSim) {
            throw AppException(AppErrorCode.EMERGENCY_NUMBER)
        }
        val account = (accountId?.let { accounts.get(it) } ?: accounts.defaultAccount())
            ?.takeIf { it.enabled && it.allowOutgoing }
            ?: throw AppException(AppErrorCode.NO_ACCOUNT)

        engineStarter.ensureStarted()
        if (!engine.hasAccount(account.id)) registration.reloadAccounts()

        val callId = UUID.randomUUID().toString()
        val uri = buildTargetUri(dial.target, account)
        val name = displayName ?: contacts.lookup(dial.target)?.displayName
        machine.dispatch(Event.OutgoingRequested(callId, account.id, dial.target, name, uri))
        dial.postDial?.let { postDial[callId] = it }

        // Put other calls on hold (single active audio path).
        holdOthers(callId)
        CallService.start(context)
        addToTelecom(callId, CallAttributesCompat.DIRECTION_OUTGOING, name ?: dial.target, dial.target)

        try {
            val handle = engine.makeCall(account.id, uri)
            bind(callId, handle)
            machine.dispatch(Event.OutgoingSent(callId))
        } catch (e: Exception) {
            Timber.e(e, "makeCall failed")
            endCall(callId, CallEndReason.FAILED, null)
            throw AppException(AppErrorCode.SERVER_UNREACHABLE, cause = e)
        }
        callId
    }

    private fun buildTargetUri(target: String, account: SipAccount): String {
        if (PhoneNumbers.isSipUri(target)) {
            return if (target.startsWith("sip:", true) || target.startsWith("sips:", true)) target else "sip:$target"
        }
        val tParam = when (account.transport) {
            SipTransport.TLS -> ";transport=tls"
            SipTransport.TCP -> ";transport=tcp"
            else -> ""
        }
        val user = Uri.encode(target, "+*#")
        return "sip:$user@${account.domain}$tParam"
    }

    // ================================================================= incoming (push path)

    override suspend fun onIncomingPush(message: PushMessage, accountId: String) = withContext(actor) {
        val pushId = message.callId ?: return@withContext
        if (machine.findByPushId(pushId) != null) return@withContext
        val account = accounts.get(accountId) ?: return@withContext
        val number = message.from.orEmpty()
        val decision = filter.decide(account, number)
        if (decision !is IncomingDecision.Ring && decision !is IncomingDecision.RingSilently) {
            // The INVITE (if it arrives) will be rejected by the same filter; don't alert now.
            registration.wakeForIncomingCall(accountId, WAKE_HOLD_MS)
            return@withContext
        }
        val callId = UUID.randomUUID().toString()
        val name = contacts.lookup(number)?.displayName ?: message.fromName
        machine.dispatch(Event.IncomingPush(callId, accountId, number, name, pushId))
        if (decision is IncomingDecision.RingSilently) silenced += callId
        alertIncoming(callId, account)
        registration.wakeForIncomingCall(accountId, WAKE_HOLD_MS)

        // If the INVITE never arrives, convert to a missed call.
        val remainingMs = (message.sentAt + message.ttlSec * 1000L - clock.now()).coerceIn(5_000, 45_000)
        pendingPushTimeouts[callId] = scope.launch {
            delay(remainingMs)
            val c = machine[callId]
            if (c != null && handleByCall[callId] == null && !c.phase.isTerminal) {
                Timber.w("INVITE for push $pushId never arrived")
                endCall(callId, CallEndReason.MISSED, null)
            }
        }
    }

    override suspend fun onPushCancelled(message: PushMessage) = withContext(actor) {
        val pushId = message.callId ?: return@withContext
        val c = machine.findByPushId(pushId) ?: return@withContext
        // This device answered (or is answering): the "answered elsewhere" fan-out is not for us.
        if (c.phase.isConnected || c.phase == CallPhase.ANSWERING || c.phase.isTerminal) return@withContext
        val reason = if (message.reason == "answered_elsewhere") CallEndReason.ANSWERED_ELSEWHERE else CallEndReason.MISSED
        handleByCall[c.callId]?.let { runCatching { engine.hangup(it, 487) } }
        endCall(c.callId, reason, null)
    }

    // ================================================================= engine events

    private suspend fun onEngineEvent(ev: SipEvent) {
        when (ev) {
            is SipEvent.IncomingCall -> onIncomingInvite(ev)
            is SipEvent.CallState -> onCallState(ev)
            is SipEvent.CallMedia -> {
                val id = callByHandle[ev.handle.value] ?: return
                if (ev.active) tones.stopRingback()
                machine.dispatch(Event.HoldChanged(id, ev.localHold, ev.remoteHold))
            }
            is SipEvent.TransferProgress -> {
                val id = callByHandle[ev.handle.value] ?: return
                if (ev.final) {
                    if (ev.statusCode in 200..299) {
                        handleByCall[id]?.let { runCatching { engine.hangup(it) } }
                        localHangup += id
                    } else {
                        machine.dispatch(Event.TransferFailed(id))
                    }
                }
            }
            else -> Unit
        }
    }

    private suspend fun onIncomingInvite(ev: SipEvent.IncomingCall) {
        val account = accounts.get(ev.accountId)
        if (account == null) {
            engine.hangup(ev.handle, 480); return
        }
        val pushId = ev.headers[HEADER_PUSH_ID]
        val existing = pushId?.let { machine.findByPushId(it) }
        val decision = filter.decide(account, ev.remoteNumber)

        when (decision) {
            is IncomingDecision.Reject -> {
                engine.hangup(ev.handle, decision.sipStatus)
                val id = existing?.callId ?: UUID.randomUUID().toString()
                if (existing == null) {
                    machine.dispatch(Event.IncomingInvite(id, account.id, ev.remoteNumber, ev.remoteName, ev.remoteUri, pushId))
                }
                endCall(id, decision.reason, decision.sipStatus, blocked = decision.reason == CallEndReason.BLOCKED)
                return
            }
            is IncomingDecision.Forward -> {
                engine.hangup(ev.handle, 302, contact = buildTargetUri(decision.target, account))
                return
            }
            else -> Unit
        }

        // Busy: already in a call and call-waiting disabled → 486
        val busy = machine.active().any { it.phase.isConnected || it.phase == CallPhase.DIALING || it.phase == CallPhase.CONNECTING }
        if (busy && !settings.current().callWaiting) {
            engine.hangup(ev.handle, 486)
            return
        }

        val callId = existing?.callId ?: UUID.randomUUID().toString()
        bind(callId, ev.handle)
        pendingPushTimeouts.remove(callId)?.cancel()
        val name = contacts.lookup(ev.remoteNumber)?.displayName ?: ev.remoteName
        machine.dispatch(Event.IncomingInvite(callId, account.id, ev.remoteNumber, name, ev.remoteUri, pushId))
        if (decision is IncomingDecision.RingSilently) silenced += callId
        runCatching { engine.ringing(ev.handle) }

        if (existing == null) {
            alertIncoming(callId, account)
        } else if (machine[callId]?.phase == CallPhase.ANSWERING) {
            // User pressed answer before the INVITE reached us.
            doAnswer(callId)
        }
    }

    private suspend fun alertIncoming(callId: String, account: SipAccount) {
        val c = machine[callId] ?: return
        addToTelecom(callId, CallAttributesCompat.DIRECTION_INCOMING, c.remoteDisplayName ?: c.remoteNumber, c.remoteNumber)
        if (!CallService.start(context)) notifier.postCallDirect(c, account.label)
        val s = settings.current()
        val othersActive = machine.active().any { it.callId != callId && it.phase.isConnected }
        when {
            callId in silenced -> Unit
            othersActive -> ringtone.startCallWaiting()
            else -> ringtone.start(account.ringtoneUri, s.vibrateOnRing, silent = false)
        }
    }

    private suspend fun onCallState(ev: SipEvent.CallState) {
        val callId = callByHandle[ev.handle.value] ?: run {
            orphanStates[ev.handle.value] = ev
            return
        }
        val c = machine[callId] ?: return
        when (ev.state) {
            EngineCallState.EARLY -> if (c.direction == CallDirection.OUTGOING) {
                machine.dispatch(Event.RemoteRinging(callId, earlyMedia = ev.lastStatus == 183))
                if (ev.lastStatus == 180) tones.startRingback() else tones.stopRingback()
            }
            EngineCallState.CONFIRMED -> {
                tones.stopRingback()
                if (c.direction == CallDirection.INCOMING) ringtone.stop()
                machine.dispatch(Event.Connected(callId))
                machine.dispatch(Event.CapabilitiesChanged(callId, capabilitiesFor(callId)))
                telecomActions[callId]?.trySend(TelecomAction.SetActive)
                postDial.remove(callId)?.let { pd ->
                    scope.launch {
                        pd.forEach { ch ->
                            if (ch == ',') delay(2_000) else if (ch != ';') sendDtmf(callId, ch.toString())
                        }
                    }
                }
            }
            EngineCallState.DISCONNECTED -> {
                val wasConnected = c.connectedAtMillis != null
                val local = callId in localHangup
                val reason = when {
                    c.phase == CallPhase.TRANSFERRING -> CallEndReason.TRANSFERRED
                    c.direction == CallDirection.INCOMING && !wasConnected && !local ->
                        if (ev.reason.contains("elsewhere", true)) CallEndReason.ANSWERED_ELSEWHERE else CallEndReason.MISSED
                    c.direction == CallDirection.INCOMING && !wasConnected && local -> CallEndReason.DECLINED
                    else -> SipStatusMapper.endReason(ev.lastStatus, local, wasConnected)
                }
                endCall(callId, reason, ev.lastStatus)
            }
            else -> Unit
        }
    }

    // ================================================================= user actions

    override suspend fun answer(callId: String) = withContext(actor) {
        val c = machine[callId] ?: return@withContext
        if (!c.phase.isRinging) return@withContext
        machine.dispatch(Event.AnswerRequested(callId))
        ringtone.stop()
        telecomActions[callId]?.trySend(TelecomAction.Answer)
        if (handleByCall[callId] != null) doAnswer(callId)
        // else: answered while INVITE still in flight → onIncomingInvite answers it.
    }

    private suspend fun doAnswer(callId: String) {
        val handle = handleByCall[callId] ?: return
        holdOthers(callId)
        try {
            engine.answer(handle)
        } catch (e: Exception) {
            Timber.e(e, "answer failed")
            endCall(callId, CallEndReason.FAILED, null)
        }
    }

    override suspend fun decline(callId: String) = withContext(actor) {
        val c = machine[callId] ?: return@withContext
        ringtone.stop()
        localHangup += callId
        val h = handleByCall[callId]
        if (h != null) {
            // 486 Busy Here → Asterisk DIALSTATUS=BUSY → voicemail "busy" greeting.
            runCatching { engine.hangup(h, 486, "Declined") }
        } else {
            endCall(callId, CallEndReason.DECLINED, null)
        }
    }

    override suspend fun silenceRinging(callId: String) = withContext(actor) {
        silenced += callId
        ringtone.stop()
    }

    override suspend fun hangup(callId: String) = withContext(actor) {
        val c = machine[callId] ?: return@withContext
        if (c.phase.isRinging) return@withContext decline(callId)
        localHangup += callId
        tones.stopRingback()
        val h = handleByCall[callId]
        if (h != null) runCatching { engine.hangup(h) } else endCall(callId, CallEndReason.CANCELLED, null)
    }

    override suspend fun setMuted(callId: String, muted: Boolean) = withContext(actor) {
        val h = handleByCall[callId] ?: return@withContext
        engine.setMicMuted(h, muted)
        machine.dispatch(Event.MuteChanged(callId, muted))
    }

    override suspend fun setHold(callId: String, hold: Boolean) = withContext(actor) {
        val h = handleByCall[callId] ?: return@withContext
        if (!hold) holdOthers(callId)
        runCatching { engine.setHold(h, hold) }.onFailure { Timber.w(it, "hold failed") }
        telecomActions[callId]?.trySend(if (hold) TelecomAction.SetInactive else TelecomAction.SetActive)
    }

    override suspend fun sendDtmf(callId: String, digits: String) = withContext(actor) {
        val h = handleByCall[callId] ?: return@withContext
        val c = machine[callId] ?: return@withContext
        val method = accounts.get(c.accountId)?.dtmfMethod ?: com.ivrtop.dialer.core.models.DtmfMethod.RFC4733
        runCatching { engine.sendDtmf(h, digits, method) }.onFailure { Timber.w("dtmf failed: ${it.message}") }
    }

    override suspend fun setAudioRoute(callId: String, endpoint: AudioEndpoint) = withContext(actor) {
        val target = telecomEndpoints[callId]?.firstOrNull { it.identifier.toString() == endpoint.id } ?: return@withContext
        telecomActions[callId]?.trySend(TelecomAction.Endpoint(target))
    }

    override suspend fun blindTransfer(callId: String, target: String) = withContext(actor) {
        val h = handleByCall[callId] ?: return@withContext
        val c = machine[callId] ?: return@withContext
        val acc = accounts.get(c.accountId) ?: return@withContext
        machine.dispatch(Event.TransferStarted(callId))
        try {
            engine.transferBlind(h, buildTargetUri(PhoneNumbers.toDialString(target).target, acc))
        } catch (e: Exception) {
            machine.dispatch(Event.TransferFailed(callId))
            throw AppException(AppErrorCode.TRANSFER_FAILED, cause = e)
        }
    }

    override suspend fun attendedTransfer(callId: String, consultCallId: String) = withContext(actor) {
        val h = handleByCall[callId] ?: return@withContext
        val consult = handleByCall[consultCallId] ?: return@withContext
        machine.dispatch(Event.TransferStarted(callId))
        try {
            engine.transferAttended(h, consult)
        } catch (e: Exception) {
            machine.dispatch(Event.TransferFailed(callId))
            throw AppException(AppErrorCode.TRANSFER_FAILED, cause = e)
        }
    }

    override suspend fun swap(callId: String) = withContext(actor) {
        machine.active().filter { it.callId != callId && it.phase == CallPhase.ACTIVE }.forEach { setHold(it.callId, true) }
        setHold(callId, false)
    }

    override suspend fun merge(callIds: List<String>) = withContext(actor) {
        val handles = callIds.mapNotNull { handleByCall[it] }
        if (handles.size < 2) return@withContext
        callIds.forEach { id ->
            handleByCall[id]?.let { h -> if (machine[id]?.onHold == true) runCatching { engine.setHold(h, false) } }
        }
        delay(300) // let the re-INVITEs settle before bridging media
        engine.conference(handles, true)
        callIds.forEach { machine.dispatch(Event.ConferenceChanged(it, callIds)) }
    }

    // ================================================================= helpers

    private fun bind(callId: String, handle: EngineCallHandle) {
        handleByCall[callId] = handle
        callByHandle[handle.value] = callId
        orphanStates.remove(handle.value)?.let { ev -> scope.launch { onCallState(ev) } }
    }

    private suspend fun holdOthers(exceptCallId: String) {
        machine.active().filter { it.callId != exceptCallId && it.phase == CallPhase.ACTIVE && it.conferenceIds.isEmpty() }.forEach {
            handleByCall[it.callId]?.let { h -> runCatching { engine.setHold(h, true) } }
            telecomActions[it.callId]?.trySend(TelecomAction.SetInactive)
        }
    }

    private fun capabilitiesFor(callId: String): CallCapabilities {
        val caps = engine.capabilities
        val others = machine.active().count { it.callId != callId && it.phase.isConnected }
        return CallCapabilities(
            canHold = true, canMute = true, canDtmf = true,
            canBlindTransfer = true, canAttendedTransfer = caps.attendedTransfer && others > 0,
            canAddCall = machine.active().size < 4,
            canMerge = caps.conference && others > 0, canSwap = others > 0,
        )
    }

    private suspend fun endCall(callId: String, reason: CallEndReason, sipStatus: Int?, blocked: Boolean = false) {
        val before = machine[callId] ?: return
        if (before.phase.isTerminal) return
        ringtone.stop()
        tones.stopRingback()
        pendingPushTimeouts.remove(callId)?.cancel()
        val ended = machine.dispatch(Event.Ended(callId, reason, sipStatus)) ?: return
        telecomActions[callId]?.trySend(TelecomAction.Disconnect(disconnectCauseFor(reason)))
        runCatching { history.record(ended, blocked) }.onFailure { Timber.e(it, "history write failed") }
        if (ended.connectedAtMillis != null) tones.playEndTone()
        if (reason == CallEndReason.MISSED) {
            notifier.postMissed(ended, history.unreadMissed.first().coerceAtLeast(1))
        }

        // Refresh capabilities of remaining calls (e.g. merge no longer possible).
        machine.active().forEach { machine.dispatch(Event.CapabilitiesChanged(it.callId, capabilitiesFor(it.callId))) }

        scope.launch {
            delay(ENDED_LINGER_MS)
            machine.dispatch(Event.Removed(callId))
            handleByCall.remove(callId)?.let { callByHandle.remove(it.value); orphanStates.remove(it.value) }
            telecomActions.remove(callId)?.close()
            telecomEndpoints.remove(callId)
            localHangup.remove(callId)
            silenced.remove(callId)
            if (machine.active().isEmpty()) {
                audioState.reset()
                notifier.cancelCallNotification() // in case it was posted directly (no FGS)
            }
        }
    }

    private fun disconnectCauseFor(reason: CallEndReason) = DisconnectCause(
        when (reason) {
            CallEndReason.LOCAL_HANGUP -> DisconnectCause.LOCAL
            CallEndReason.REMOTE_HANGUP, CallEndReason.TRANSFERRED -> DisconnectCause.REMOTE
            CallEndReason.DECLINED, CallEndReason.REJECTED_DND, CallEndReason.BLOCKED -> DisconnectCause.REJECTED
            CallEndReason.MISSED, CallEndReason.NO_ANSWER -> DisconnectCause.MISSED
            CallEndReason.BUSY -> DisconnectCause.BUSY
            CallEndReason.CANCELLED -> DisconnectCause.CANCELED
            CallEndReason.ANSWERED_ELSEWHERE -> DisconnectCause.ANSWERED_ELSEWHERE
            else -> DisconnectCause.ERROR
        },
    )

    /**
     * Registers the call with Android Telecom. Telecom then owns audio routing (BT/wired/car),
     * coordinates with cellular calls, shows the call on wearables/Auto and lets the app keep a
     * phoneCall foreground service with microphone access while in background.
     */
    private fun addToTelecom(callId: String, direction: Int, displayName: String, number: String) {
        val channel = Channel<TelecomAction>(Channel.UNLIMITED)
        telecomActions[callId] = channel
        val address = if (PhoneNumbers.isSipUri(number)) Uri.parse("sip:$number") else Uri.fromParts("tel", number, null)
        val attrs = CallAttributesCompat(
            displayName = displayName,
            address = address,
            direction = direction,
            callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
        )
        scope.launch {
            try {
                callsManager.addCall(
                    attrs,
                    onAnswer = { _ -> scope.launch { answer(callId) } },
                    onDisconnect = { _ -> scope.launch { hangup(callId) } },
                    onSetActive = { scope.launch { if (machine[callId]?.onHold == true) setHold(callId, false) } },
                    onSetInactive = { scope.launch { if (machine[callId]?.onHold == false) setHold(callId, true) } },
                ) {
                    launch { processTelecomActions(this@addCall, callId, channel) }
                    launch {
                        availableEndpoints.collect { eps ->
                            telecomEndpoints[callId] = eps
                            publishAudio(callId, null, eps)
                        }
                    }
                    launch { currentCallEndpoint.collect { ep -> publishAudio(callId, ep, telecomEndpoints[callId].orEmpty()) } }
                    launch {
                        isMuted.collect { muted ->
                            // Mute toggled by the system (car kit, wearable).
                            if (machine[callId]?.muted != muted) setMuted(callId, muted)
                        }
                    }
                }
            } catch (e: Exception) {
                // e.g. Telecom refused because an emergency call is in progress.
                Timber.e(e, "Telecom addCall failed for $callId")
                if (direction == CallAttributesCompat.DIRECTION_INCOMING) {
                    handleByCall[callId]?.let { runCatching { engine.hangup(it, 486) } }
                }
            }
        }
    }

    private suspend fun processTelecomActions(scopeCtl: CallControlScope, callId: String, ch: Channel<TelecomAction>) {
        for (action in ch) {
            try {
                when (action) {
                    TelecomAction.Answer -> scopeCtl.answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
                    TelecomAction.SetActive -> scopeCtl.setActive()
                    TelecomAction.SetInactive -> scopeCtl.setInactive()
                    is TelecomAction.Endpoint -> scopeCtl.requestEndpointChange(action.endpoint)
                    is TelecomAction.Disconnect -> {
                        scopeCtl.disconnect(action.cause)
                        break
                    }
                }
            } catch (e: Exception) {
                Timber.w("telecom action $action failed: ${e.message}")
            }
        }
    }

    private fun publishAudio(callId: String, current: CallEndpointCompat?, eps: List<CallEndpointCompat>) {
        val mapped = eps.map { AudioEndpoint(it.identifier.toString(), it.name.toString(), routeOf(it.type)) }
        val route = current?.let { routeOf(it.type) } ?: machine[callId]?.audioRoute ?: AudioRoute.EARPIECE
        machine.dispatch(Event.AudioChanged(callId, route, mapped))
        audioState.update(route, mapped, callActive = machine[callId]?.phase?.isConnected == true)
    }

    private fun routeOf(type: Int) = when (type) {
        CallEndpointCompat.TYPE_SPEAKER -> AudioRoute.SPEAKER
        CallEndpointCompat.TYPE_BLUETOOTH -> AudioRoute.BLUETOOTH
        CallEndpointCompat.TYPE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        CallEndpointCompat.TYPE_STREAMING -> AudioRoute.STREAMING
        else -> AudioRoute.EARPIECE
    }

    private suspend fun qualityLoop() {
        while (scope.isActive) {
            delay(3_000)
            machine.active().filter { it.phase.isConnected }.forEach { c ->
                val h = handleByCall[c.callId] ?: return@forEach
                engine.quality(h)?.let { machine.dispatch(Event.QualityUpdated(c.callId, it)) }
            }
        }
    }

    companion object {
        const val HEADER_PUSH_ID = "x-ivrtop-call-id"
        const val WAKE_HOLD_MS = 60_000L
        const val ENDED_LINGER_MS = 2_000L
    }
}

package com.ivrtop.dialer.telephony.sip

import com.ivrtop.dialer.core.models.AudioEndpoint
import com.ivrtop.dialer.core.models.AudioRoute
import com.ivrtop.dialer.core.models.CallSnapshot
import com.ivrtop.dialer.core.models.MessageWaiting
import com.ivrtop.dialer.core.models.PresenceState
import com.ivrtop.dialer.core.models.PushMessage
import com.ivrtop.dialer.core.models.RegistrationState
import com.ivrtop.dialer.core.models.VoicemailMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Single source of truth for calls exposed to UI and notifications. */
interface CallManager {
    val calls: StateFlow<List<CallSnapshot>>
    val foregroundCall: StateFlow<CallSnapshot?>

    /** Returns call id or throws AppException (EMERGENCY_NUMBER, NO_ACCOUNT, BLOCKED_NUMBER…). */
    suspend fun placeCall(number: String, accountId: String? = null, displayName: String? = null): String
    suspend fun answer(callId: String)
    suspend fun decline(callId: String)
    suspend fun silenceRinging(callId: String)
    suspend fun hangup(callId: String)
    suspend fun setMuted(callId: String, muted: Boolean)
    suspend fun setHold(callId: String, hold: Boolean)
    suspend fun sendDtmf(callId: String, digits: String)
    suspend fun setAudioRoute(callId: String, endpoint: AudioEndpoint)
    suspend fun blindTransfer(callId: String, target: String)
    /** Attended: [consultCallId] must be an active call to the transfer target. */
    suspend fun attendedTransfer(callId: String, consultCallId: String)
    suspend fun swap(callId: String)
    suspend fun merge(callIds: List<String>)

    /** Push path – unused while there is no push server (docs/PUSH_NOTIFICATIONS.md); kept for a future server. */
    suspend fun onIncomingPush(message: PushMessage, accountId: String)
    suspend fun onPushCancelled(message: PushMessage)
}

/** Keeps every enabled account registered according to the connectivity mode. */
interface RegistrationManager {
    val states: StateFlow<Map<String, RegistrationState>>
    suspend fun start()
    suspend fun reloadAccounts()
    suspend fun reconnectNow(accountId: String? = null)
    /** Called from push wake-up: register quickly and keep registered at least [holdMs]. */
    suspend fun wakeForIncomingCall(accountId: String, holdMs: Long)
    suspend fun onAppForeground(foreground: Boolean)
}

interface AudioRouteManager {
    val currentRoute: StateFlow<AudioRoute>
    val availableEndpoints: StateFlow<List<AudioEndpoint>>
}

interface VoicemailProvider {
    val messages: Flow<List<VoicemailMessage>>
    val waiting: StateFlow<Map<String, MessageWaiting>>
    suspend fun refresh(accountId: String)
    suspend fun markRead(id: String)
    suspend fun delete(id: String)
    /** Number to dial to listen via the PBX IVR (e.g. *97). */
    suspend fun accessNumber(accountId: String): String?
}

/** PBX-specific (IVRTOP / Asterisk) features exposed when available. */
interface PbxIntegrationProvider {
    fun supports(accountId: String, feature: PbxFeature): Boolean
    val presence: StateFlow<Map<String, PresenceState>>
    suspend fun watchPresence(accountId: String, extension: String)
    suspend fun unwatchPresence(accountId: String, extension: String)
    fun featureCode(accountId: String, feature: PbxFeature): String?
}

enum class PbxFeature { VOICEMAIL, CALL_PICKUP, CALL_PARK, CALL_FORWARD, DND_SYNC, BLF, RECORDINGS, BLOCKLIST_SYNC, DIRECTORY }

/** Auto-detection / verification of SIP connection settings (implemented by ConnectionProber). */
interface ConnectionTester {
    suspend fun detect(
        base: com.ivrtop.dialer.core.models.SipAccount,
        password: String,
        allowInsecure: Boolean,
        fixedTransport: com.ivrtop.dialer.core.models.SipTransport? = null,
        onStep: (com.ivrtop.dialer.core.models.ProbeStep) -> Unit,
    ): com.ivrtop.dialer.core.models.ProbeResult
}

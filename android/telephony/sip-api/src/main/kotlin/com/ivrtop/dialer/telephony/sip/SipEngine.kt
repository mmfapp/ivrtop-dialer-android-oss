package com.ivrtop.dialer.telephony.sip

import com.ivrtop.dialer.core.models.CallQuality
import com.ivrtop.dialer.core.models.DtmfMethod
import com.ivrtop.dialer.core.models.PresenceState
import com.ivrtop.dialer.core.models.SipAccount
import kotlinx.coroutines.flow.SharedFlow

/**
 * Boundary to the native SIP stack. The ONLY implementation today is PjsipEngine
 * (telephony:sip-engine). Nothing outside telephony may reference the implementation,
 * so the engine can be swapped (e.g. Linphone SDK, or a commercial PJSIP licence build)
 * without touching UI or domain code. See docs/SIP_ENGINE_RESEARCH.md.
 *
 * Threading: all methods are main-safe; implementations serialize work on their own thread.
 * [EngineCallHandle] values are opaque and only valid inside the same engine instance.
 */
interface SipEngine {
    val events: SharedFlow<SipEvent>
    val capabilities: EngineCapabilities
    val isStarted: Boolean

    suspend fun start(config: EngineConfig)
    suspend fun stop()

    /** Creates or reconfigures an account. Registration starts only if [register] is true. */
    suspend fun upsertAccount(account: SipAccount, secrets: AccountSecrets, register: Boolean)
    suspend fun removeAccount(accountId: String)
    suspend fun setRegistration(accountId: String, register: Boolean)
    suspend fun hasAccount(accountId: String): Boolean

    /**
     * One-shot REGISTER used by the auto-configuration wizard. Creates a temporary account,
     * waits for the final response and unregisters. Never retries by itself (Fail2Ban safety).
     */
    suspend fun probeRegistration(account: SipAccount, secrets: AccountSecrets, timeoutMs: Long): ProbeOutcome

    suspend fun makeCall(accountId: String, targetUri: String, headers: Map<String, String> = emptyMap()): EngineCallHandle
    suspend fun ringing(handle: EngineCallHandle)
    suspend fun answer(handle: EngineCallHandle)
    /** Ends a call. For unanswered incoming calls [sipStatus] selects 486/603/302 etc. */
    suspend fun hangup(handle: EngineCallHandle, sipStatus: Int = 0, reason: String? = null, contact: String? = null)
    suspend fun setHold(handle: EngineCallHandle, hold: Boolean)
    suspend fun setMicMuted(handle: EngineCallHandle, muted: Boolean)
    suspend fun sendDtmf(handle: EngineCallHandle, digits: String, method: DtmfMethod)
    suspend fun transferBlind(handle: EngineCallHandle, targetUri: String)
    suspend fun transferAttended(handle: EngineCallHandle, consultHandle: EngineCallHandle)
    /** Local conference (audio bridge mixing). */
    suspend fun conference(handles: List<EngineCallHandle>, enabled: Boolean)
    suspend fun quality(handle: EngineCallHandle): CallQuality?

    /** Network changed (Wi-Fi ⇄ cellular, new IP). Re-registers and re-INVITEs active calls. */
    suspend fun handleIpChange()

    suspend fun subscribePresence(accountId: String, uri: String)
    suspend fun unsubscribePresence(accountId: String, uri: String)

    /** Audio device lifecycle, coordinated with Telecom (open only while a call needs audio). */
    suspend fun setAudioActive(active: Boolean)
}

@JvmInline
value class EngineCallHandle(val value: Int)

data class AccountSecrets(val password: String, val turnPassword: String? = null)

data class EngineConfig(
    val userAgent: String,
    val caBundlePath: String?,
    val logLevel: Int,
    val logSink: (String) -> Unit,
    val echoCancellation: Boolean,
    val noiseSuppression: Boolean,
    val autoGainControl: Boolean,
    val stunServers: List<String> = emptyList(),
    val maxCalls: Int = 4,
)

data class EngineCapabilities(
    val transports: Set<String>,
    val codecs: List<String>,
    val srtp: Boolean,
    val tls: Boolean,
    val ice: Boolean,
    val presence: Boolean,
    val mwi: Boolean,
    val conference: Boolean,
    val attendedTransfer: Boolean,
    val video: Boolean = false,
)

sealed interface ProbeOutcome {
    data class Registered(val code: Int, val expires: Int, val elapsedMs: Long) : ProbeOutcome
    data class Rejected(val code: Int, val reason: String) : ProbeOutcome
    data class TransportError(val reason: String) : ProbeOutcome
    data object Timeout : ProbeOutcome
}

/** Low-level engine events. Translated into CallStateMachine events by the CallManager. */
sealed interface SipEvent {
    data class Registration(
        val accountId: String,
        val code: Int,
        val reason: String,
        val expiresSec: Int,
        val active: Boolean,
        val transportError: Boolean = false,
    ) : SipEvent

    data class IncomingCall(
        val handle: EngineCallHandle,
        val accountId: String,
        val remoteUri: String,
        val remoteNumber: String,
        val remoteName: String?,
        val headers: Map<String, String>,
        /** Call-ID header – used together with X-IVRTOP-Call-ID for push correlation. */
        val sipCallId: String,
        val replacesCall: EngineCallHandle? = null,
    ) : SipEvent

    data class CallState(
        val handle: EngineCallHandle,
        val state: EngineCallState,
        val lastStatus: Int,
        val reason: String,
        val sipCallId: String,
    ) : SipEvent

    data class CallMedia(val handle: EngineCallHandle, val active: Boolean, val localHold: Boolean, val remoteHold: Boolean) : SipEvent
    data class TransferProgress(val handle: EngineCallHandle, val statusCode: Int, val final: Boolean) : SipEvent
    data class MessageWaiting(val accountId: String, val newMessages: Int, val oldMessages: Int) : SipEvent
    data class Presence(val accountId: String, val uri: String, val state: PresenceState) : SipEvent
    data class EngineError(val message: String) : SipEvent
}

enum class EngineCallState { CALLING, INCOMING, EARLY, CONNECTING, CONFIRMED, DISCONNECTED }

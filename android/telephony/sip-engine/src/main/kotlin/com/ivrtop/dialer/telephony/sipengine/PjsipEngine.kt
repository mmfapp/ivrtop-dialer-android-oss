package com.ivrtop.dialer.telephony.sipengine

import android.media.audiofx.AcousticEchoCanceler
import android.os.Handler
import android.os.HandlerThread
import com.ivrtop.dialer.core.models.CallQuality
import com.ivrtop.dialer.core.models.DtmfMethod
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.models.SipTransport
import com.ivrtop.dialer.core.models.SrtpMode
import com.ivrtop.dialer.telephony.sip.AccountSecrets
import com.ivrtop.dialer.telephony.sip.EngineCallHandle
import com.ivrtop.dialer.telephony.sip.EngineCapabilities
import com.ivrtop.dialer.telephony.sip.EngineConfig
import com.ivrtop.dialer.telephony.sip.ProbeOutcome
import com.ivrtop.dialer.telephony.sip.SipEngine
import com.ivrtop.dialer.telephony.sip.SipEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.BuddyConfig
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.CallSendDtmfParam
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.IpChangeParam
import org.pjsip.pjsua2.SipHeader
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pj_qos_type
import org.pjsip.pjsua2.pjmedia_echo_flag
import org.pjsip.pjsua2.pjmedia_srtp_use
import org.pjsip.pjsua2.pjsip_transport_type_e
import org.pjsip.pjsua2.pjsua_call_flag
import org.pjsip.pjsua2.pjsua_dtmf_method
import org.pjsip.pjsua2.pjsua_ipv6_use
import org.pjsip.pjsua2.pjsua_nat64_opt
import org.pjsip.pjsua2.pjsua_stun_use
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * PJSUA2 implementation of [SipEngine].
 *
 * Threading model
 *  - PJSIP runs ONE internal worker thread (uaConfig.threadCnt = 1) that blocks in the ioqueue
 *    (no busy polling → no battery cost while idle). Callbacks arrive on that thread.
 *  - Every API call from Kotlin is executed on a dedicated HandlerThread ("ivrtop-pjsip") that is
 *    registered with PJLIB once. This serializes all mutations of the SWIG object graph.
 */
/** Must match `PJ_HAS_IPV6` of the bundled libpjsua2 (currently not enabled in scripts/pjsip). */
private const val PJSIP_BUILT_WITH_IPV6 = false

@Singleton
class PjsipEngine @Inject constructor() : SipEngine {

    private val thread = HandlerThread("ivrtop-pjsip").apply { start() }
    private val pjDispatcher = Handler(thread.looper).asCoroutineDispatcher("pjsip")
    private val scope = CoroutineScope(SupervisorJob() + pjDispatcher)

    private val _events = MutableSharedFlow<SipEvent>(extraBufferCapacity = 512)
    override val events: SharedFlow<SipEvent> = _events.asSharedFlow()

    private var endpoint: Endpoint? = null
    private var logWriter: PjLogWriter? = null
    private val accounts = ConcurrentHashMap<String, PjAccount>()
    private val calls = ConcurrentHashMap<Int, PjCall>()
    private val buddies = ConcurrentHashMap<String, PjBuddy>()
    private val conferenceMembers = mutableSetOf<Int>()
    @Volatile private var started = false
    /**
     * Whether libpjsua2 was built with PJ_HAS_IPV6. Our build (scripts/pjsip) does NOT enable it, and
     * pjsua_acc_add() then ASSERTS (process abort) if any account asks for IPv6 SIP/media or NAT64.
     * Creating an IPv6 socket may still succeed, so this cannot be detected at runtime – flip it only
     * together with `#define PJ_HAS_IPV6 1` in the PJSIP build.
     */
    private val ipv6Available = PJSIP_BUILT_WITH_IPV6
    private var codecIds: List<String> = emptyList()

    override val isStarted: Boolean get() = started

    override val capabilities: EngineCapabilities
        get() = EngineCapabilities(
            // PJSIP has no WebSocket transport → WS/WSS are deliberately not offered in the UI.
            transports = setOf("udp", "tcp", "tls"),
            codecs = codecIds,
            srtp = true,
            tls = true,
            ice = true,
            presence = true,
            mwi = true,
            conference = true,
            attendedTransfer = true,
        )

    internal fun emit(event: SipEvent) {
        if (!_events.tryEmit(event)) Timber.w("SIP event buffer full, dropped ${event::class.simpleName}")
    }

    private suspend fun <T> onPj(block: (Endpoint) -> T): T = withContext(pjDispatcher) {
        val ep = endpoint ?: error("SIP engine not started")
        if (!ep.libIsThreadRegistered()) ep.libRegisterThread(thread.name)
        block(ep)
    }

    // ------------------------------------------------------------------ lifecycle

    override suspend fun start(config: EngineConfig) = withContext(pjDispatcher) {
        if (started) return@withContext
        System.loadLibrary("c++_shared")
        System.loadLibrary("pjsua2")

        val ep = Endpoint()
        ep.libCreate()
        val epCfg = EpConfig()

        epCfg.uaConfig.apply {
            userAgent = config.userAgent
            maxCalls = config.maxCalls.toLong()
            threadCnt = 1
            mainThreadOnly = false
            stunIgnoreFailure = true
            config.stunServers.forEach { stunServer.add(it) }
        }

        val writer = PjLogWriter(config.logSink)
        logWriter = writer
        epCfg.logConfig.apply {
            level = config.logLevel.toLong()
            consoleLevel = config.logLevel.toLong()
            this.writer = writer
            decor = 0 // our sink adds timestamps
        }

        epCfg.medConfig.apply {
            clockRate = 16000
            sndClockRate = 0
            quality = 8
            noVad = true
            channelCount = 1
            // Echo cancellation: prefer the platform AEC (VOICE_COMMUNICATION input preset used by
            // PJMEDIA's Android audio device); fall back to WebRTC AEC in software.
            if (config.echoCancellation && !AcousticEchoCanceler.isAvailable()) {
                ecTailLen = 200
                var flags = pjmedia_echo_flag.PJMEDIA_ECHO_WEBRTC
                if (config.noiseSuppression) flags = flags or pjmedia_echo_flag.PJMEDIA_ECHO_USE_NOISE_SUPPRESSOR
                ecOptions = flags.toLong()
            } else if (!config.echoCancellation) {
                ecTailLen = 0
            } else {
                ecTailLen = 0 // platform AEC handles it
            }
        }

        ep.libInit(epCfg)

        // Transports: unbound (port 0) so multiple accounts share them; TLS verifies the server
        // certificate chain + hostname against the exported system CA bundle.
        fun tp(type: Int, tls: Boolean = false) = try {
            val tc = TransportConfig().apply {
                port = 0
                qosType = pj_qos_type.PJ_QOS_TYPE_VOICE
                if (tls) {
                    tlsConfig.caListFile = config.caBundlePath.orEmpty()
                    tlsConfig.verifyServer = true
                    tlsConfig.verifyClient = false
                }
            }
            ep.transportCreate(type, tc)
        } catch (e: Exception) {
            Timber.w("transport $type unavailable: ${e.message}")
            -1
        }
        tp(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP)
        tp(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP)
        tp(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, tls = true)
        // IPv6 transports only when the PJSIP build supports IPv6 (see [ipv6Available]).
        if (ipv6Available) {
            tp(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP6)
            tp(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP6)
            tp(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS6, tls = true)
        }

        ep.libStart()
        endpoint = ep
        if (!ep.libIsThreadRegistered()) ep.libRegisterThread(thread.name)

        codecIds = try {
            ep.codecEnum2().map { it.codecId }
        } catch (e: Exception) {
            emptyList()
        }
        started = true
        Timber.i("PJSIP started (${ep.libVersion().full}); codecs=$codecIds")
    }

    override suspend fun stop() = withContext(pjDispatcher) {
        val ep = endpoint ?: return@withContext
        try {
            ep.hangupAllCalls()
            buddies.values.forEach { runCatching { it.delete() } }
            buddies.clear()
            accounts.values.forEach { runCatching { it.shutdown(); it.delete() } }
            accounts.clear()
            calls.values.forEach { runCatching { it.delete() } }
            calls.clear()
            ep.libDestroy()
        } catch (e: Exception) {
            Timber.e(e, "PJSIP shutdown error")
        } finally {
            ep.delete()
            endpoint = null
            logWriter = null
            started = false
        }
    }

    // ------------------------------------------------------------------ accounts

    override suspend fun upsertAccount(account: SipAccount, secrets: AccountSecrets, register: Boolean) = onPj { ep ->
        applyCodecPriorities(ep, account)
        val cfg = buildAccountConfig(account, secrets, register)
        val existing = accounts[account.id]
        if (existing != null) {
            existing.modify(cfg)
        } else {
            val acc = PjAccount(account.id, this)
            try {
                acc.create(cfg, true)
            } catch (e: Exception) {
                acc.delete() // on the PJSIP-registered thread – never leave it to the GC finalizer
                throw e
            }
            accounts[account.id] = acc
        }
        Unit
    }

    override suspend fun removeAccount(accountId: String) = onPj {
        accounts.remove(accountId)?.let { acc ->
            buddies.entries.removeIf { (k, b) -> if (k.startsWith("$accountId|")) { runCatching { b.delete() }; true } else false }
            runCatching { acc.setRegistration(false) }
            runCatching { acc.shutdown() }
            acc.delete()
        }
        Unit
    }

    override suspend fun setRegistration(accountId: String, register: Boolean) = onPj {
        val acc = accounts[accountId] ?: return@onPj
        try {
            acc.setRegistration(register)
        } catch (e: Exception) {
            // PJSIP throws if a REGISTER is already pending – harmless.
            Timber.d("setRegistration($register) skipped: ${e.message}")
        }
    }

    override suspend fun hasAccount(accountId: String): Boolean = accounts.containsKey(accountId)

    override suspend fun probeRegistration(account: SipAccount, secrets: AccountSecrets, timeoutMs: Long): ProbeOutcome {
        val probeId = "probe-" + UUID.randomUUID()
        val started = System.currentTimeMillis()
        val resultFlow = events.filterIsInstance<SipEvent.Registration>()
        onPj { ep ->
            val cfg = buildAccountConfig(account.copy(id = probeId), secrets, register = false)
            val acc = PjAccount(probeId, this, isProbe = true)
            try {
                acc.create(cfg, true)
            } catch (e: Exception) {
                acc.delete()
                throw e
            }
            accounts[probeId] = acc
            Unit
        }
        val result = try {
            withTimeoutOrNull(timeoutMs) {
                kotlinx.coroutines.coroutineScope {
                    // UNDISPATCHED: subscribe to events before the REGISTER is sent (no race).
                    val deferred = this.async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                        resultFlow.first { it.accountId == probeId && (it.code >= 200 || it.transportError) }
                    }
                    setRegistration(probeId, true)
                    deferred.await()
                }
            }
        } finally {
            // Unregister + delete the temporary account (best effort).
            runCatching { setRegistration(probeId, false) }
            runCatching { removeAccount(probeId) }
        }
        return when {
            result == null -> ProbeOutcome.Timeout
            result.transportError -> ProbeOutcome.TransportError(result.reason)
            result.code in 200..299 -> ProbeOutcome.Registered(result.code, result.expiresSec, System.currentTimeMillis() - started)
            else -> ProbeOutcome.Rejected(result.code, result.reason)
        }
    }

    private fun buildAccountConfig(a: SipAccount, s: AccountSecrets, register: Boolean): AccountConfig {
        val cfg = AccountConfig()
        val t = a.transport.takeIf { it == SipTransport.UDP || it == SipTransport.TCP || it == SipTransport.TLS } ?: SipTransport.TLS
        val portPart = a.port?.let { ":$it" } ?: ""
        val registrarHost = a.registrar?.takeIf { it.isNotBlank() } ?: a.domain
        val tParam = ";transport=${t.wire}"
        val display = a.displayName.replace("\"", "")

        cfg.idUri = "\"$display\" <sip:${a.username}@${a.domain}>"
        cfg.regConfig.apply {
            registrarUri = "sip:$registrarHost$portPart$tParam"
            registerOnAdd = register
            timeoutSec = a.registrationExpirySec.toLong()
            // Retries are owned by RegistrationManager (backoff + Fail2Ban protection).
            retryIntervalSec = 0
            firstRetryIntervalSec = 0
            dropCallsOnFail = false
            unregWaitMsec = 2000
        }
        // Without IPv6 in the PJSIP build, pjsua_acc_add() ASSERTS (process abort) unless the SIP,
        // media and NAT64 IPv6 options are all disabled – see pjsua_acc.c (#if !PJ_HAS_IPV6).
        val v6 = ipv6Available && a.nat.ipVersion != com.ivrtop.dialer.core.models.IpVersion.IPV4
        cfg.sipConfig.apply {
            ipv6Use = if (v6) pjsua_ipv6_use.PJSUA_IPV6_ENABLED_NO_PREFERENCE else pjsua_ipv6_use.PJSUA_IPV6_DISABLED
            authCreds.add(AuthCredInfo("digest", a.realm?.takeIf { it.isNotBlank() } ?: "*", a.effectiveAuthUser, 0, s.password))
            a.outboundProxy?.takeIf { it.isNotBlank() }?.let { proxy ->
                val p = if (proxy.startsWith("sip:")) proxy else "sip:$proxy"
                proxies.add("$p;lr$tParam")
            }
        }
        cfg.natConfig.apply {
            if (!v6) nat64Opt = pjsua_nat64_opt.PJSUA_NAT64_DISABLED
            udpKaIntervalSec = if (t == SipTransport.UDP) a.keepAliveSec.toLong() else 0
            contactRewriteUse = if (a.nat.rewriteContact) 1 else 0
            viaRewriteUse = if (a.nat.rewriteContact) 1 else 0
            sdpNatRewriteUse = 0
            sipOutboundUse = 0 // Asterisk does not implement RFC 5626 reliably.
            sipStunUse = if (a.nat.stunServer.isNullOrBlank()) pjsua_stun_use.PJSUA_STUN_USE_DISABLED else pjsua_stun_use.PJSUA_STUN_USE_DEFAULT
            mediaStunUse = sipStunUse
            iceEnabled = a.nat.iceEnabled
            turnEnabled = a.nat.turnEnabled && !a.nat.turnServer.isNullOrBlank()
            if (turnEnabled) {
                turnServer = a.nat.turnServer
                turnUserName = a.nat.turnUsername.orEmpty()
                turnPassword = s.turnPassword.orEmpty()
                turnPasswordType = 0
            }
        }
        cfg.mediaConfig.apply {
            srtpUse = when (if (a.policy.requireSrtp) SrtpMode.MANDATORY else a.srtp) {
                SrtpMode.DISABLED -> pjmedia_srtp_use.PJMEDIA_SRTP_DISABLED
                SrtpMode.OPTIONAL -> pjmedia_srtp_use.PJMEDIA_SRTP_OPTIONAL
                SrtpMode.MANDATORY -> pjmedia_srtp_use.PJMEDIA_SRTP_MANDATORY
            }
            // SDES keys travel in SDP; only allow them over TLS-protected signalling.
            srtpSecureSignaling = if (t == SipTransport.TLS) 1 else 0
            // Requesting IPv6 media from a PJSIP built without IPv6 aborts the whole process
            // (pjsua_acc_add assertion "ipv6_media_use == PJSUA_IPV6_DISABLED") – only enable it
            // when an IPv6 transport actually exists.
            ipv6Use = if (v6) pjsua_ipv6_use.PJSUA_IPV6_ENABLED_NO_PREFERENCE else pjsua_ipv6_use.PJSUA_IPV6_DISABLED
        }
        cfg.mwiConfig.apply {
            enabled = true
            expirationSec = 3600
        }
        cfg.presConfig.publishEnabled = false
        cfg.callConfig.timerUse = 1 // session timers optional (RFC 4028)
        return cfg
    }

    private fun applyCodecPriorities(ep: Endpoint, account: SipAccount) {
        // NOTE: PJSUA codec priorities are global (per endpoint). The default account's list wins.
        if (!account.isDefault && accounts.isNotEmpty()) return
        codecIds.forEach { id ->
            val pref = account.codecs.firstOrNull { id.startsWith(it.id, ignoreCase = true) }
            val prio = when {
                pref == null -> 0
                !pref.enabled -> 0
                else -> pref.priority.coerceIn(1, 254)
            }
            runCatching { ep.codecSetPriority(id, prio.toShort()) }
        }
    }

    // ------------------------------------------------------------------ calls

    internal fun onIncomingCall(acc: PjAccount, call: PjCall, rawMessage: String) {
        if (acc.isProbe) {
            scope.launch { runCatching { call.hangup(CallOpParam().apply { statusCode = 480 }) }; call.delete() }
            return
        }
        calls[call.id] = call
        val headers = SipMessageParser.headers(rawMessage)
        val ci = try { call.info } catch (e: Exception) { null }
        val remoteUri = ci?.remoteUri.orEmpty()
        call.sipCallId = ci?.callIdString.orEmpty()
        emit(
            SipEvent.IncomingCall(
                handle = EngineCallHandle(call.id),
                accountId = acc.accountId,
                remoteUri = remoteUri,
                remoteNumber = SipMessageParser.remoteNumber(remoteUri, headers),
                remoteName = SipMessageParser.remoteName(remoteUri, headers),
                headers = headers,
                sipCallId = call.sipCallId,
            ),
        )
    }

    internal fun onCallDisconnected(callId: Int) {
        scope.launch {
            conferenceMembers.remove(callId)
            calls.remove(callId)?.delete()
        }
    }

    /** Wire the call's audio into the conference bridge (mic → call, call → speaker). */
    internal fun connectAudio(call: PjCall, mediaIndex: Int) {
        val ep = endpoint ?: return
        try {
            val am: AudioMedia = call.getAudioMedia(mediaIndex)
            val mgr = ep.audDevManager()
            am.startTransmit(mgr.playbackDevMedia)
            if (!call.micMuted) mgr.captureDevMedia.startTransmit(am) else mgr.captureDevMedia.stopTransmit(am)
        } catch (e: Exception) {
            Timber.e(e, "connectAudio failed")
        }
    }

    override suspend fun makeCall(accountId: String, targetUri: String, headers: Map<String, String>): EngineCallHandle = onPj {
        val acc = accounts[accountId] ?: error("account not loaded")
        val call = PjCall(acc, -1, this)
        val prm = CallOpParam(true)
        headers.forEach { (k, v) ->
            prm.txOption.headers.add(SipHeader().apply { hName = k; hValue = v })
        }
        call.makeCall(targetUri, prm)
        calls[call.id] = call
        EngineCallHandle(call.id)
    }

    private fun call(handle: EngineCallHandle): PjCall = calls[handle.value] ?: error("call ${handle.value} not found")

    override suspend fun ringing(handle: EngineCallHandle) = onPj {
        call(handle).answer(CallOpParam().apply { statusCode = 180 })
    }

    override suspend fun answer(handle: EngineCallHandle) = onPj {
        call(handle).answer(CallOpParam(true).apply { statusCode = 200 })
    }

    override suspend fun hangup(handle: EngineCallHandle, sipStatus: Int, reason: String?, contact: String?) = onPj {
        val c = calls[handle.value] ?: return@onPj
        val prm = CallOpParam(true)
        if (sipStatus > 0) prm.statusCode = sipStatus
        reason?.let { prm.reason = it }
        contact?.let { prm.txOption.headers.add(SipHeader().apply { hName = "Contact"; hValue = "<$it>" }) }
        try {
            c.hangup(prm)
        } catch (e: Exception) {
            Timber.w("hangup failed: ${e.message}")
        }
    }

    override suspend fun setHold(handle: EngineCallHandle, hold: Boolean) = onPj {
        val c = call(handle)
        if (hold) {
            c.setHold(CallOpParam(true))
        } else {
            val prm = CallOpParam(true)
            prm.opt.flag = pjsua_call_flag.PJSUA_CALL_UNHOLD.toLong()
            c.reinvite(prm)
        }
    }

    override suspend fun setMicMuted(handle: EngineCallHandle, muted: Boolean) = onPj { ep ->
        val c = call(handle)
        c.micMuted = muted
        val capture = ep.audDevManager().captureDevMedia
        val ci = c.info
        for (i in 0 until ci.media.size) {
            val am = try { c.getAudioMedia(i) } catch (_: Exception) { continue }
            if (muted) capture.stopTransmit(am) else capture.startTransmit(am)
        }
    }

    override suspend fun sendDtmf(handle: EngineCallHandle, digits: String, method: DtmfMethod) = onPj {
        val prm = CallSendDtmfParam().apply {
            this.digits = digits
            this.method = when (method) {
                DtmfMethod.SIP_INFO -> pjsua_dtmf_method.PJSUA_DTMF_METHOD_SIP_INFO
                // In-band is not generated by PJSUA2 directly; RFC 4733 is accepted by Asterisk in all modes.
                DtmfMethod.RFC4733, DtmfMethod.INBAND -> pjsua_dtmf_method.PJSUA_DTMF_METHOD_RFC2833
            }
            duration = 160
        }
        call(handle).sendDtmf(prm)
    }

    override suspend fun transferBlind(handle: EngineCallHandle, targetUri: String) = onPj {
        call(handle).xfer(targetUri, CallOpParam(true))
    }

    override suspend fun transferAttended(handle: EngineCallHandle, consultHandle: EngineCallHandle) = onPj {
        call(handle).xferReplaces(call(consultHandle), CallOpParam(true))
    }

    override suspend fun conference(handles: List<EngineCallHandle>, enabled: Boolean) = onPj {
        val medias = handles.mapNotNull { h ->
            calls[h.value]?.let { c -> runCatching { c.getAudioMedia(-1) }.getOrNull()?.let { h.value to it } }
        }
        for ((idA, a) in medias) for ((idB, b) in medias) {
            if (idA == idB) continue
            if (enabled) a.startTransmit(b) else a.stopTransmit(b)
        }
        if (enabled) conferenceMembers += handles.map { it.value } else conferenceMembers -= handles.map { it.value }.toSet()
    }

    override suspend fun quality(handle: EngineCallHandle): CallQuality? = onPj {
        val c = calls[handle.value] ?: return@onPj null
        try {
            val info = c.info
            var idx = -1
            for (i in 0 until info.media.size) if (info.media[i].type == org.pjsip.pjsua2.pjmedia_type.PJMEDIA_TYPE_AUDIO) { idx = i; break }
            if (idx < 0) return@onPj null
            val si = c.getStreamInfo(idx.toLong())
            val st = c.getStreamStat(idx.toLong())
            val rx = st.rtcp.rxStat
            val rttMs = (st.rtcp.rttUsec.mean / 1000).toInt()
            val jitterMs = (rx.jitterUsec.mean / 1000).toInt()
            val total = rx.pkt + rx.loss
            val lossPct = if (total > 0) rx.loss * 100f / total else 0f
            CallQuality(
                codec = "${si.codecName}/${si.codecClockRate / 1000}k",
                rttMs = rttMs,
                jitterMs = jitterMs,
                lossPercent = lossPct,
                mos = estimateMos(rttMs, jitterMs, lossPct),
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun handleIpChange() = onPj { ep ->
        Timber.i("IP change → PJSIP handleIpChange")
        ep.handleIpChange(IpChangeParam())
    }

    override suspend fun subscribePresence(accountId: String, uri: String) = onPj {
        val key = "$accountId|$uri"
        if (buddies.containsKey(key)) return@onPj
        val acc = accounts[accountId] ?: return@onPj
        val b = PjBuddy(accountId, uri, this)
        b.create(acc, BuddyConfig().apply { this.uri = uri; subscribe = true })
        buddies[key] = b
    }

    override suspend fun unsubscribePresence(accountId: String, uri: String) = onPj {
        buddies.remove("$accountId|$uri")?.let { runCatching { it.subscribePresence(false) }; it.delete() }
        Unit
    }

    override suspend fun setAudioActive(active: Boolean) {
        // PJMEDIA opens the sound device on demand and auto-closes it 1 s after the last call
        // (snd_auto_close_time). Nothing to do; kept for engines that need explicit control.
    }

    /** Simplified ITU-T G.107 E-model → MOS. */
    private fun estimateMos(rttMs: Int, jitterMs: Int, lossPct: Float): Float {
        val effLatency = rttMs / 2.0 + jitterMs * 2.0 + 10.0
        var r = if (effLatency < 160) 93.2 - effLatency / 40 else 93.2 - (effLatency - 120) / 10
        r -= lossPct * 2.5
        r = max(0.0, min(100.0, r))
        val mos = 1 + 0.035 * r + 0.000007 * r * (r - 60) * (100 - r)
        return mos.toFloat().let { if (it.isNaN()) 1f else it }.coerceIn(1f, 4.5f)
    }
}

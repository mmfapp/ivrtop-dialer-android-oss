package com.ivrtop.dialer.telephony.sipengine

import com.ivrtop.dialer.core.common.log.Redactor
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.models.PresenceState
import com.ivrtop.dialer.telephony.sip.EngineCallHandle
import com.ivrtop.dialer.telephony.sip.EngineCallState
import com.ivrtop.dialer.telephony.sip.SipEvent
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.Buddy
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.LogEntry
import org.pjsip.pjsua2.LogWriter
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.OnCallTransferStatusParam
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnMwiInfoParam
import org.pjsip.pjsua2.OnRegStateParam
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjrpid_activity
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsua_buddy_status
import org.pjsip.pjsua2.pjsua_call_media_status

/*
 * PJSUA2 callback subclasses. Callbacks run on the PJSIP worker thread: they ONLY extract data
 * and hand it to the engine (non-blocking). Strong references to every Account/Call/Buddy are
 * held by PjsipEngine – letting SWIG objects be garbage collected while PJSIP still uses them
 * causes native crashes.
 */

internal class PjAccount(
    val accountId: String,
    private val engine: PjsipEngine,
    val isProbe: Boolean = false,
) : Account() {

    override fun onRegState(prm: OnRegStateParam) {
        val active = try { info.regIsActive } catch (_: Exception) { false }
        engine.emit(
            SipEvent.Registration(
                accountId = accountId,
                code = prm.code,
                reason = prm.reason.orEmpty(),
                expiresSec = prm.expiration.toInt(),
                active = active,
                // pj_status_t != PJ_SUCCESS means the request never got a SIP response (DNS/TCP/TLS failure).
                transportError = prm.status != 0,
            ),
        )
    }

    override fun onIncomingCall(prm: OnIncomingCallParam) {
        val raw = prm.rdata.wholeMsg
        val call = PjCall(this, prm.callId, engine)
        engine.onIncomingCall(this, call, raw)
    }

    override fun onMwiInfo(prm: OnMwiInfoParam) {
        // Body example (RFC 3842):  Messages-Waiting: yes\r\nVoice-Message: 2/8 (0/0)
        val body = prm.rdata.wholeMsg
        val match = Regex("(?i)Voice-Message:\\s*(\\d+)/(\\d+)").find(body)
        val newMsgs = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val oldMsgs = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
        engine.emit(SipEvent.MessageWaiting(accountId, newMsgs, oldMsgs))
    }
}

internal class PjCall(acc: Account, callId: Int, private val engine: PjsipEngine) : Call(acc, callId) {
    @Volatile var micMuted: Boolean = false
    @Volatile var sipCallId: String = ""

    override fun onCallState(prm: OnCallStateParam) {
        val ci = try { info } catch (_: Exception) { return }
        sipCallId = ci.callIdString.orEmpty()
        val state = when (ci.state) {
            pjsip_inv_state.PJSIP_INV_STATE_CALLING -> EngineCallState.CALLING
            pjsip_inv_state.PJSIP_INV_STATE_INCOMING -> EngineCallState.INCOMING
            pjsip_inv_state.PJSIP_INV_STATE_EARLY -> EngineCallState.EARLY
            pjsip_inv_state.PJSIP_INV_STATE_CONNECTING -> EngineCallState.CONNECTING
            pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> EngineCallState.CONFIRMED
            pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> EngineCallState.DISCONNECTED
            else -> return
        }
        engine.emit(SipEvent.CallState(EngineCallHandle(id), state, ci.lastStatusCode, ci.lastReason.orEmpty(), sipCallId))
        if (state == EngineCallState.DISCONNECTED) engine.onCallDisconnected(id)
    }

    override fun onCallMediaState(prm: OnCallMediaStateParam) {
        val ci = try { info } catch (_: Exception) { return }
        var active = false
        var localHold = false
        var remoteHold = false
        val media = ci.media
        for (i in 0 until media.size) {
            val cmi = media[i]
            if (cmi.type != pjmedia_type.PJMEDIA_TYPE_AUDIO) continue
            when (cmi.status) {
                pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE -> active = true
                pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD -> { active = true; remoteHold = true }
                pjsua_call_media_status.PJSUA_CALL_MEDIA_LOCAL_HOLD -> localHold = true
                else -> Unit
            }
            if (cmi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                cmi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD
            ) {
                engine.connectAudio(this, i)
            }
        }
        engine.emit(SipEvent.CallMedia(EngineCallHandle(id), active, localHold, remoteHold))
    }

    override fun onCallTransferStatus(prm: OnCallTransferStatusParam) {
        engine.emit(SipEvent.TransferProgress(EngineCallHandle(id), prm.statusCode, prm.finalNotify))
        // Keep receiving NOTIFYs until the final one.
        prm.cont = !prm.finalNotify
    }
}

internal class PjBuddy(val accountId: String, val uri: String, private val engine: PjsipEngine) : Buddy() {
    override fun onBuddyState() {
        val info = try { info } catch (_: Exception) { return }
        val pres = info.presStatus
        val note = pres.note.orEmpty().lowercase() + " " + pres.statusText.orEmpty().lowercase()
        val state = when {
            pres.status == pjsua_buddy_status.PJSUA_BUDDY_STATUS_OFFLINE -> PresenceState.OFFLINE
            pres.status == pjsua_buddy_status.PJSUA_BUDDY_STATUS_UNKNOWN -> PresenceState.UNKNOWN
            "ring" in note -> PresenceState.RINGING
            "on the phone" in note || "busy" in note || "in use" in note ||
                pres.activity == pjrpid_activity.PJRPID_ACTIVITY_BUSY -> PresenceState.BUSY
            "dnd" in note || "do not disturb" in note -> PresenceState.DND
            else -> PresenceState.AVAILABLE
        }
        engine.emit(SipEvent.Presence(accountId, uri, state))
    }
}

internal class PjLogWriter(private val sink: (String) -> Unit) : LogWriter() {
    override fun write(entry: LogEntry) {
        sink(Redactor.redact(entry.msg.orEmpty()).trimEnd())
    }
}

/** Header helpers for the raw SIP message text PJSUA2 gives us in onIncomingCall. */
internal object SipMessageParser {
    fun headers(raw: String): Map<String, String> {
        val head = raw.substringBefore("\r\n\r\n").substringBefore("\n\n")
        val map = LinkedHashMap<String, String>()
        head.lineSequence().drop(1).forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val name = line.substring(0, idx).trim().lowercase()
                // Compact forms: i = Call-ID, f = From
                val canonical = when (name) { "i" -> "call-id"; "f" -> "from"; else -> name }
                map.putIfAbsent(canonical, line.substring(idx + 1).trim())
            }
        }
        return map
    }

    fun remoteNumber(remoteUri: String, headers: Map<String, String>): String {
        // Prefer P-Asserted-Identity (trusted PBX network) then From.
        val pai = headers["p-asserted-identity"]
        val source = pai ?: remoteUri
        return PhoneNumbers.userFromSipUri(source)
    }

    fun remoteName(remoteUri: String, headers: Map<String, String>): String? =
        headers["p-asserted-identity"]?.let { PhoneNumbers.displayNameFromSipUri(it) }
            ?: PhoneNumbers.displayNameFromSipUri(remoteUri)
}

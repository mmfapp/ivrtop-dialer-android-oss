package com.ivrtop.dialer.telephony.telecom.pbx

import com.ivrtop.dialer.core.common.ApplicationScope
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.models.MessageWaiting
import com.ivrtop.dialer.core.models.PresenceState
import com.ivrtop.dialer.core.models.VoicemailMessage
import com.ivrtop.dialer.telephony.sip.PbxFeature
import com.ivrtop.dialer.telephony.sip.PbxIntegrationProvider
import com.ivrtop.dialer.telephony.sip.SipEngine
import com.ivrtop.dialer.telephony.sip.SipEvent
import com.ivrtop.dialer.telephony.sip.VoicemailProvider
import com.ivrtop.dialer.telephony.telecom.service.CallNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voicemail via SIP MWI (RFC 3842 SUBSCRIBE/NOTIFY – Asterisk `mailboxes=` on the endpoint).
 * Listening happens through the PBX IVR (dial *97). A message list / audio download requires the
 * IVRTOP voicemail REST API (feature flag "voicemail_api") – the DAO is ready for it.
 */
@Singleton
class SipVoicemailProvider @Inject constructor(
    engine: SipEngine,
    private val accounts: AccountRepository,
    private val voicemailDao: com.ivrtop.dialer.core.database.VoicemailDao,
    private val notifier: CallNotifier,
    @ApplicationScope scope: CoroutineScope,
) : VoicemailProvider {

    private val _waiting = MutableStateFlow<Map<String, MessageWaiting>>(emptyMap())
    override val waiting: StateFlow<Map<String, MessageWaiting>> = _waiting.asStateFlow()

    override val messages: Flow<List<VoicemailMessage>> = voicemailDao.observeAll().map { list ->
        list.map { VoicemailMessage(it.id, it.accountId, it.fromNumber, it.fromName, it.receivedAt, it.durationSec, it.isNew, it.audioUrl, it.transcript) }
    }

    init {
        scope.launch {
            engine.events.filterIsInstance<SipEvent.MessageWaiting>().collect { ev ->
                val before = _waiting.value[ev.accountId]?.newMessages ?: 0
                _waiting.update { it + (ev.accountId to MessageWaiting(ev.accountId, ev.newMessages, ev.oldMessages)) }
                val total = _waiting.value.values.sumOf { it.newMessages }
                if (ev.newMessages > before || ev.newMessages == 0) notifier.postVoicemail(total)
            }
        }
    }

    override suspend fun refresh(accountId: String) { /* MWI is pushed by the PBX; API refresh when enabled */ }
    override suspend fun markRead(id: String) = voicemailDao.markRead(id)
    override suspend fun delete(id: String) = voicemailDao.delete(id)
    override suspend fun accessNumber(accountId: String): String? = accounts.get(accountId)?.voicemailNumber
}

/**
 * Asterisk / IVRTOP feature mapping. Feature codes follow FreePBX/IVRTOP defaults and can be
 * overridden by the server feature flags. Generic SIP providers get only standards-based features.
 */
@Singleton
class AsteriskPbxIntegration @Inject constructor(
    private val engine: SipEngine,
    @ApplicationScope scope: CoroutineScope,
) : PbxIntegrationProvider {

    private val _presence = MutableStateFlow<Map<String, PresenceState>>(emptyMap())
    override val presence: StateFlow<Map<String, PresenceState>> = _presence.asStateFlow()

    init {
        scope.launch {
            engine.events.filterIsInstance<SipEvent.Presence>().collect { ev ->
                _presence.update { it + (extensionOf(ev.uri) to ev.state) }
            }
        }
    }

    override fun supports(accountId: String, feature: PbxFeature): Boolean = when (feature) {
        PbxFeature.VOICEMAIL, PbxFeature.BLF, PbxFeature.CALL_PICKUP, PbxFeature.CALL_PARK, PbxFeature.CALL_FORWARD -> true
        else -> false // require IVRTOP REST API (feature flags) – see docs/ASTERISK_INTEGRATION.md
    }

    override suspend fun watchPresence(accountId: String, extension: String) {
        engine.subscribePresence(accountId, uriFor(accountId, extension))
    }

    override suspend fun unwatchPresence(accountId: String, extension: String) {
        engine.unsubscribePresence(accountId, uriFor(accountId, extension))
    }

    override fun featureCode(accountId: String, feature: PbxFeature): String? = when (feature) {
        PbxFeature.VOICEMAIL -> "*97"
        PbxFeature.CALL_PICKUP -> "*8"
        PbxFeature.CALL_PARK -> "700"
        PbxFeature.CALL_FORWARD -> "*72"
        else -> null
    }

    private val domains = HashMap<String, String>()
    private fun uriFor(accountId: String, ext: String) = "sip:$ext@${domains[accountId] ?: com.ivrtop.dialer.core.models.SipAccount.DEFAULT_IVRTOP_DOMAIN}"
    fun setDomain(accountId: String, domain: String) { domains[accountId] = domain }
    private fun extensionOf(uri: String) = uri.removePrefix("sip:").substringBefore('@')
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PbxModule {
    @Binds abstract fun voicemail(impl: SipVoicemailProvider): VoicemailProvider
    @Binds abstract fun pbx(impl: AsteriskPbxIntegration): PbxIntegrationProvider
}

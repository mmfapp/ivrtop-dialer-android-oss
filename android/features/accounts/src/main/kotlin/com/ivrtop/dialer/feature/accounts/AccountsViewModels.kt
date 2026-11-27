package com.ivrtop.dialer.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.DtmfMethod
import com.ivrtop.dialer.core.models.NatSettings
import com.ivrtop.dialer.core.models.ProbeResult
import com.ivrtop.dialer.core.models.ProbeStep
import com.ivrtop.dialer.core.models.RegistrationState
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.models.SipTransport
import com.ivrtop.dialer.core.models.SrtpMode
import com.ivrtop.dialer.telephony.sip.ConnectionTester
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AccountRow(val account: SipAccount, val state: RegistrationState?)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repo: AccountRepository,
    private val registration: RegistrationManager,
) : ViewModel() {

    val rows: StateFlow<List<AccountRow>> = combine(repo.accounts, registration.states) { accs, states ->
        accs.map { AccountRow(it, states[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun reconnect(id: String) = viewModelScope.launch { registration.reconnectNow(id) }
    fun setEnabled(id: String, on: Boolean) = viewModelScope.launch { repo.setEnabled(id, on) }
    fun setDefault(id: String) = viewModelScope.launch { repo.setDefault(id) }
    fun delete(acc: SipAccount, deleteHistory: Boolean) = viewModelScope.launch {
        repo.delete(acc.id, deleteHistory)
    }
}

/** Form model – plain strings so partially typed values never crash. */
data class AccountForm(
    val id: String? = null,
    val displayName: String = "",
    val phoneNumber: String = "",
    val username: String = "",
    val authUsername: String = "",
    val password: String = "",
    val passwordStored: Boolean = false,
    val domain: String = SipAccount.DEFAULT_IVRTOP_DOMAIN,
    val registrar: String = "",
    val proxy: String = "",
    val realm: String = "",
    val port: String = "",
    val transport: SipTransport? = null, // null = auto-detect
    val expiry: String = "300",
    val keepAlive: String = "25",
    val stun: String = "",
    val turn: String = "",
    val turnUser: String = "",
    val turnPassword: String = "",
    val ice: Boolean = false,
    val srtp: SrtpMode = SrtpMode.OPTIONAL,
    val dtmf: DtmfMethod = DtmfMethod.RFC4733,
    val push: Boolean = false,
    val voicemail: String = "*97",
    val enabled: Boolean = true,
    val incoming: Boolean = true,
    val outgoing: Boolean = true,
    val accountDnd: Boolean = false,
    val colorArgb: Long = 0xFF0E78A6,
    val allowInsecure: Boolean = false,
    val managed: Boolean = false,
    val allowEdit: Boolean = true,
) {
    val valid: Boolean get() = username.isNotBlank() && domain.isNotBlank() && (password.isNotBlank() || passwordStored)
}

sealed interface EditStatus {
    data object Idle : EditStatus
    data class Testing(val step: ProbeStep) : EditStatus
    data object Saved : EditStatus
    data class Error(val code: AppErrorCode, val technical: String?) : EditStatus
}

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: AccountRepository,
    private val tester: ConnectionTester,
    private val registration: RegistrationManager,
) : ViewModel() {

    private val accountId: String? = savedState.get<String>("accountId")?.takeIf { it.isNotBlank() }
    private var original: SipAccount? = null

    private val _form = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = _form.asStateFlow()
    private val _status = MutableStateFlow<EditStatus>(EditStatus.Idle)
    val status: StateFlow<EditStatus> = _status.asStateFlow()

    init {
        accountId?.let { id ->
            viewModelScope.launch {
                val a = repo.get(id) ?: return@launch
                original = a
                _form.value = AccountForm(
                    id = a.id, displayName = a.displayName, phoneNumber = a.phoneNumber.orEmpty(), username = a.username,
                    authUsername = a.authUsername.orEmpty(), passwordStored = repo.password(id) != null, domain = a.domain,
                    registrar = a.registrar.orEmpty(), proxy = a.outboundProxy.orEmpty(), realm = a.realm.orEmpty(),
                    port = a.port?.toString().orEmpty(), transport = a.transport, expiry = a.registrationExpirySec.toString(),
                    keepAlive = a.keepAliveSec.toString(), stun = a.nat.stunServer.orEmpty(), turn = a.nat.turnServer.orEmpty(),
                    turnUser = a.nat.turnUsername.orEmpty(), ice = a.nat.iceEnabled, srtp = a.srtp, dtmf = a.dtmfMethod,
                    push = a.pushEnabled, voicemail = a.voicemailNumber.orEmpty(), enabled = a.enabled, incoming = a.acceptIncoming,
                    outgoing = a.allowOutgoing, accountDnd = a.dnd, colorArgb = a.colorArgb,
                    allowInsecure = a.policy.allowInsecureTransportFallback || !a.transport.encrypted,
                    managed = a.provisioning?.managed == true, allowEdit = a.policy.allowUserEdit,
                )
            }
        }
    }

    fun update(transform: (AccountForm) -> AccountForm) = _form.update(transform)

    private fun toAccount(f: AccountForm, transport: SipTransport, port: Int?): SipAccount {
        val base = original ?: SipAccount(id = f.id ?: UUID.randomUUID().toString(), displayName = "", username = "")
        return base.copy(
            displayName = f.displayName.ifBlank { f.phoneNumber.ifBlank { f.username } },
            phoneNumber = f.phoneNumber.ifBlank { null },
            username = f.username.trim(),
            authUsername = f.authUsername.ifBlank { null },
            domain = f.domain.trim(),
            registrar = f.registrar.ifBlank { null },
            outboundProxy = f.proxy.ifBlank { null },
            realm = f.realm.ifBlank { null },
            port = port,
            transport = transport,
            registrationExpirySec = f.expiry.toIntOrNull()?.coerceIn(60, 3600) ?: 300,
            keepAliveSec = f.keepAlive.toIntOrNull()?.coerceIn(10, 300) ?: 25,
            nat = NatSettings(
                stunServer = f.stun.ifBlank { null }, turnServer = f.turn.ifBlank { null }, turnUsername = f.turnUser.ifBlank { null },
                turnEnabled = f.turn.isNotBlank(), iceEnabled = f.ice,
            ),
            srtp = f.srtp,
            dtmfMethod = f.dtmf,
            pushEnabled = f.push,
            voicemailNumber = f.voicemail.ifBlank { null },
            enabled = f.enabled,
            acceptIncoming = f.incoming,
            allowOutgoing = f.outgoing,
            dnd = f.accountDnd,
            colorArgb = f.colorArgb,
        )
    }

    /** Validates with a single REGISTER (auto-detecting transport if not chosen), then saves. */
    fun testAndSave() = viewModelScope.launch {
        val f = _form.value
        if (!f.valid) return@launch
        val password = f.password.ifBlank { f.id?.let { repo.password(it) } }.orEmpty()
        val draft = toAccount(f, f.transport ?: SipTransport.TLS, f.port.toIntOrNull())
        _status.value = EditStatus.Testing(ProbeStep.LOCATE_SERVER)
        val result = tester.detect(draft, password, allowInsecure = f.allowInsecure, fixedTransport = f.transport) {
            _status.value = EditStatus.Testing(it)
        }
        when (result) {
            is ProbeResult.Success -> {
                val c = result.candidate
                val final = toAccount(f, c.transport, f.port.toIntOrNull() ?: c.port.takeIf { it != c.transport.defaultPort })
                    .let { if (f.registrar.isBlank() && !c.host.equals(f.domain, true)) it.copy(registrar = c.host) else it }
                save(final, f)
            }
            is ProbeResult.Failure -> _status.value = EditStatus.Error(result.error, result.technical + "\n" + result.tried.joinToString("\n"))
        }
    }

    /** Save without testing (e.g. editing ringtone / colour on a working account). */
    fun saveOnly() = viewModelScope.launch {
        val f = _form.value
        if (!f.valid) return@launch
        save(toAccount(f, f.transport ?: original?.transport ?: SipTransport.TLS, f.port.toIntOrNull()), f)
    }

    private suspend fun save(account: SipAccount, f: AccountForm) {
        try {
            val saved = repo.save(account, password = f.password.ifBlank { null }, turnPassword = f.turnPassword.ifBlank { null })
            registration.reloadAccounts()
            registration.reconnectNow(saved.id)
            _status.value = EditStatus.Saved
        } catch (e: AppException) {
            _status.value = EditStatus.Error(e.error, e.message)
        }
    }

    fun dismissError() { _status.value = EditStatus.Idle }
}

package com.ivrtop.dialer.telephony.telecom

import android.content.Context
import com.ivrtop.dialer.core.common.ApplicationScope
import com.ivrtop.dialer.core.common.Backoff
import com.ivrtop.dialer.core.common.Clock
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.datastore.ConnectivityMode
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.RegistrationState
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.network.NetworkMonitor
import com.ivrtop.dialer.core.network.NetworkState
import com.ivrtop.dialer.core.network.NetworkType
import com.ivrtop.dialer.telephony.sip.AccountSecrets
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import com.ivrtop.dialer.telephony.sip.SipEngine
import com.ivrtop.dialer.telephony.sip.SipEvent
import com.ivrtop.dialer.telephony.sip.SipStatusMapper
import com.ivrtop.dialer.telephony.telecom.service.KeepAliveService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Policy (docs/BACKGROUND_EXECUTION.md):
 *  - FOREGROUND_ONLY mode: registered while the app is visible and during calls (no push server,
 *    so calls are not received while the app is closed).
 *  - PERSISTENT mode: always registered; KeepAliveService (foreground, user-visible) keeps the
 *    process alive; PJSIP refreshes REGISTER before expiry, UDP keep-alives / TCP keep-alives.
 *  - Retries: exponential backoff with jitter. FINAL 401/403 responses are never retried
 *    automatically (prevents Fail2Ban bans) – user action or config change re-arms them.
 */
@Singleton
class DefaultRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineStarter: EngineStarter,
    private val engine: SipEngine,
    private val accounts: AccountRepository,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : RegistrationManager {

    private val _states = MutableStateFlow<Map<String, RegistrationState>>(emptyMap())
    override val states: StateFlow<Map<String, RegistrationState>> = _states.asStateFlow()

    private val mutex = Mutex()
    private val loaded = HashMap<String, SipAccount>()
    private val backoffs = HashMap<String, Backoff>()
    private val retryJobs = HashMap<String, Job>()
    private val wakeUntil = HashMap<String, Long>()
    private val authBlocked = HashSet<String>()
    @Volatile private var foreground = false
    @Volatile private var activeCalls = 0
    @Volatile private var started = false
    private var lastNetwork: NetworkState? = null
    private var graceJob: Job? = null

    override suspend fun start() {
        if (started) return
        started = true
        scope.launch {
            engine.events.filterIsInstance<SipEvent.Registration>().collect { onRegistration(it) }
        }
        scope.launch { accounts.changes.collect { reloadAccounts() } }
        scope.launch { network.state.collect { onNetwork(it) } }
        scope.launch {
            settings.settings.map { it.connectivityMode to it.wifiOnly }.distinctUntilChanged().collect { reloadAccounts() }
        }

        reloadAccounts()
    }

    /** Called by CallManager so registrations stay alive during calls in FOREGROUND_ONLY mode. */
    fun setActiveCallCount(count: Int) {
        val before = activeCalls
        activeCalls = count
        if (before > 0 && count == 0) scheduleGraceUnregister()
    }

    override suspend fun onAppForeground(foreground: Boolean) {
        this.foreground = foreground
        if (foreground) {
            graceJob?.cancel()
            reloadAccounts()
        } else {
            scheduleGraceUnregister()
        }
    }

    private fun scheduleGraceUnregister() {
        graceJob?.cancel()
        graceJob = scope.launch {
            delay(GRACE_MS)
            reloadAccounts()
        }
    }

    override suspend fun wakeForIncomingCall(accountId: String, holdMs: Long) {
        mutex.withLock { wakeUntil[accountId] = clock.now() + holdMs }
        authBlocked.remove(accountId)
        reloadAccounts(forceRegister = accountId)
        scope.launch {
            delay(holdMs + 1_000)
            reloadAccounts()
        }
    }

    override suspend fun reconnectNow(accountId: String?) {
        if (accountId == null) authBlocked.clear() else authBlocked.remove(accountId)
        if (accountId == null) backoffs.values.forEach { it.reset() } else backoffs[accountId]?.reset()
        reloadAccounts(forceRegister = accountId ?: ALL)
    }

    override suspend fun reloadAccounts() = reloadAccounts(forceRegister = null)

    private suspend fun reloadAccounts(forceRegister: String?) = mutex.withLock {
        val all = accounts.all()
        val s = settings.current()
        val net = network.state.value
        val wantAny = all.any { it.enabled }

        if (wantAny) engineStarter.ensureStarted() else if (!engine.isStarted) return@withLock

        // Removed accounts
        (loaded.keys - all.map { it.id }.toSet()).forEach { id ->
            runCatching { engine.removeAccount(id) }
            loaded.remove(id)
            retryJobs.remove(id)?.cancel()
            _states.update { it - id }
        }

        // No push server: background reachability = persistent connection (visible FGS).
        val persistent = s.connectivityMode == ConnectivityMode.PERSISTENT
        val now = clock.now()
        all.forEach { acc ->
            val wifiBlocked = s.wifiOnly && net.type != NetworkType.WIFI && net.type != NetworkType.ETHERNET
            val wanted = acc.enabled && !wifiBlocked && net.isConnected && (
                persistent || foreground || activeCalls > 0 ||
                    (wakeUntil[acc.id] ?: 0) > now
                )
            val register = wanted && (acc.id !in authBlocked || forceRegister == acc.id || forceRegister == ALL)

            if (!acc.enabled) {
                if (loaded.containsKey(acc.id)) runCatching { engine.removeAccount(acc.id) }
                loaded.remove(acc.id)
                setState(acc.id, RegistrationState.Disabled)
                return@forEach
            }
            val password = accounts.password(acc.id)
            if (password == null) {
                setState(acc.id, RegistrationState.Error(AppErrorCode.AUTH_FAILED, detail = "missing password"))
                return@forEach
            }
            val changed = loaded[acc.id] != acc
            if (changed || !engine.hasAccount(acc.id)) {
                engine.upsertAccount(acc, AccountSecrets(password, accounts.turnPassword(acc.id)), register)
                loaded[acc.id] = acc
            } else {
                engine.setRegistration(acc.id, register)
            }
            when {
                register -> if (_states.value[acc.id] !is RegistrationState.Registered) setState(acc.id, RegistrationState.Registering)
                !net.isConnected || wifiBlocked -> setState(acc.id, RegistrationState.NoNetwork)
                acc.id in authBlocked -> Unit // keep AuthFailed visible
                else -> setState(acc.id, RegistrationState.Idle)
            }
        }

        // Persistent mode keeps a visible foreground service alive.
        if (persistent && wantAny) KeepAliveService.start(context) else KeepAliveService.stop(context)
    }

    private fun onRegistration(ev: SipEvent.Registration) {
        if (ev.accountId.startsWith("probe-")) return
        val id = ev.accountId
        when {
            ev.code in 200..299 && ev.active -> {
                backoffs[id]?.reset()
                retryJobs.remove(id)?.cancel()
                val acc = loaded[id]
                setState(id, RegistrationState.Registered(ev.expiresSec, acc?.transport ?: com.ivrtop.dialer.core.models.SipTransport.TLS))
            }
            ev.code in 200..299 && !ev.active -> setState(id, if (network.state.value.isConnected) RegistrationState.Idle else RegistrationState.NoNetwork)
            ev.code == 401 || ev.code == 407 || ev.code == 403 -> {
                authBlocked += id
                setState(id, if (ev.code == 403) RegistrationState.AccountSuspended else RegistrationState.AuthFailed)
                Timber.w("registration rejected (${ev.code}) – automatic retries suspended for $id")
            }
            ev.code == 404 -> {
                authBlocked += id
                setState(id, RegistrationState.Error(AppErrorCode.NOT_FOUND, ev.code, ev.reason))
            }
            else -> {
                val err = SipStatusMapper.registrationError(ev.code, ev.transportError)
                val backoff = backoffs.getOrPut(id) { Backoff() }
                val wait = backoff.next()
                setState(
                    id,
                    if (!network.state.value.isConnected) RegistrationState.NoNetwork
                    else if (err == AppErrorCode.SERVER_UNREACHABLE) RegistrationState.ServerUnreachable(ev.reason)
                    else RegistrationState.Disconnected((wait / 1000).toInt()),
                )
                retryJobs.remove(id)?.cancel()
                retryJobs[id] = scope.launch {
                    delay(wait)
                    reloadAccounts(forceRegister = null)
                }
            }
        }
    }

    private suspend fun onNetwork(state: NetworkState) {
        val prev = lastNetwork
        lastNetwork = state
        if (prev == null) return
        when {
            !state.isConnected -> loaded.keys.forEach { setState(it, RegistrationState.NoNetwork) }
            !prev.isConnected -> {
                backoffs.values.forEach { it.reset() }
                reloadAccounts()
            }
            prev.networkId != state.networkId || prev.localAddresses != state.localAddresses -> {
                // Wi-Fi ⇄ cellular or new IP: let PJSIP rebuild transports, re-REGISTER and re-INVITE calls.
                if (engine.isStarted) runCatching { engine.handleIpChange() }
                reloadAccounts()
            }
        }
    }

    private fun setState(id: String, state: RegistrationState) {
        _states.update { it + (id to state) }
    }

    private companion object {
        const val GRACE_MS = 30_000L
        const val ALL = "*"
    }
}

package com.ivrtop.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.common.Clock
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.data.CallHistoryRepository
import com.ivrtop.dialer.core.data.IncomingCallFilter
import com.ivrtop.dialer.core.datastore.AppSettings
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.RegistrationState
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.telephony.sip.CallManager
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import com.ivrtop.dialer.telephony.sip.VoicemailProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUi(
    val ready: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val accounts: List<SipAccount> = emptyList(),
    val registration: Map<String, RegistrationState> = emptyMap(),
    val unreadMissed: Int = 0,
    val voicemail: Int = 0,
    val hasActiveCall: Boolean = false,
    val dndActive: Boolean = false,
) {
    val activeAccount: SipAccount?
        get() = accounts.firstOrNull { it.id == settings.defaultAccountId } ?: accounts.firstOrNull { it.isDefault } ?: accounts.firstOrNull()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val accounts: AccountRepository,
    registration: RegistrationManager,
    history: CallHistoryRepository,
    voicemail: VoicemailProvider,
    callManager: CallManager,
    private val clock: Clock,
) : ViewModel() {

    val ui: StateFlow<MainUi> = combine(
        settingsStore.settings, accounts.accounts, registration.states,
        combine(history.unreadMissed, voicemail.waiting) { m, v -> m to v.values.sumOf { it.newMessages } },
        callManager.calls,
    ) { s, a, r, (missed, vm), calls ->
        MainUi(
            ready = true, settings = s, accounts = a, registration = r, unreadMissed = missed, voicemail = vm,
            hasActiveCall = calls.any { !it.phase.isTerminal },
            dndActive = IncomingCallFilter.isDndActive(s.dnd, null, clock.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUi())

    /** Biometric lock state (true = UI hidden behind lock). */
    val locked = MutableStateFlow(true)
    private var backgroundAt = 0L

    fun onBackground() { backgroundAt = clock.now() }
    fun shouldRelock(): Boolean = ui.value.settings.appLock && backgroundAt > 0 && clock.now() - backgroundAt > RELOCK_AFTER_MS
    fun unlock() { locked.value = false }

    fun switchAccount(id: String) = viewModelScope.launch { accounts.setDefault(id) }

    private companion object {
        const val RELOCK_AFTER_MS = 60_000L
    }
}

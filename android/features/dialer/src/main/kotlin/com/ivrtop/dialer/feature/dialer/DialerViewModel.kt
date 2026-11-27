package com.ivrtop.dialer.feature.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.data.CallHistoryRepository
import com.ivrtop.dialer.core.data.ContactMatch
import com.ivrtop.dialer.core.data.ContactsRepository
import com.ivrtop.dialer.core.data.FavoritesRepository
import com.ivrtop.dialer.core.datastore.AppSettings
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.RegistrationState
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.telephony.sip.CallManager
import com.ivrtop.dialer.telephony.sip.RegistrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DialerUiState(
    val input: String = "",
    val suggestions: List<ContactMatch> = emptyList(),
    val matchedName: String? = null,
    val accounts: List<SipAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val registration: Map<String, RegistrationState> = emptyMap(),
    val settings: AppSettings = AppSettings(),
) {
    val selectedAccount: SipAccount? get() = accounts.firstOrNull { it.id == selectedAccountId }
        ?: accounts.firstOrNull { it.isDefault } ?: accounts.firstOrNull()
}

sealed interface DialerEvent {
    data class OpenEmergencyDialer(val number: String) : DialerEvent
    data object NeedAccount : DialerEvent
    data class CallStarted(val callId: String) : DialerEvent
    data class Error(val code: AppErrorCode) : DialerEvent
    data class SpeedDialMissing(val digit: Int) : DialerEvent
    data object ChooseAccount : DialerEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class DialerViewModel @Inject constructor(
    private val callManager: CallManager,
    accountsRepo: AccountRepository,
    private val contacts: ContactsRepository,
    private val favorites: FavoritesRepository,
    private val history: CallHistoryRepository,
    private val settingsStore: SettingsStore,
    registration: RegistrationManager,
) : ViewModel() {

    private val input = MutableStateFlow("")
    private val selected = MutableStateFlow<String?>(null)
    private val _events = Channel<DialerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val suggestions = combine(input.debounce(120), contacts.contacts) { q, _ ->
        if (q.length < 2 || PhoneNumbers.isSipUri(q)) emptyList() else contacts.search(q, t9 = true).take(6)
    }

    val state: StateFlow<DialerUiState> = combine(
        input, suggestions, accountsRepo.accounts.map { l -> l.filter { it.enabled && it.allowOutgoing } }, selected,
        combine(registration.states, settingsStore.settings) { r, s -> r to s },
    ) { text, sugg, accs, sel, (reg, settings) ->
        DialerUiState(
            input = text,
            suggestions = sugg,
            matchedName = sugg.firstOrNull { m -> m.phone?.let { PhoneNumbers.sameNumber(it.number, text) } == true }?.contact?.displayName,
            accounts = accs,
            selectedAccountId = sel ?: settings.defaultAccountId,
            registration = reg,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DialerUiState())

    fun onKey(c: Char) { if (input.value.length < 64) input.value += c }

    fun onDelete() { input.value = input.value.dropLast(1) }

    fun onClear() { input.value = "" }

    fun setInput(text: String) { input.value = PhoneNumbers.stripFormatting(text).take(128) }

    fun selectAccount(id: String) { selected.value = id }

    /** Long-press handling: returns true when consumed. */
    fun onLongPress(c: Char): Boolean {
        when (c) {
            '0' -> { input.value += '+'; return true }
            '1' -> {
                viewModelScope.launch {
                    val acc = state.value.selectedAccount ?: return@launch _events.send(DialerEvent.NeedAccount)
                    call(acc.voicemailNumber ?: "*97", acc.id)
                }
                return true
            }
            in '2'..'9' -> {
                if (input.value.isNotEmpty()) return false
                viewModelScope.launch {
                    val fav = favorites.speedDial(c - '0')
                    if (fav == null) _events.send(DialerEvent.SpeedDialMissing(c - '0'))
                    else call(fav.number, fav.preferredAccountId, fav.displayName)
                }
                return true
            }
        }
        return false
    }

    fun onCallPressed() {
        viewModelScope.launch {
            val text = input.value
            if (text.isBlank()) {
                // Android convention: first press recalls the last dialed number.
                history.lastDialed()?.let { input.value = it.number }
                return@launch
            }
            if (state.value.settings.askAccountEachCall && state.value.accounts.size > 1 && selected.value == null) {
                _events.send(DialerEvent.ChooseAccount)
                return@launch
            }
            call(text, state.value.selectedAccount?.id, state.value.matchedName)
        }
    }

    fun callSuggestion(match: ContactMatch) {
        val number = match.phone?.number ?: return
        viewModelScope.launch { call(number, state.value.selectedAccount?.id, match.contact.displayName) }
    }

    private suspend fun call(number: String, accountId: String?, name: String? = null) {
        try {
            val id = callManager.placeCall(number, accountId, name)
            input.value = ""
            _events.send(DialerEvent.CallStarted(id))
        } catch (e: AppException) {
            when (e.error) {
                AppErrorCode.EMERGENCY_NUMBER -> _events.send(DialerEvent.OpenEmergencyDialer(PhoneNumbers.stripFormatting(number)))
                AppErrorCode.NO_ACCOUNT -> _events.send(DialerEvent.NeedAccount)
                else -> _events.send(DialerEvent.Error(e.error))
            }
        } catch (e: Exception) {
            _events.send(DialerEvent.Error(AppErrorCode.UNKNOWN))
        }
    }
}

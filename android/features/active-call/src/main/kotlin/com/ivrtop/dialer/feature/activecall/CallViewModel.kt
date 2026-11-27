package com.ivrtop.dialer.feature.activecall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.data.ContactsRepository
import com.ivrtop.dialer.core.datastore.AppSettings
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.AudioEndpoint
import com.ivrtop.dialer.core.models.CallPhase
import com.ivrtop.dialer.core.models.CallSnapshot
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.telephony.sip.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallUiState(
    val calls: List<CallSnapshot> = emptyList(),
    val focusedId: String? = null,
    val accounts: Map<String, SipAccount> = emptyMap(),
    val photos: Map<String, String?> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    /** Attended transfer in progress: original call → consult call. */
    val consultFor: Pair<String, String>? = null,
) {
    val live: List<CallSnapshot> get() = calls.filter { !it.phase.isTerminal }
    val focused: CallSnapshot?
        get() = calls.firstOrNull { it.callId == focusedId && !it.phase.isTerminal }
            ?: live.firstOrNull { it.phase.isRinging }
            ?: live.firstOrNull { it.phase == CallPhase.ACTIVE }
            ?: live.firstOrNull()
            ?: calls.maxByOrNull { it.endedAtMillis ?: 0 }
    val others: List<CallSnapshot> get() = live.filter { it.callId != focused?.callId }
    fun photoFor(c: CallSnapshot): String? = photos[PhoneNumbers.matchKey(c.remoteNumber)]
}

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callManager: CallManager,
    accounts: AccountRepository,
    contacts: ContactsRepository,
    settings: SettingsStore,
) : ViewModel() {

    private val focused = MutableStateFlow<String?>(null)
    private val consult = MutableStateFlow<Pair<String, String>?>(null)
    private val _errors = Channel<AppErrorCode>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    val state: StateFlow<CallUiState> = combine(
        callManager.calls, focused, accounts.accounts, contacts.contacts, combine(settings.settings, consult) { s, c -> s to c },
    ) { calls, f, accs, cs, (s, c) ->
        val photos = HashMap<String, String?>()
        cs.forEach { contact -> contact.phones.forEach { p -> photos.putIfAbsent(PhoneNumbers.matchKey(p.number), contact.photoUri) } }
        CallUiState(calls, f, accs.associateBy { it.id }, photos, s, c)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CallUiState(calls = callManager.calls.value))

    fun focus(callId: String?) { focused.value = callId }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: AppException) { _errors.send(e.error) } catch (e: Exception) { _errors.send(AppErrorCode.UNKNOWN) }
    }

    fun answer(id: String) = act { callManager.answer(id); focused.value = id }
    fun decline(id: String) = act { callManager.decline(id) }
    fun silence(id: String) = act { callManager.silenceRinging(id) }
    fun hangup(id: String) = act {
        callManager.hangup(id)
        if (consult.value?.second == id) consult.value = null
    }
    fun toggleMute(c: CallSnapshot) = act { callManager.setMuted(c.callId, !c.muted) }
    fun toggleHold(c: CallSnapshot) = act { callManager.setHold(c.callId, !c.onHold) }
    fun dtmf(c: CallSnapshot, key: Char) = act { callManager.sendDtmf(c.callId, key.toString()) }
    fun route(c: CallSnapshot, ep: AudioEndpoint) = act { callManager.setAudioRoute(c.callId, ep) }
    fun swap(toId: String) = act { callManager.swap(toId); focused.value = toId }
    fun merge() = act { callManager.merge(state.value.live.filter { it.phase.isConnected }.map { it.callId }) }

    fun addCall(number: String) = act {
        val id = callManager.placeCall(number)
        focused.value = id
    }

    fun blindTransfer(c: CallSnapshot, target: String) = act { callManager.blindTransfer(c.callId, target) }

    /** Attended transfer step 1: call the target (original goes on hold automatically). */
    fun startConsult(c: CallSnapshot, target: String) = act {
        val consultId = callManager.placeCall(target, c.accountId)
        consult.value = c.callId to consultId
        focused.value = consultId
    }

    /** Step 2: connect the original caller with the consulted party (REFER with Replaces). */
    fun completeTransfer() = act {
        val (orig, cons) = consult.value ?: return@act
        callManager.attendedTransfer(orig, cons)
        consult.value = null
    }
}

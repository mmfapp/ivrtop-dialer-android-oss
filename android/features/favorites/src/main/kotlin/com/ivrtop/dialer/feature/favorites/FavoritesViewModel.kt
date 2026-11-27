package com.ivrtop.dialer.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.data.ContactsRepository
import com.ivrtop.dialer.core.data.FavoritesRepository
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.Favorite
import com.ivrtop.dialer.core.models.PresenceState
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.telephony.sip.CallManager
import com.ivrtop.dialer.telephony.sip.PbxIntegrationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoriteItem(val favorite: Favorite, val photo: String?, val presence: PresenceState?)

data class FavoritesUiState(val items: List<FavoriteItem> = emptyList(), val accounts: List<SipAccount> = emptyList(), val loaded: Boolean = false)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favorites: FavoritesRepository,
    contacts: ContactsRepository,
    accounts: AccountRepository,
    private val pbx: PbxIntegrationProvider,
    private val callManager: CallManager,
) : ViewModel() {

    private val _events = Channel<Pair<String?, AppErrorCode?>>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<FavoritesUiState> = combine(favorites.favorites, contacts.contacts, pbx.presence, accounts.accounts) { favs, cs, pres, accs ->
        val photoByKey = HashMap<String, String?>()
        cs.forEach { c -> c.phones.forEach { p -> photoByKey.putIfAbsent(PhoneNumbers.matchKey(p.number), c.photoUri) } }
        FavoritesUiState(
            items = favs.map { f ->
                FavoriteItem(
                    f,
                    f.photoUri ?: photoByKey[PhoneNumbers.matchKey(f.number)],
                    if (f.blfEnabled) pres[f.number] else null,
                )
            },
            accounts = accs,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    init {
        // Subscribe BLF for extensions flagged by the user (only meaningful on PBX accounts).
        viewModelScope.launch {
            favorites.favorites.collect { list ->
                list.filter { it.blfEnabled && PhoneNumbers.isExtension(it.number) }.forEach { f ->
                    val accId = f.preferredAccountId ?: state.value.accounts.firstOrNull()?.id ?: return@forEach
                    runCatching { pbx.watchPresence(accId, f.number) }
                }
            }
        }
    }

    fun call(f: Favorite) = viewModelScope.launch {
        try {
            _events.send(callManager.placeCall(f.number, f.preferredAccountId, f.displayName) to null)
        } catch (e: AppException) {
            _events.send(null to e.error)
        }
    }

    fun remove(f: Favorite) = viewModelScope.launch { favorites.remove(f.id) }
    fun reorder(ids: List<Long>) = viewModelScope.launch { favorites.reorder(ids) }
    fun setSpeedDial(f: Favorite, digit: Int?) = viewModelScope.launch { favorites.assignSpeedDial(f.id, digit) }
    fun setPreferredAccount(f: Favorite, accountId: String?) = viewModelScope.launch { favorites.update(f.copy(preferredAccountId = accountId)) }
    fun setBlf(f: Favorite, on: Boolean) = viewModelScope.launch { favorites.update(f.copy(blfEnabled = on)) }
}

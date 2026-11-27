package com.ivrtop.dialer.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.data.ContactsRepository
import com.ivrtop.dialer.core.data.FavoritesRepository
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.Contact
import com.ivrtop.dialer.core.models.ContactPhone
import com.ivrtop.dialer.telephony.sip.CallManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val hasPermission: Boolean = false,
    val query: String = "",
    val contacts: List<Contact> = emptyList(),
    val favoriteNumbers: Set<String> = emptySet(),
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repo: ContactsRepository,
    private val favorites: FavoritesRepository,
    private val callManager: CallManager,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val permission = MutableStateFlow(repo.hasPermission())
    private val _events = Channel<Pair<String?, AppErrorCode?>>(Channel.BUFFERED)
    /** first = started call id, second = error */
    val events = _events.receiveAsFlow()

    val state: StateFlow<ContactsUiState> = combine(repo.contacts, query, permission, favorites.favorites) { all, q, perm, favs ->
        val list = if (q.isBlank()) all else repo.search(q, t9 = false).map { it.contact }
        ContactsUiState(perm, q, list, favs.map { it.number }.toSet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState(hasPermission = repo.hasPermission()))

    fun setQuery(q: String) { query.value = q }

    fun onPermissionResult() {
        permission.value = repo.hasPermission()
        repo.onPermissionChanged()
    }

    fun call(contact: Contact, phone: ContactPhone) = viewModelScope.launch {
        try {
            _events.send(callManager.placeCall(phone.number, null, contact.displayName) to null)
        } catch (e: AppException) {
            _events.send(null to e.error)
        }
    }

    fun toggleFavorite(contact: Contact, phone: ContactPhone) = viewModelScope.launch {
        val existing = state.value.favoriteNumbers.contains(phone.number)
        if (existing) {
            favorites.favorites.first().firstOrNull { it.number == phone.number }?.let { favorites.remove(it.id) }
        } else {
            favorites.add(contact.displayName, phone.number, contact.id, contact.photoUri)
        }
    }
}

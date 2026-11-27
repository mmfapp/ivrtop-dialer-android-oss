package com.ivrtop.dialer.feature.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.data.BlockRepository
import com.ivrtop.dialer.core.data.CallHistoryRepository
import com.ivrtop.dialer.core.data.CallLogGroup
import com.ivrtop.dialer.core.data.ContactsRepository
import com.ivrtop.dialer.core.models.AppErrorCode
import com.ivrtop.dialer.core.models.AppException
import com.ivrtop.dialer.core.models.BlockKind
import com.ivrtop.dialer.core.models.CallLogType
import com.ivrtop.dialer.core.models.Contact
import com.ivrtop.dialer.core.models.SipAccount
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecentsFilter { ALL, MISSED, INCOMING, OUTGOING, BLOCKED }

data class RecentRow(val group: CallLogGroup, val contact: Contact?)

data class RecentsUiState(
    val rows: List<RecentRow> = emptyList(),
    val filter: RecentsFilter = RecentsFilter.ALL,
    val accountFilter: String? = null,
    val query: String = "",
    val selection: Set<Long> = emptySet(),
    val accounts: Map<String, SipAccount> = emptyMap(),
    val loading: Boolean = true,
)

sealed interface RecentsEvent {
    data class CallStarted(val callId: String) : RecentsEvent
    data class Error(val code: AppErrorCode) : RecentsEvent
    data class Export(val csv: String) : RecentsEvent
}

@HiltViewModel
class RecentsViewModel @Inject constructor(
    private val history: CallHistoryRepository,
    private val contacts: ContactsRepository,
    private val blocks: BlockRepository,
    private val callManager: CallManager,
    accounts: AccountRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(RecentsFilter.ALL)
    private val accountFilter = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val _events = Channel<RecentsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<RecentsUiState> = combine(
        history.groups, contacts.contacts, combine(filter, accountFilter, query) { f, a, q -> Triple(f, a, q) },
        selection, accounts.accounts,
    ) { groups, contactList, (f, a, q), sel, accs ->
        val index = HashMap<String, Contact>()
        contactList.forEach { c -> c.phones.forEach { p -> index.putIfAbsent(PhoneNumbers.matchKey(p.number), c) } }
        val rows = groups.asSequence()
            .filter { g ->
                when (f) {
                    RecentsFilter.ALL -> true
                    RecentsFilter.MISSED -> g.latest.type == CallLogType.MISSED
                    RecentsFilter.INCOMING -> g.latest.type == CallLogType.INCOMING
                    RecentsFilter.OUTGOING -> g.latest.type == CallLogType.OUTGOING
                    RecentsFilter.BLOCKED -> g.latest.type == CallLogType.BLOCKED
                }
            }
            .filter { g -> a == null || g.latest.accountId == a }
            .map { g -> RecentRow(g, index[PhoneNumbers.matchKey(g.latest.number)]) }
            .filter { r ->
                q.isBlank() || r.group.latest.number.contains(q.filter { it.isDigit() }.ifEmpty { "\u0000" }) ||
                    (r.contact?.displayName ?: r.group.latest.displayName).orEmpty().contains(q, ignoreCase = true)
            }
            .toList()
        RecentsUiState(rows, f, a, q, sel, accs.associateBy { it.id }, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecentsUiState())

    init {
        viewModelScope.launch { history.markMissedRead() }
    }

    fun setFilter(f: RecentsFilter) { filter.value = f }
    fun setAccountFilter(id: String?) { accountFilter.value = id }
    fun setQuery(q: String) { query.value = q }

    fun toggleSelect(row: RecentRow) {
        val ids = row.group.entries.map { it.id }.toSet()
        selection.update { if (it.containsAll(ids)) it - ids else it + ids }
    }
    fun clearSelection() { selection.value = emptySet() }

    fun deleteSelected() = viewModelScope.launch {
        history.delete(selection.value.toList())
        selection.value = emptySet()
    }

    fun deleteRow(row: RecentRow) = viewModelScope.launch { history.delete(row.group.entries.map { it.id }) }

    fun clearAll() = viewModelScope.launch { history.clear() }

    fun block(number: String) = viewModelScope.launch { blocks.add(number, BlockKind.EXACT) }

    fun call(row: RecentRow) = viewModelScope.launch {
        try {
            val id = callManager.placeCall(row.group.latest.number, row.group.latest.accountId, row.contact?.displayName ?: row.group.latest.displayName)
            _events.send(RecentsEvent.CallStarted(id))
        } catch (e: AppException) {
            _events.send(RecentsEvent.Error(e.error))
        }
    }

    fun export() = viewModelScope.launch {
        _events.send(RecentsEvent.Export(history.toCsv(history.entries.first())))
    }
}

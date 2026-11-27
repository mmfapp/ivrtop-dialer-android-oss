package com.ivrtop.dialer.feature.voicemail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.data.AccountRepository
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.AccountColorDot
import com.ivrtop.dialer.core.designsystem.component.Formatters
import com.ivrtop.dialer.core.designsystem.component.InfoBanner
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.StatusTone
import com.ivrtop.dialer.core.models.MessageWaiting
import com.ivrtop.dialer.core.models.SipAccount
import com.ivrtop.dialer.core.models.VoicemailMessage
import com.ivrtop.dialer.telephony.sip.CallManager
import com.ivrtop.dialer.telephony.sip.VoicemailProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoicemailUi(
    val accounts: List<SipAccount> = emptyList(),
    val waiting: Map<String, MessageWaiting> = emptyMap(),
    val messages: List<VoicemailMessage> = emptyList(),
)

@HiltViewModel
class VoicemailViewModel @Inject constructor(
    private val provider: VoicemailProvider,
    private val callManager: CallManager,
    accounts: AccountRepository,
) : ViewModel() {
    val state = combine(accounts.accounts, provider.waiting, provider.messages) { a, w, m -> VoicemailUi(a, w, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoicemailUi())

    fun listen(acc: SipAccount, onCall: (String) -> Unit) = viewModelScope.launch {
        val number = provider.accessNumber(acc.id) ?: return@launch
        runCatching { callManager.placeCall(number, acc.id) }.onSuccess(onCall)
    }

    fun callBack(m: VoicemailMessage, onCall: (String) -> Unit) = viewModelScope.launch {
        runCatching { callManager.placeCall(m.from, m.accountId, m.fromName) }.onSuccess(onCall)
        provider.markRead(m.id)
    }
}

@Composable
fun VoicemailScreen(onCallStarted: (String) -> Unit, vm: VoicemailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(s.accounts.filter { it.enabled }, key = { it.id }) { acc ->
            val count = s.waiting[acc.id]?.newMessages ?: 0
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccountColorDot(acc.colorArgb)
                        Text(acc.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Voicemail, null, tint = if (count > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
                    }
                    Text(
                        if (count > 0) stringResource(R.string.voicemail_new, count) else stringResource(R.string.voicemail_none),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Button(onClick = { vm.listen(acc, onCallStarted) }) {
                        Icon(Icons.Rounded.Call, null)
                        Text(stringResource(R.string.voicemail_call), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (s.messages.isEmpty()) {
            item { InfoBanner(stringResource(R.string.voicemail_api_note), tone = StatusTone.OK) }
        } else {
            items(s.messages, key = { it.id }) { m ->
                Card(onClick = { vm.callBack(m, onCallStarted) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.fromName ?: PhoneNumbers.format(m.from), style = MaterialTheme.typography.titleMedium)
                            LtrText(Formatters.dateTime(m.receivedAtMillis) + " · " + Formatters.duration(m.durationSec.toLong()), style = MaterialTheme.typography.bodySmall)
                            m.transcript?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3) }
                        }
                        if (m.isNew) AccountColorDot(0xFFA3418F)
                    }
                }
            }
        }
    }
}

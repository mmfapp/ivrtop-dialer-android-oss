package com.ivrtop.dialer.feature.dialer

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.AccountColorDot
import com.ivrtop.dialer.core.designsystem.component.Avatar
import com.ivrtop.dialer.core.designsystem.component.BigRoundButton
import com.ivrtop.dialer.core.designsystem.component.DialPad
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.errorMessageRes
import com.ivrtop.dialer.core.designsystem.theme.IvrtopThemeExt
import com.ivrtop.dialer.core.designsystem.theme.NumberStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    onCallStarted: (String) -> Unit,
    onSetupAccount: () -> Unit,
    onAddContact: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialNumber: String? = null,
    vm: DialerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var emergencyNumber by remember { mutableStateOf<String?>(null) }
    var needAccount by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }

    LaunchedEffect(initialNumber) { initialNumber?.let(vm::setInput) }
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is DialerEvent.CallStarted -> onCallStarted(ev.callId)
                is DialerEvent.OpenEmergencyDialer -> emergencyNumber = ev.number
                DialerEvent.NeedAccount -> needAccount = true
                DialerEvent.ChooseAccount -> accountMenu = true
                is DialerEvent.Error -> Toast.makeText(context, context.getString(errorMessageRes(ev.code)), Toast.LENGTH_LONG).show()
                is DialerEvent.SpeedDialMissing -> Toast.makeText(context, context.getString(R.string.speed_dial_unassigned, ev.digit), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Suggestions (T9 / number match)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
            items(state.suggestions, key = { it.contact.id + (it.phone?.number ?: "") }) { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.callSuggestion(m) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(m.contact.displayName, m.contact.thumbnailUri, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(m.contact.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        m.phone?.let { p ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                p.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                LtrText(PhoneNumbers.format(p.number), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.action_call), tint = IvrtopThemeExt.colors.callGreen)
                }
            }
        }

        // Number display
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            if (state.input.isEmpty()) {
                Text(stringResource(R.string.dialer_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            } else {
                val display = PhoneNumbers.format(state.input)
                LtrText(
                    text = display,
                    style = NumberStyle.copy(fontSize = when { display.length > 16 -> 26.sp; display.length > 12 -> 32.sp; else -> 38.sp }),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .combinedClickable(onClick = {}, onLongClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(state.input))
                            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                        }),
                )
            }
        }
        AnimatedVisibility(state.matchedName != null, enter = fadeIn(), exit = fadeOut()) {
            Text(state.matchedName.orEmpty(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }

        // Action row: add contact · account selector · paste/backspace
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAddContact(state.input) }, enabled = state.input.isNotEmpty()) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.action_add_contact))
            }
            Spacer(Modifier.weight(1f))
            state.selectedAccount?.let { acc ->
                Box {
                    AssistChip(
                        onClick = { if (state.accounts.size > 1) accountMenu = true },
                        label = { Text(stringResource(R.string.call_from, acc.label), maxLines = 1) },
                        leadingIcon = { AccountColorDot(acc.colorArgb) },
                    )
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        state.accounts.forEach { a ->
                            DropdownMenuItem(
                                text = { Text(a.label) },
                                leadingIcon = { AccountColorDot(a.colorArgb) },
                                onClick = { vm.selectAccount(a.id); accountMenu = false },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.input.isEmpty()) {
                IconButton(onClick = { clipboard.getText()?.text?.let(vm::setInput) }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.action_paste))
                }
            } else {
                Box(
                    Modifier.size(48.dp).combinedClickable(onClick = vm::onDelete, onLongClick = vm::onClear, onClickLabel = stringResource(R.string.action_delete_digit), onLongClickLabel = stringResource(R.string.action_clear)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = stringResource(R.string.action_delete_digit))
                }
            }
        }

        DialPad(
            onKey = vm::onKey,
            onLongPress = vm::onLongPress,
            showHebrew = state.settings.showHebrewLettersOnDialpad,
            haptics = state.settings.haptics,
        )
        Spacer(Modifier.height(16.dp))
        BigRoundButton(
            icon = Icons.Rounded.Call,
            contentDescription = stringResource(R.string.action_call),
            color = IvrtopThemeExt.colors.callGreen,
            onClick = vm::onCallPressed,
        )
        Spacer(Modifier.height(16.dp))
    }

    emergencyNumber?.let { number ->
        AlertDialog(
            onDismissRequest = { emergencyNumber = null },
            title = { Text(stringResource(R.string.emergency_title)) },
            text = { Text(stringResource(R.string.emergency_body)) },
            confirmButton = {
                TextButton(onClick = {
                    emergencyNumber = null
                    // ACTION_DIAL needs no permission and lets the system dialer place the cellular call.
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.emergency_btn)) }
            },
            dismissButton = { TextButton(onClick = { emergencyNumber = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (needAccount) {
        AlertDialog(
            onDismissRequest = { needAccount = false },
            title = { Text(stringResource(R.string.no_account_title)) },
            text = { Text(stringResource(R.string.no_account_body)) },
            confirmButton = { TextButton(onClick = { needAccount = false; onSetupAccount() }) { Text(stringResource(R.string.no_account_btn)) } },
            dismissButton = { TextButton(onClick = { needAccount = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

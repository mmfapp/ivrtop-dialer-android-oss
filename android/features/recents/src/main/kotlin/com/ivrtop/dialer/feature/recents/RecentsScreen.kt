package com.ivrtop.dialer.feature.recents

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.AccountColorDot
import com.ivrtop.dialer.core.designsystem.component.Avatar
import com.ivrtop.dialer.core.designsystem.component.EmptyState
import com.ivrtop.dialer.core.designsystem.component.Formatters
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.SectionHeader
import com.ivrtop.dialer.core.designsystem.component.endReasonRes
import com.ivrtop.dialer.core.designsystem.component.errorMessageRes
import com.ivrtop.dialer.core.designsystem.component.relativeTime
import com.ivrtop.dialer.core.designsystem.theme.IvrtopThemeExt
import com.ivrtop.dialer.core.models.CallLogType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    onCallStarted: (String) -> Unit,
    onAddContact: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: RecentsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var detail by remember { mutableStateOf<RecentRow?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is RecentsEvent.CallStarted -> onCallStarted(ev.callId)
                is RecentsEvent.Error -> Toast.makeText(context, errorMessageRes(ev.code), Toast.LENGTH_LONG).show()
                is RecentsEvent.Export -> context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_TEXT, ev.csv), null),
                )
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        // Toolbar: selection mode or filters
        if (state.selection.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::clearSelection) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
                Text(stringResource(R.string.selected_count, state.selection.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.deleteSelected() }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete_selected)) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecentsFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { vm.setFilter(f) },
                            label = {
                                Text(stringResource(when (f) {
                                    RecentsFilter.ALL -> R.string.filter_all
                                    RecentsFilter.MISSED -> R.string.filter_missed
                                    RecentsFilter.INCOMING -> R.string.filter_incoming
                                    RecentsFilter.OUTGOING -> R.string.filter_outgoing
                                    RecentsFilter.BLOCKED -> R.string.filter_blocked
                                }))
                            },
                        )
                    }
                    if (state.accounts.size > 1) {
                        state.accounts.values.forEach { a ->
                            FilterChip(
                                selected = state.accountFilter == a.id,
                                onClick = { vm.setAccountFilter(if (state.accountFilter == a.id) null else a.id) },
                                label = { Text(a.label) },
                                leadingIcon = { AccountColorDot(a.colorArgb) },
                            )
                        }
                    }
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_history)) }, leadingIcon = { Icon(Icons.Rounded.IosShare, null) }, onClick = { menu = false; vm.export() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_all_history)) }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; confirmClear = true })
                }
            }
        }

        if (!state.loading && state.rows.isEmpty()) {
            EmptyState(stringResource(R.string.recents_empty), stringResource(R.string.recents_empty_body), Icons.Rounded.History)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            var lastBucket = -1
            state.rows.forEach { row ->
                val bucket = Formatters.dayBucket(row.group.latest.startedAtMillis)
                if (bucket != lastBucket) {
                    lastBucket = bucket
                    item(key = "h$bucket") {
                        SectionHeader(stringResource(when (bucket) { 0 -> R.string.today; 1 -> R.string.yesterday; else -> R.string.older }))
                    }
                }
                item(key = row.group.key) {
                    RecentItem(
                        row = row,
                        selected = row.group.entries.all { it.id in state.selection },
                        accountColor = state.accounts[row.group.latest.accountId]?.colorArgb,
                        showAccount = state.accounts.size > 1,
                        onClick = { if (state.selection.isNotEmpty()) vm.toggleSelect(row) else detail = row },
                        onLongClick = { vm.toggleSelect(row) },
                        onCall = { vm.call(row) },
                    )
                }
            }
        }
    }

    detail?.let { row ->
        val e = row.group.latest
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(row.contact?.displayName ?: e.displayName, row.contact?.photoUri, size = 56.dp)
                    Column {
                        Text(row.contact?.displayName ?: e.displayName ?: PhoneNumbers.format(e.number), style = MaterialTheme.typography.titleLarge)
                        LtrText(PhoneNumbers.format(e.number), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                row.group.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TypeIcon(entry.type)
                        Text(Formatters.dateTime(entry.startedAtMillis), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (entry.durationSec > 0) Text(stringResource(R.string.duration, Formatters.duration(entry.durationSec.toLong())), style = MaterialTheme.typography.bodySmall)
                        else if (entry.endReason != null) Text(stringResource(endReasonRes(entry.endReason)), style = MaterialTheme.typography.bodySmall)
                    }
                    entry.sipStatus?.takeIf { it >= 300 }?.let { Text(stringResource(R.string.sip_status_detail, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                    state.accounts[entry.accountId]?.let { Text(stringResource(R.string.via_account, it.label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                }
                HorizontalDivider()
                SheetAction(Icons.Rounded.Call, stringResource(R.string.action_call)) { detail = null; vm.call(row) }
                if (row.contact == null) SheetAction(Icons.Rounded.PersonAdd, stringResource(R.string.action_add_contact)) { detail = null; onAddContact(e.number) }
                SheetAction(Icons.Rounded.ContentCopy, stringResource(R.string.copy_number)) {
                    clipboard.setText(AnnotatedString(e.number)); detail = null
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                }
                SheetAction(Icons.Rounded.Block, stringResource(R.string.block_number)) { vm.block(e.number); detail = null }
                SheetAction(Icons.Rounded.Delete, stringResource(R.string.delete)) { vm.deleteRow(row); detail = null }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearAll() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentItem(
    row: RecentRow,
    selected: Boolean,
    accountColor: Long?,
    showAccount: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCall: () -> Unit,
) {
    val e = row.group.latest
    val missed = e.type == CallLogType.MISSED
    val name = row.contact?.displayName ?: e.displayName
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(name, row.contact?.thumbnailUri, size = 46.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (name != null) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        color = if (missed) IvrtopThemeExt.colors.missed else MaterialTheme.colorScheme.onSurface)
                } else {
                    LtrText(PhoneNumbers.format(e.number).ifBlank { stringResource(R.string.private_number) }, style = MaterialTheme.typography.titleMedium,
                        color = if (missed) IvrtopThemeExt.colors.missed else MaterialTheme.colorScheme.onSurface)
                }
                if (row.group.count > 1) Text("(${row.group.count})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeIcon(e.type, small = true)
                if (showAccount && accountColor != null) AccountColorDot(accountColor, size = 8.dp)
                Text(relativeTime(e.startedAtMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.action_call), tint = IvrtopThemeExt.colors.callGreen)
        }
    }
}

@Composable
private fun TypeIcon(type: CallLogType, small: Boolean = false) {
    val (icon, desc) = when (type) {
        CallLogType.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived to R.string.type_incoming
        CallLogType.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade to R.string.type_outgoing
        CallLogType.MISSED -> Icons.AutoMirrored.Rounded.CallMissed to R.string.type_missed
        CallLogType.REJECTED -> Icons.Rounded.CallEnd to R.string.type_rejected
        CallLogType.FAILED -> Icons.Rounded.CallEnd to R.string.type_failed
        CallLogType.CANCELLED -> Icons.AutoMirrored.Rounded.CallMade to R.string.type_cancelled
        CallLogType.BLOCKED -> Icons.Rounded.Block to R.string.type_blocked
    }
    val tint = when (type) {
        CallLogType.MISSED, CallLogType.REJECTED, CallLogType.BLOCKED -> IvrtopThemeExt.colors.missed
        CallLogType.INCOMING -> MaterialTheme.colorScheme.secondary
        else -> IvrtopThemeExt.colors.callGreen
    }
    val label = stringResource(desc)
    Icon(icon as ImageVector, contentDescription = null, tint = tint, modifier = Modifier.size(if (small) 16.dp else 20.dp).semantics { contentDescription = label })
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

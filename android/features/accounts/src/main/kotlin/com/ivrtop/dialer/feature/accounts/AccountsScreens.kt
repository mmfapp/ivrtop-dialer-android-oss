@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ivrtop.dialer.feature.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.AccountColorDot
import com.ivrtop.dialer.core.designsystem.component.InfoBanner
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.SectionHeader
import com.ivrtop.dialer.core.designsystem.component.StatusPill
import com.ivrtop.dialer.core.designsystem.component.StatusTone
import com.ivrtop.dialer.core.designsystem.component.errorMessageRes
import com.ivrtop.dialer.core.models.DtmfMethod
import com.ivrtop.dialer.core.models.ProbeStep
import com.ivrtop.dialer.core.models.SipTransport
import com.ivrtop.dialer.core.models.SrtpMode

private val ACCOUNT_COLORS = listOf(0xFF0E78A6, 0xFF10B3B5, 0xFF5B5FA8, 0xFFA3418F, 0xFF1F9D55, 0xFFE8A317, 0xFFE5484D, 0xFF455A64)

@Composable
fun AccountsScreen(
    onAddViaSetup: () -> Unit,
    onAddManual: () -> Unit,
    onEdit: (String) -> Unit,
    vm: AccountsViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountRow?>(null) }

    Scaffold(
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.add_account)) })
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_via_link) + " / QR") }, onClick = { addMenu = false; onAddViaSetup() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_manual)) }, onClick = { addMenu = false; onAddManual() })
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = { it.account.id }) { row ->
                val a = row.account
                var menu by remember { mutableStateOf(false) }
                Card(onClick = { onEdit(a.id) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AccountColorDot(a.colorArgb, size = 14.dp)
                            Column(Modifier.weight(1f)) {
                                Text(a.label, style = MaterialTheme.typography.titleMedium)
                                LtrText("${a.username}@${a.domain}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = a.enabled, onCheckedChange = { vm.setEnabled(a.id, it) })
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more)) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.account_reconnect)) }, onClick = { menu = false; vm.reconnect(a.id) })
                                    if (!a.isDefault) DropdownMenuItem(text = { Text(stringResource(R.string.account_default)) }, onClick = { menu = false; vm.setDefault(a.id) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = row })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(row.state, dnd = a.dnd)
                            if (a.isDefault) Text(stringResource(R.string.account_default), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (a.provisioning?.managed == true) Text(stringResource(R.string.account_managed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    deleting?.let { row ->
        var alsoHistory by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = {
                Column {
                    Text(stringResource(R.string.account_delete_confirm, row.account.label))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { alsoHistory = !alsoHistory }) {
                        Checkbox(checked = alsoHistory, onCheckedChange = { alsoHistory = it })
                        Text(stringResource(R.string.account_delete_history))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.delete(row.account, alsoHistory); deleting = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun AccountEditScreen(onDone: () -> Unit, vm: AccountEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var advanced by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val locked = f.managed && !f.allowEdit

    LaunchedEffect(status) { if (status is EditStatus.Saved) onDone() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (locked) InfoBanner(stringResource(R.string.managed_field_locked), tone = StatusTone.OK)

        Field(f.displayName, R.string.field_display_name, enabled = true) { v -> vm.update { it.copy(displayName = v) } }
        Field(f.phoneNumber, R.string.field_phone_number, enabled = !locked, ltr = true, keyboard = KeyboardType.Phone) { v -> vm.update { it.copy(phoneNumber = v) } }
        Field(f.domain, R.string.field_domain, enabled = !locked, ltr = true, keyboard = KeyboardType.Uri) { v -> vm.update { it.copy(domain = v.trim()) } }
        Field(f.username, R.string.field_username, enabled = !locked, ltr = true) { v -> vm.update { it.copy(username = v.trim()) } }
        OutlinedTextField(
            value = f.password,
            onValueChange = { v -> vm.update { it.copy(password = v) } },
            label = { Text(stringResource(R.string.field_password)) },
            placeholder = { if (f.passwordStored) Text("••••••••") },
            singleLine = true,
            enabled = !locked,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.field_transport), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = f.transport == null, onClick = { vm.update { it.copy(transport = null) } }, label = { Text(stringResource(R.string.auto_detect)) }, enabled = !locked)
            listOf(SipTransport.TLS, SipTransport.TCP, SipTransport.UDP).forEach { t ->
                FilterChip(selected = f.transport == t, onClick = { vm.update { it.copy(transport = t) } }, label = { Text(t.wire.uppercase()) }, enabled = !locked)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.allow_insecure), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = f.allowInsecure, onCheckedChange = { v -> vm.update { it.copy(allowInsecure = v) } }, enabled = !locked)
        }
        AnimatedVisibility(f.allowInsecure || f.transport?.encrypted == false) {
            InfoBanner(stringResource(R.string.allow_insecure_warning))
        }

        SectionHeader(stringResource(R.string.field_color), Modifier.padding(0.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ACCOUNT_COLORS.forEach { c ->
                Box(
                    Modifier.size(32.dp).background(Color(c), CircleShape)
                        .border(if (f.colorArgb == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { vm.update { it.copy(colorArgb = c) } },
                )
            }
        }

        ToggleRow(R.string.field_enabled, f.enabled) { v -> vm.update { it.copy(enabled = v) } }
        ToggleRow(R.string.field_incoming, f.incoming) { v -> vm.update { it.copy(incoming = v) } }
        ToggleRow(R.string.field_outgoing, f.outgoing) { v -> vm.update { it.copy(outgoing = v) } }
        ToggleRow(R.string.field_account_dnd, f.accountDnd) { v -> vm.update { it.copy(accountDnd = v) } }

        TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.advanced_settings)) }
        AnimatedVisibility(advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(f.authUsername, R.string.field_auth_username, !locked, ltr = true) { v -> vm.update { it.copy(authUsername = v) } }
                Field(f.registrar, R.string.field_registrar, !locked, ltr = true) { v -> vm.update { it.copy(registrar = v) } }
                Field(f.proxy, R.string.field_proxy, !locked, ltr = true) { v -> vm.update { it.copy(proxy = v) } }
                Field(f.realm, R.string.field_realm, !locked, ltr = true) { v -> vm.update { it.copy(realm = v) } }
                Field(f.port, R.string.field_port, !locked, ltr = true, keyboard = KeyboardType.Number) { v -> vm.update { it.copy(port = v.filter(Char::isDigit).take(5)) } }
                Field(f.expiry, R.string.field_expiry, !locked, ltr = true, keyboard = KeyboardType.Number) { v -> vm.update { it.copy(expiry = v.filter(Char::isDigit)) } }
                Field(f.keepAlive, R.string.field_keepalive, !locked, ltr = true, keyboard = KeyboardType.Number) { v -> vm.update { it.copy(keepAlive = v.filter(Char::isDigit)) } }
                Field(f.stun, R.string.field_stun, !locked, ltr = true) { v -> vm.update { it.copy(stun = v) } }
                Field(f.turn, R.string.field_turn, !locked, ltr = true) { v -> vm.update { it.copy(turn = v) } }
                Field(f.turnUser, R.string.field_turn_user, !locked, ltr = true) { v -> vm.update { it.copy(turnUser = v) } }
                Field(f.turnPassword, R.string.field_turn_password, !locked, ltr = true) { v -> vm.update { it.copy(turnPassword = v) } }
                Field(f.voicemail, R.string.field_voicemail, true, ltr = true) { v -> vm.update { it.copy(voicemail = v) } }
                ToggleRow(R.string.field_ice, f.ice) { v -> vm.update { it.copy(ice = v) } }
                ToggleRow(R.string.field_push, f.push) { v -> vm.update { it.copy(push = v) } }
                Text(stringResource(R.string.field_srtp), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SrtpMode.DISABLED to R.string.srtp_disabled, SrtpMode.OPTIONAL to R.string.srtp_optional, SrtpMode.MANDATORY to R.string.srtp_mandatory).forEach { (m, l) ->
                        FilterChip(selected = f.srtp == m, onClick = { vm.update { it.copy(srtp = m) } }, label = { Text(stringResource(l)) }, enabled = !locked)
                    }
                }
                Text(stringResource(R.string.field_dtmf), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DtmfMethod.RFC4733 to "RFC 4733", DtmfMethod.SIP_INFO to "SIP INFO", DtmfMethod.INBAND to "In-band").forEach { (m, l) ->
                        FilterChip(selected = f.dtmf == m, onClick = { vm.update { it.copy(dtmf = m) } }, label = { Text(l) })
                    }
                }
            }
        }

        when (val s = status) {
            is EditStatus.Testing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(when (s.step) {
                    ProbeStep.LOCATE_SERVER -> R.string.step_locate
                    ProbeStep.CHECK_CREDENTIALS -> R.string.step_credentials
                    else -> R.string.step_connection
                }))
            }
            is EditStatus.Error -> {
                var tech by remember { mutableStateOf(false) }
                InfoBanner(stringResource(errorMessageRes(s.code)), tone = StatusTone.ERROR) {
                    TextButton(onClick = { tech = !tech }) { Text(stringResource(R.string.technical_details)) }
                }
                if (tech) LtrText(s.technical.orEmpty(), maxLines = 10, style = MaterialTheme.typography.bodySmall)
            }
            else -> Unit
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::testAndSave, enabled = f.valid && status !is EditStatus.Testing, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.setup_confirm_btn))
        }
        if (f.id != null) {
            OutlinedButton(onClick = vm::saveOnly, enabled = f.valid && status !is EditStatus.Testing, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Field(value: String, label: Int, enabled: Boolean, ltr: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        enabled = enabled,
        textStyle = if (ltr) MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ivrtop.dialer.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivrtop.dialer.core.datastore.AppLanguage
import com.ivrtop.dialer.core.datastore.ConnectivityMode
import com.ivrtop.dialer.core.datastore.StartTab
import com.ivrtop.dialer.core.datastore.ThemeMode
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onAccounts: () -> Unit,
    onBlocking: () -> Unit,
    onDnd: () -> Unit,
    onSpeedDial: () -> Unit,
    onVoicemail: () -> Unit,
    onDiagnostics: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val s = ui.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        // ---------------- Accounts
        item { SectionHeader(stringResource(R.string.settings_accounts)) }
        item { NavRow(stringResource(R.string.accounts_title), "${ui.accounts.size}", onAccounts) }
        item { NavRow(stringResource(R.string.voicemail_title), null, onVoicemail) }

        // ---------------- Calls
        item { SectionHeader(stringResource(R.string.settings_calls)) }
        if (ui.accounts.size > 1) item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.set_default_account), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.accounts.forEach { a ->
                        FilterChip(s.defaultAccountId == a.id || (s.defaultAccountId == null && a.isDefault), { vm.update { it.copy(defaultAccountId = a.id) } }, { Text(a.label) })
                    }
                }
            }
        }
        if (ui.accounts.size > 1) item { Toggle(stringResource(R.string.set_ask_each_call), s.askAccountEachCall) { v -> vm.update { it.copy(askAccountEachCall = v) } } }
        item { Toggle(stringResource(R.string.set_call_waiting), s.callWaiting) { v -> vm.update { it.copy(callWaiting = v) } } }
        item { Toggle(stringResource(R.string.set_vibrate), s.vibrateOnRing) { v -> vm.update { it.copy(vibrateOnRing = v) } } }
        item { Toggle(stringResource(R.string.set_dialpad_tones), s.dialpadTones) { v -> vm.update { it.copy(dialpadTones = v) } } }
        item { Toggle(stringResource(R.string.set_haptics), s.haptics) { v -> vm.update { it.copy(haptics = v) } } }
        item { Toggle(stringResource(R.string.set_proximity), s.proximitySensor) { v -> vm.update { it.copy(proximitySensor = v) } } }
        item { NavRow(stringResource(R.string.set_speed_dial), null, onSpeedDial) }
        item { NavRow(stringResource(R.string.set_blocking), null, onBlocking) }
        item { NavRow(stringResource(R.string.set_dnd), if (s.dnd.enabledGlobally) stringResource(R.string.on) else null, onDnd) }

        // ---------------- Audio
        item { SectionHeader(stringResource(R.string.settings_audio)) }
        item { Toggle(stringResource(R.string.set_echo), s.echoCancellation) { v -> vm.update { it.copy(echoCancellation = v) } } }
        item { Toggle(stringResource(R.string.set_noise), s.noiseSuppression) { v -> vm.update { it.copy(noiseSuppression = v) } } }
        item { Toggle(stringResource(R.string.set_agc), s.autoGainControl) { v -> vm.update { it.copy(autoGainControl = v) } } }

        // ---------------- Network
        item { SectionHeader(stringResource(R.string.settings_network)) }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.set_connectivity_mode), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
                listOf(
                    Triple(ConnectivityMode.PERSISTENT, R.string.mode_persistent, R.string.mode_persistent_desc),
                    Triple(ConnectivityMode.FOREGROUND_ONLY, R.string.mode_foreground, R.string.mode_foreground_desc),
                ).forEach { (mode, title, desc) ->
                    Row(Modifier.fillMaxWidth().clickable { vm.update { it.copy(connectivityMode = mode) } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.connectivityMode == mode, { vm.update { it.copy(connectivityMode = mode) } })
                        Column {
                            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Toggle(stringResource(R.string.set_wifi_only), s.wifiOnly) { v -> vm.update { it.copy(wifiOnly = v) } } }
        item { NavRow(stringResource(R.string.set_connection_test), null, onDiagnostics) }

        // ---------------- Display
        item { SectionHeader(stringResource(R.string.settings_display)) }
        item {
            Chips(stringResource(R.string.set_theme), listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark), s.theme) { m ->
                vm.update { it.copy(theme = m) }
            }
        }
        item { Toggle(stringResource(R.string.set_dynamic_color), s.dynamicColor) { v -> vm.update { it.copy(dynamicColor = v) } } }
        item {
            Chips(stringResource(R.string.set_language), listOf(AppLanguage.SYSTEM to R.string.lang_system, AppLanguage.HEBREW to R.string.lang_he, AppLanguage.ENGLISH to R.string.lang_en), s.language, vm::setLanguage)
        }
        item {
            Chips(stringResource(R.string.set_start_tab), listOf(StartTab.DIALER to R.string.tab_dialer, StartTab.RECENTS to R.string.tab_recents, StartTab.FAVORITES to R.string.tab_favorites, StartTab.CONTACTS to R.string.tab_contacts), s.startTab) { t ->
                vm.update { it.copy(startTab = t) }
            }
        }
        item { Toggle(stringResource(R.string.set_hebrew_letters), s.showHebrewLettersOnDialpad) { v -> vm.update { it.copy(showHebrewLettersOnDialpad = v) } } }
        item { Toggle(stringResource(R.string.set_reduce_motion), s.reduceMotion) { v -> vm.update { it.copy(reduceMotion = v) } } }

        // ---------------- Security
        item { SectionHeader(stringResource(R.string.settings_security)) }
        item { Toggle(stringResource(R.string.set_app_lock), s.appLock) { v -> vm.update { it.copy(appLock = v) } } }
        item {
            NavRow(stringResource(R.string.set_permissions), null) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }
        item { NavRow(stringResource(R.string.set_privacy), null) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) } }
        item { NavRow(stringResource(R.string.set_delete_data), null) { confirmWipe = true } }

        // ---------------- Support
        item { SectionHeader(stringResource(R.string.settings_support)) }
        item { NavRow(stringResource(R.string.set_diagnostics), null, onDiagnostics) }
        item {
            NavRow(stringResource(R.string.set_export_report), null) {
                scope.launch {
                    val report = vm.technicalReport()
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), null))
                }
            }
        }
        item { Toggle(stringResource(R.string.set_verbose_logs), s.verboseSipLogging) { v -> vm.update { it.copy(verboseSipLogging = v) } } }
        item { NavRow(stringResource(R.string.set_contact_support), null) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL))) } }
        item { NavRow(stringResource(R.string.set_guide), null) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL))) } }
        item { NavRow(stringResource(R.string.set_website), "ivr.top") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL))) } }
        item { NavRow(stringResource(R.string.set_licenses), null) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LICENSES_URL))) } }
        item {
            Text(
                stringResource(R.string.set_version, vm.buildInfo.versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            text = { Text(stringResource(R.string.set_delete_data_confirm)) },
            confirmButton = { TextButton(onClick = vm::deleteAllData) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun DiagnosticsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val d = remember(ui) { vm.diagnostics() }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) {
        item { DiagRow(stringResource(R.string.diag_network), d.networkOk, ui.network.type.name) }
        item { DiagRow(stringResource(R.string.diag_registration), d.registeredCount == d.accountCount && d.accountCount > 0, "${d.registeredCount}/${d.accountCount}") { vm.reconnectAll() } }
        item {
            DiagRow(stringResource(R.string.diag_battery), d.batteryUnrestricted, null) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        item {
            DiagRow(stringResource(R.string.diag_fullscreen), d.fullScreenAllowed, null) {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, ok: Boolean, detail: String?, fixLabel: String? = null, onFix: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(stringResource(if (ok) R.string.diag_ok else R.string.diag_problem) + (detail?.let { " · $it" } ?: "")) },
        leadingContent = {
            Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        },
        trailingContent = { if (!ok && onFix != null) TextButton(onClick = onFix) { Text(fixLabel ?: stringResource(R.string.open_settings)) } },
    )
}

@Composable
private fun NavRow(title: String, value: String?, onClick: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                value?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                // Chevron points "forward": left in RTL, right in LTR.
                Icon(Icons.Rounded.ChevronLeft, null, modifier = Modifier.scale(scaleX = if (rtl) 1f else -1f, scaleY = 1f))
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked, onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun <T> Chips(title: String, options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) -> FilterChip(selected == v, { onSelect(v) }, { Text(stringResource(label)) }) }
        }
    }
}

private const val WEBSITE_URL = "https://ivr.top"
private const val PRIVACY_URL = "https://dialer.ivr.top/privacy"
private const val SUPPORT_URL = "https://ivr.top/contact"
private const val GUIDE_URL = "https://dialer.ivr.top/guide"
private const val LICENSES_URL = "https://dialer.ivr.top/licenses"

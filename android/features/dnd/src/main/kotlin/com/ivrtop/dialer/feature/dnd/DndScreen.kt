@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ivrtop.dialer.feature.dnd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.common.Clock
import com.ivrtop.dialer.core.data.IncomingCallFilter
import com.ivrtop.dialer.core.datastore.SettingsStore
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.Formatters
import com.ivrtop.dialer.core.designsystem.component.InfoBanner
import com.ivrtop.dialer.core.designsystem.component.SectionHeader
import com.ivrtop.dialer.core.designsystem.component.StatusTone
import com.ivrtop.dialer.core.models.DndAction
import com.ivrtop.dialer.core.models.DndConfig
import com.ivrtop.dialer.core.models.DndSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class DndViewModel @Inject constructor(private val settings: SettingsStore, private val clock: Clock) : ViewModel() {
    val config = settings.settings.map { it.dnd }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DndConfig())
    fun isActive(cfg: DndConfig) = IncomingCallFilter.isDndActive(cfg, null, clock.now())
    private fun edit(t: (DndConfig) -> DndConfig) = viewModelScope.launch { settings.update { it.copy(dnd = t(it.dnd)) } }

    fun turnOnFor(hours: Int?) = edit { it.copy(enabledGlobally = true, until = hours?.let { h -> clock.now() + h * 3_600_000L }) }
    fun turnOnUntil(time: LocalTime) = edit {
        var target = ZonedDateTime.now(ZoneId.systemDefault()).with(time)
        if (target.toInstant().toEpochMilli() <= clock.now()) target = target.plusDays(1)
        it.copy(enabledGlobally = true, until = target.toInstant().toEpochMilli())
    }
    fun turnOff() = edit { it.copy(enabledGlobally = false, until = null) }
    fun setAction(a: DndAction) = edit { it.copy(action = a) }
    fun setForward(t: String) = edit { it.copy(forwardTo = t) }
    fun setFavorites(on: Boolean) = edit { it.copy(allowFavorites = on) }
    fun setRepeat(on: Boolean) = edit { it.copy(allowRepeatCallers = on) }
    fun addSchedule(s: DndSchedule) = edit { it.copy(schedules = it.schedules + s.copy(id = (it.schedules.maxOfOrNull { x -> x.id } ?: 0) + 1)) }
    fun removeSchedule(id: Long) = edit { it.copy(schedules = it.schedules.filterNot { s -> s.id == id }) }
    fun toggleSchedule(id: Long, on: Boolean) = edit { it.copy(schedules = it.schedules.map { s -> if (s.id == id) s.copy(enabled = on) else s }) }
}

@Composable
fun DndScreen(vm: DndViewModel = hiltViewModel()) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val active = vm.isActive(cfg)
    var pickUntil by remember { mutableStateOf(false) }
    var addSchedule by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dnd_title), style = MaterialTheme.typography.headlineSmall)
                if (active && cfg.enabledGlobally) {
                    Text(cfg.until?.let { stringResource(R.string.dnd_active_until, Formatters.dateTime(it)) } ?: stringResource(R.string.dnd_until_off),
                        color = MaterialTheme.colorScheme.tertiary)
                    Button(onClick = vm::turnOff) { Text(stringResource(R.string.dnd_turn_off)) }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.turnOnFor(1) }) { Text(stringResource(R.string.dnd_for_1h)) }
                        OutlinedButton(onClick = { vm.turnOnFor(3) }) { Text(stringResource(R.string.dnd_for_hours, 3)) }
                        OutlinedButton(onClick = { vm.turnOnFor(8) }) { Text(stringResource(R.string.dnd_for_hours, 8)) }
                        OutlinedButton(onClick = { pickUntil = true }) { Text(stringResource(R.string.dnd_until_time, "…")) }
                        Button(onClick = { vm.turnOnFor(null) }) { Text(stringResource(R.string.dnd_until_off)) }
                    }
                }
            }
        }

        InfoBanner(stringResource(R.string.dnd_scope_note), tone = StatusTone.OK)

        SectionHeader(stringResource(R.string.dnd_behavior), Modifier.padding(0.dp))
        listOf(
            DndAction.SILENCE to R.string.dnd_action_silence,
            DndAction.REJECT to R.string.dnd_action_reject,
            DndAction.VOICEMAIL to R.string.dnd_action_voicemail,
            DndAction.FORWARD to R.string.dnd_action_forward,
        ).forEach { (a, label) ->
            Row(Modifier.fillMaxWidth().clickable { vm.setAction(a) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = cfg.action == a, onClick = { vm.setAction(a) })
                Text(stringResource(label))
            }
        }
        if (cfg.action == DndAction.FORWARD) {
            OutlinedTextField(cfg.forwardTo.orEmpty(), vm::setForward, label = { Text(stringResource(R.string.dnd_forward_to)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dnd_allow_favorites), Modifier.weight(1f)); Switch(cfg.allowFavorites, vm::setFavorites)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dnd_repeat_callers), Modifier.weight(1f)); Switch(cfg.allowRepeatCallers, vm::setRepeat)
        }

        SectionHeader(stringResource(R.string.dnd_schedule), Modifier.padding(0.dp))
        val dayNames = stringResource(R.string.days_short).split(',')
        cfg.schedules.forEach { s ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("%02d:%02d – %02d:%02d".format(s.startMinuteOfDay / 60, s.startMinuteOfDay % 60, s.endMinuteOfDay / 60, s.endMinuteOfDay % 60))
                        // ISO 1=Mon..7=Sun → dayNames index 0=Sun
                        Text(s.days.sorted().joinToString(" ") { d -> dayNames.getOrElse(d % 7) { "?" } }, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(s.enabled, { vm.toggleSchedule(s.id, it) })
                    IconButton(onClick = { vm.removeSchedule(s.id) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                }
            }
        }
        TextButton(onClick = { addSchedule = true }) { Text(stringResource(R.string.dnd_add_schedule)) }
    }

    if (pickUntil) {
        val tp = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickUntil = false },
            text = { TimePicker(tp) },
            confirmButton = { TextButton(onClick = { vm.turnOnUntil(LocalTime.of(tp.hour, tp.minute)); pickUntil = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { pickUntil = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (addSchedule) ScheduleDialog(onDismiss = { addSchedule = false }, onSave = { vm.addSchedule(it); addSchedule = false })
}

@Composable
private fun ScheduleDialog(onDismiss: () -> Unit, onSave: (DndSchedule) -> Unit) {
    var days by remember { mutableStateOf(setOf(7, 1, 2, 3, 4)) } // Sun–Thu (Israeli work week)
    var editingStart by remember { mutableStateOf(true) }
    val start = rememberTimePickerState(22, 0, true)
    val end = rememberTimePickerState(7, 0, true)
    val dayNames = stringResource(R.string.days_short).split(',')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dnd_add_schedule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 1, 2, 3, 4, 5, 6).forEach { d ->
                        FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(dayNames.getOrElse(d % 7) { "?" }) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(editingStart, { editingStart = true }, { Text(stringResource(R.string.from_time) + " %02d:%02d".format(start.hour, start.minute)) })
                    FilterChip(!editingStart, { editingStart = false }, { Text(stringResource(R.string.to_time) + " %02d:%02d".format(end.hour, end.minute)) })
                }
                TimePicker(if (editingStart) start else end)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(DndSchedule(days = days, startMinuteOfDay = start.hour * 60 + start.minute, endMinuteOfDay = end.hour * 60 + end.minute))
            }, enabled = days.isNotEmpty()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

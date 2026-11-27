package com.ivrtop.dialer.feature.activecall

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.Avatar
import com.ivrtop.dialer.core.designsystem.component.BigRoundButton
import com.ivrtop.dialer.core.designsystem.component.CallControlButton
import com.ivrtop.dialer.core.designsystem.component.DialPad
import com.ivrtop.dialer.core.designsystem.component.Formatters
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.endReasonRes
import com.ivrtop.dialer.core.designsystem.component.phaseRes
import com.ivrtop.dialer.core.designsystem.theme.Brand
import com.ivrtop.dialer.core.designsystem.theme.IvrtopThemeExt
import com.ivrtop.dialer.core.designsystem.theme.NumberStyle
import com.ivrtop.dialer.core.models.AudioRoute
import com.ivrtop.dialer.core.models.CallPhase
import com.ivrtop.dialer.core.models.CallSnapshot
import com.ivrtop.dialer.core.models.QualityLevel
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveCallScreen(
    state: CallUiState,
    call: CallSnapshot,
    vm: CallViewModel,
    onKeyTone: (Char) -> Unit,
) {
    var showKeypad by remember { mutableStateOf(false) }
    var showRoutes by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var showAddCall by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var dtmfEcho by remember { mutableStateOf("") }
    val caps = call.capabilities
    val connected = call.phase.isConnected

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // soft brand wash behind the header only
        Box(Modifier.fillMaxWidth().height(280.dp).background(Brand.gradientSoft))
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Other calls (on hold) – tap to swap
            state.others.forEach { other ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                        .clickable { vm.swap(other.callId) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Pause, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(other.remoteDisplayName ?: PhoneNumbers.format(other.remoteNumber), modifier = Modifier.weight(1f), maxLines = 1)
                    Text(stringResource(phaseRes(other.phase)), style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(24.dp))
            val accountLabel = state.accounts[call.accountId]?.label
            if (state.accounts.size > 1 && accountLabel != null) {
                Text(stringResource(R.string.via_account, accountLabel), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Avatar(call.remoteDisplayName ?: call.remoteNumber, state.photoFor(call), size = 112.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                call.remoteDisplayName ?: PhoneNumbers.format(call.remoteNumber),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (call.remoteDisplayName != null) {
                LtrText(PhoneNumbers.format(call.remoteNumber), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            StatusLine(call)
            if (connected && call.quality.level != QualityLevel.UNKNOWN) {
                Row(Modifier.clickable { showQuality = !showQuality }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SignalCellularAlt, null, modifier = Modifier.size(16.dp), tint = when (call.quality.level) {
                        QualityLevel.POOR -> IvrtopThemeExt.colors.missed
                        QualityLevel.FAIR -> IvrtopThemeExt.colors.warning
                        else -> IvrtopThemeExt.colors.online
                    })
                    Text(
                        stringResource(R.string.quality_title) + ": " + stringResource(when (call.quality.level) {
                            QualityLevel.EXCELLENT -> R.string.quality_excellent
                            QualityLevel.GOOD -> R.string.quality_good
                            QualityLevel.FAIR -> R.string.quality_fair
                            else -> R.string.quality_poor
                        }),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                if (showQuality) {
                    val q = call.quality
                    LtrText(
                        stringResource(R.string.quality_detail, q.codec ?: "-", q.rttMs ?: 0, q.jitterMs ?: 0, q.lossPercent ?: 0f),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedContent(showKeypad, label = "keypad") { keypad ->
                if (keypad) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LtrText(dtmfEcho, style = NumberStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize), modifier = Modifier.padding(bottom = 8.dp))
                        DialPad(
                            onKey = { k -> dtmfEcho = (dtmfEcho + k).takeLast(24); onKeyTone(k); vm.dtmf(call, k) },
                            onLongPress = { false },
                            showLetters = false,
                            compact = true,
                            haptics = state.settings.haptics,
                        )
                        TextButton(onClick = { showKeypad = false }) { Text(stringResource(R.string.hide_keypad)) }
                    }
                } else {
                    FlowRow(
                        maxItemsInEachRow = 3,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val on = stringResource(R.string.on)
                        val off = stringResource(R.string.off)
                        if (caps.canMute) CallControlButton(Icons.Rounded.MicOff, stringResource(R.string.mute), checked = call.muted, stateText = if (call.muted) on else off, onClick = { vm.toggleMute(call) })
                        val hasExternal = call.availableRoutes.any { it.route == AudioRoute.BLUETOOTH || it.route == AudioRoute.WIRED_HEADSET }
                        val routeIcon: ImageVector = when (call.audioRoute) {
                            AudioRoute.BLUETOOTH -> Icons.Rounded.Bluetooth
                            AudioRoute.WIRED_HEADSET -> Icons.Rounded.Headphones
                            else -> Icons.AutoMirrored.Rounded.VolumeUp
                        }
                        CallControlButton(
                            routeIcon,
                            stringResource(if (hasExternal) R.string.audio_output else R.string.speaker),
                            checked = call.audioRoute == AudioRoute.SPEAKER,
                            onClick = {
                                if (hasExternal) showRoutes = true
                                else {
                                    val target = call.availableRoutes.firstOrNull {
                                        it.route == if (call.audioRoute == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER
                                    }
                                    target?.let { vm.route(call, it) }
                                }
                            },
                        )
                        if (caps.canDtmf) CallControlButton(Icons.Rounded.Dialpad, stringResource(R.string.keypad), enabled = connected, onClick = { showKeypad = true })
                        if (caps.canHold) CallControlButton(Icons.Rounded.Pause, stringResource(if (call.onHold) R.string.resume else R.string.hold), checked = call.onHold, enabled = connected, onClick = { vm.toggleHold(call) })
                        if (caps.canAddCall) CallControlButton(Icons.Rounded.AddIcCall, stringResource(R.string.add_call), enabled = connected, onClick = { showAddCall = true })
                        if (caps.canBlindTransfer) CallControlButton(Icons.AutoMirrored.Rounded.CallSplit, stringResource(R.string.transfer), enabled = connected, onClick = { showTransfer = true })
                        if (caps.canSwap && state.others.isNotEmpty()) CallControlButton(Icons.Rounded.SwapCalls, stringResource(R.string.swap_calls), onClick = { vm.swap(state.others.first().callId) })
                        if (caps.canMerge && state.others.isNotEmpty()) CallControlButton(Icons.AutoMirrored.Rounded.CallMerge, stringResource(R.string.merge_calls), onClick = { vm.merge() })
                    }
                }
            }

            state.consultFor?.let { (_, consultId) ->
                if (consultId == call.callId && connected) {
                    Button(onClick = { vm.completeTransfer() }, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Rounded.PhoneInTalk, null)
                        Text(stringResource(R.string.transfer), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            BigRoundButton(Icons.Rounded.CallEnd, stringResource(R.string.end_call), IvrtopThemeExt.colors.hangupRed, { vm.hangup(call.callId) }, size = 76.dp)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRoutes) {
        ModalBottomSheet(onDismissRequest = { showRoutes = false }) {
            Text(stringResource(R.string.audio_output), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            call.availableRoutes.forEach { ep ->
                ListItem(
                    headlineContent = {
                        Text(when (ep.route) {
                            AudioRoute.EARPIECE -> stringResource(R.string.route_earpiece)
                            AudioRoute.SPEAKER -> stringResource(R.string.route_speaker)
                            AudioRoute.WIRED_HEADSET -> stringResource(R.string.route_wired)
                            else -> ep.name
                        })
                    },
                    leadingContent = {
                        Icon(when (ep.route) {
                            AudioRoute.BLUETOOTH -> Icons.Rounded.Bluetooth
                            AudioRoute.WIRED_HEADSET -> Icons.Rounded.Headphones
                            else -> Icons.AutoMirrored.Rounded.VolumeUp
                        }, null)
                    },
                    trailingContent = { if (ep.route == call.audioRoute) Text("✓") },
                    modifier = Modifier.clickable { vm.route(call, ep); showRoutes = false },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTransfer) TargetSheet(
        title = stringResource(R.string.transfer_to),
        onDismiss = { showTransfer = false },
        primaryLabel = stringResource(R.string.transfer_blind),
        onPrimary = { vm.blindTransfer(call, it); showTransfer = false },
        // Consult-then-transfer creates its own second call, so it is always offered (PJSIP supports REFER/Replaces).
        secondaryLabel = stringResource(R.string.transfer_attended),
        onSecondary = { vm.startConsult(call, it); showTransfer = false },
    )

    if (showAddCall) TargetSheet(
        title = stringResource(R.string.add_call),
        onDismiss = { showAddCall = false },
        primaryLabel = stringResource(R.string.action_call),
        onPrimary = { vm.addCall(it); showAddCall = false },
    )
}

@Composable
private fun StatusLine(call: CallSnapshot) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.connectedAtMillis, call.phase) {
        while (call.connectedAtMillis != null && !call.phase.isTerminal) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val text = when {
        call.phase == CallPhase.ENDED -> stringResource(endReasonRes(call.endReason))
        call.phase == CallPhase.ACTIVE && call.connectedAtMillis != null -> Formatters.duration((now - call.connectedAtMillis!!) / 1000)
        else -> stringResource(phaseRes(call.phase))
    }
    Text(
        text,
        style = if (call.phase == CallPhase.ACTIVE) NumberStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize) else MaterialTheme.typography.titleMedium,
        color = if (call.phase == CallPhase.ENDED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun TargetSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    onPrimary: (String) -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (String) -> Unit = {},
) {
    var target by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = target,
                onValueChange = { target = PhoneNumbers.stripFormatting(it) },
                label = { Text(stringResource(R.string.transfer_target_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            Button(onClick = { onPrimary(target) }, enabled = target.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(primaryLabel) }
            secondaryLabel?.let {
                OutlinedButton(onClick = { onSecondary(target) }, enabled = target.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(it) }
            }
        }
    }
}

package com.ivrtop.dialer.feature.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.BrandLogoChip
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.errorMessageRes
import com.ivrtop.dialer.core.designsystem.theme.Brand
import com.ivrtop.dialer.core.models.ProbeStep

/**
 * Entry point for first-run and "add account" flows.
 * @param deepLink validated by ProvisioningRepository; null when opened manually.
 */
@Composable
fun OnboardingScreen(
    deepLink: String?,
    skipWelcome: Boolean,
    onManualSetup: () -> Unit,
    onFinished: () -> Unit,
    vm: ProvisioningViewModel = hiltViewModel(),
) {
    val step by vm.step.collectAsStateWithLifecycle()
    LaunchedEffect(deepLink) { vm.start(deepLink, skipWelcome) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(320.dp).background(Brand.gradientSoft))
        AnimatedContent(step, label = "setup", contentKey = { it::class }) { s ->
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrandLogoChip(height = 56.dp, modifier = Modifier.padding(top = 16.dp))
                when (s) {
                    SetupStep.Welcome -> Welcome(onNext = vm::toPermissions)
                    SetupStep.Permissions -> PermissionsStep(onNext = vm::toMethods)
                    SetupStep.Methods -> Methods(onLink = vm::submitLink, onManual = onManualSetup)
                    is SetupStep.Confirm -> Confirm(s, onConfirm = vm::confirm, onCancel = vm::backToMethods)
                    is SetupStep.Testing -> Testing(s.current)
                    is SetupStep.Done -> Done(s.accountLabel, onFinished)
                    is SetupStep.Failed -> Failed(s, onRetry = vm::retry, onBack = vm::backToMethods, onManual = onManualSetup)
                }
            }
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(48.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.get_started)) }
}

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.permissions_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    key(tick) {
        PermissionRow(stringResource(R.string.perm_mic), granted(Manifest.permission.RECORD_AUDIO)) {
            launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(stringResource(R.string.perm_notifications), granted(Manifest.permission.POST_NOTIFICATIONS)) {
                launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        PermissionRow(stringResource(R.string.perm_contacts), granted(Manifest.permission.READ_CONTACTS)) {
            launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(NotificationManager::class.java)
            PermissionRow(stringResource(R.string.perm_fullscreen), nm.canUseFullScreenIntent()) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
            }
        }
        val pm = context.getSystemService(PowerManager::class.java)
        PermissionRow(stringResource(R.string.perm_battery), pm.isIgnoringBatteryOptimizations(context.packageName)) {
            // Opens the system list (no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission needed).
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.continue_)) }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, stringResource(R.string.perm_granted), tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onGrant) { Text(stringResource(R.string.perm_grant)) }
            }
        }
    }
}

@Composable
private fun Methods(onLink: (String) -> Unit, onManual: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }

    Text(stringResource(R.string.setup_choose), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.setup_link_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

    val scanPrompt = stringResource(R.string.scan_qr_prompt)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(onLink) }
    MethodCard(Icons.Rounded.QrCodeScanner, stringResource(R.string.add_via_qr)) {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(scanPrompt)
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }
    MethodCard(Icons.Rounded.Link, stringResource(R.string.add_via_link)) { mode = "link"; text = "" }
    MethodCard(Icons.Rounded.Tune, stringResource(R.string.add_manual), onManual)

    if (mode != null) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.trim() },
            label = { Text(stringResource(R.string.add_via_link)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onLink(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.continue_))
        }
    }
}

@Composable
private fun MethodCard(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(44.dp).background(Brand.gradient, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Confirm(s: SetupStep.Confirm, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val a = s.request.account
    Text(stringResource(R.string.setup_confirm_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.setup_confirm_body), style = MaterialTheme.typography.bodyMedium)
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(a.displayName, style = MaterialTheme.typography.titleLarge)
            a.phoneNumber?.let { LtrText(PhoneNumbers.format(it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            LtrText("${a.username}@${a.domain}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    when {
        s.updatesExisting -> Text(stringResource(R.string.setup_update_existing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        s.additional -> Text(stringResource(R.string.setup_additional_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.setup_confirm_btn)) }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
}

@Composable
private fun Testing(current: ProbeStep) {
    Spacer(Modifier.height(24.dp))
    val steps = listOf(ProbeStep.LOCATE_SERVER to R.string.step_locate, ProbeStep.CHECK_CREDENTIALS to R.string.step_credentials, ProbeStep.CHECK_CONNECTION to R.string.step_connection)
    steps.forEach { (step, label) ->
        val done = current.ordinal > step.ordinal
        val active = current == step
        Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                when {
                    done -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    active -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    else -> Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)))
                }
            }
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium,
                color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun Done(label: String, onFinished: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
    Text(stringResource(R.string.step_done), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.setup_to_dialer)) }
}

@Composable
private fun Failed(s: SetupStep.Failed, onRetry: () -> Unit, onBack: () -> Unit, onManual: () -> Unit) {
    var showTech by remember { mutableStateOf(false) }
    Spacer(Modifier.height(24.dp))
    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(80.dp))
    Text(stringResource(R.string.setup_failed), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(errorMessageRes(s.error)), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    if (s.canRetry) Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    TextButton(onClick = onManual) { Text(stringResource(R.string.add_manual)) }
    if (!s.technical.isNullOrBlank()) {
        TextButton(onClick = { showTech = !showTech }) { Text(stringResource(R.string.technical_details)) }
        if (showTech) LtrText("${s.error.code}\n${s.technical}", maxLines = 12, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}


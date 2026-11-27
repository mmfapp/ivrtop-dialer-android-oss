package com.ivrtop.dialer.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.theme.IvrtopThemeExt
import com.ivrtop.dialer.core.models.RegistrationState

enum class StatusTone { OK, BUSY, WARN, ERROR, MUTED }

@Composable
fun registrationLabel(state: RegistrationState?): Pair<String, StatusTone> = when (state) {
    null, RegistrationState.Idle -> stringResource(R.string.status_idle) to StatusTone.MUTED
    RegistrationState.Disabled -> stringResource(R.string.status_disabled) to StatusTone.MUTED
    RegistrationState.Registering -> stringResource(R.string.status_registering) to StatusTone.BUSY
    is RegistrationState.Registered -> stringResource(R.string.status_registered) to StatusTone.OK
    RegistrationState.NoNetwork -> stringResource(R.string.status_no_network) to StatusTone.WARN
    is RegistrationState.Disconnected -> stringResource(R.string.status_disconnected) to StatusTone.WARN
    RegistrationState.AuthFailed -> stringResource(R.string.status_auth_failed) to StatusTone.ERROR
    is RegistrationState.ServerUnreachable -> stringResource(R.string.status_unreachable) to StatusTone.ERROR
    RegistrationState.AccountSuspended -> stringResource(R.string.status_suspended) to StatusTone.ERROR
    RegistrationState.CallsDisabled -> stringResource(R.string.status_calls_disabled) to StatusTone.WARN
    is RegistrationState.Error -> stringResource(errorMessageRes(state.code)) to StatusTone.ERROR
}

@Composable
fun StatusPill(state: RegistrationState?, modifier: Modifier = Modifier, dnd: Boolean = false) {
    val (label, tone) = if (dnd) stringResource(R.string.status_dnd) to StatusTone.WARN else registrationLabel(state)
    val ext = IvrtopThemeExt.colors
    val dot = when (tone) {
        StatusTone.OK -> ext.online
        StatusTone.BUSY -> MaterialTheme.colorScheme.primary
        StatusTone.WARN -> ext.warning
        StatusTone.ERROR -> MaterialTheme.colorScheme.error
        StatusTone.MUTED -> MaterialTheme.colorScheme.outline
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (tone == StatusTone.BUSY) a else 1f)
                .background(dot, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

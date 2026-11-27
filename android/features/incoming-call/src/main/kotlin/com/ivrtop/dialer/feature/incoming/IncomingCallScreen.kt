package com.ivrtop.dialer.feature.incoming

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.Avatar
import com.ivrtop.dialer.core.designsystem.component.BigRoundButton
import com.ivrtop.dialer.core.designsystem.component.BrandLogoChip
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.phaseRes
import com.ivrtop.dialer.core.designsystem.theme.Brand
import com.ivrtop.dialer.core.designsystem.theme.IvrtopThemeExt
import com.ivrtop.dialer.core.models.CallSnapshot

/**
 * Full-screen incoming call. Rendered on the brand gradient with white text (contrast ≥ 4.5:1
 * across the whole gradient). Buttons instead of swipe gestures: one tap, TalkBack-friendly.
 */
@Composable
fun IncomingCallScreen(
    call: CallSnapshot,
    accountLabel: String?,
    photoUri: String?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onSilence: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "ring")
    val ringScale by pulse.animateFloat(1f, 1.35f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "s")
    val ringAlpha by pulse.animateFloat(0.45f, 0f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "a")
    val name = call.remoteDisplayName
    val number = call.remoteNumber

    Box(Modifier.fillMaxSize().background(Brand.gradient)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandLogoChip(height = 36.dp)
            Spacer(Modifier.height(32.dp))
            Text(
                accountLabel?.let { stringResource(R.string.incoming_to_account, it) } ?: stringResource(phaseRes(call.phase)),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Spacer(Modifier.height(36.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(140.dp).scale(ringScale).alpha(ringAlpha).background(Color.White, CircleShape))
                Avatar(name ?: number, photoUri, size = 132.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                name ?: PhoneNumbers.format(number).ifBlank { stringResource(R.string.private_number) },
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (name != null && number.isNotBlank()) {
                LtrText(PhoneNumbers.format(number), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSilence) {
                Icon(Icons.Rounded.NotificationsOff, null, tint = Color.White)
                Text(stringResource(R.string.silence), color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BigRoundButton(Icons.Rounded.CallEnd, stringResource(R.string.decline), IvrtopThemeExt.colors.hangupRed, onDecline, size = 80.dp)
                    Text(stringResource(R.string.decline), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BigRoundButton(Icons.Rounded.Call, stringResource(R.string.answer), Brand.CallGreen, onAnswer, size = 80.dp)
                    Text(stringResource(R.string.answer), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

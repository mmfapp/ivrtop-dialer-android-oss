package com.ivrtop.dialer.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import com.ivrtop.dialer.core.common.phone.T9
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.theme.NumberStyle

private val KEYS = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
    listOf('*', '0', '#'),
)

/**
 * Phone keypad. Always laid out LTR (1-2-3 left-to-right) regardless of UI direction, exactly like
 * Israeli handsets. Long-press: 0 → "+", 1 → voicemail, 2–9 → speed dial (handled by caller).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialPad(
    onKey: (Char) -> Unit,
    onLongPress: (Char) -> Boolean,
    modifier: Modifier = Modifier,
    showLetters: Boolean = true,
    showHebrew: Boolean = true,
    haptics: Boolean = true,
    compact: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            KEYS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        DialKey(
                            key = key,
                            letters = when {
                                !showLetters -> ""
                                key == '0' -> "+"
                                key == '1' -> "" // long-press = voicemail (announced via semantics)
                                else -> T9.lettersFor(key, showHebrew)
                            },
                            modifier = Modifier.weight(1f),
                            compact = compact,
                            onClick = {
                                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onKey(key)
                            },
                            onLongClick = {
                                if (onLongPress(key) && haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    key: Char,
    letters: String,
    compact: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "keyScale")
    val longLabel = when (key) {
        '0' -> stringResource(R.string.dialpad_long_plus)
        '1' -> stringResource(R.string.dialpad_long_voicemail)
        in '2'..'9' -> stringResource(R.string.dialpad_long_speeddial)
        else -> null
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .aspectRatio(if (compact) 1.6f else 1.25f)
            .scale(scale)
            .clip(CircleShape)
            .semantics {
                contentDescription = key.toString()
                if (longLabel != null) onLongClick(label = longLabel) { onLongClick(); true }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = key.toString(),
                    style = NumberStyle.copy(fontSize = if (compact) 24.sp else 30.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (letters.isNotEmpty() && key != '1') {
                    Text(
                        text = letters,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 0.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

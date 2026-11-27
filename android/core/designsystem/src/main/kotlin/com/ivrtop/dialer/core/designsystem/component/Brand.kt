package com.ivrtop.dialer.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.theme.Brand

/**
 * The ORIGINAL logo artwork on a white rounded chip. The logo was designed for light
 * backgrounds; placing it on white keeps it pixel-faithful in dark mode too (never recoloured).
 */
@Composable
fun BrandLogoChip(modifier: Modifier = Modifier, height: Dp = 40.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.brand_logo_full),
            contentDescription = stringResource(R.string.brand_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(height - 8.dp),
        )
    }
}

/** Thin brand gradient band used as a decorative accent (never behind body text). */
@Composable
fun BrandGradientBar(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Brand.gradient))
}

@Composable
fun EmptyState(
    title: String,
    body: String? = null,
    icon: ImageVector = Icons.Rounded.Inbox,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(Brand.gradientSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        action?.invoke()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun AccountColorDot(colorArgb: Long, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(50)).background(Color(colorArgb)))
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, tone: StatusTone = StatusTone.WARN, action: (@Composable () -> Unit)? = null) {
    val bg = when (tone) {
        StatusTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        StatusTone.OK -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val fg = when (tone) {
        StatusTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        StatusTone.OK -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

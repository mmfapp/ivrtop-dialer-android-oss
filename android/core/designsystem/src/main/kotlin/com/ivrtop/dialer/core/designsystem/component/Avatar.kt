package com.ivrtop.dialer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val avatarPalettes = listOf(
    listOf(Color(0xFF13B5B8), Color(0xFF0A78A6)),
    listOf(Color(0xFF0A78A6), Color(0xFF5B5FA8)),
    listOf(Color(0xFF5B5FA8), Color(0xFFA3418F)),
    listOf(Color(0xFF229D99), Color(0xFF13B5B8)),
    listOf(Color(0xFFA3418F), Color(0xFF5B5FA8)),
)

/** Contact photo, or brand-gradient initials, or a person glyph for unknown numbers. */
@Composable
fun Avatar(
    name: String?,
    photoUri: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        val initials = name?.takeIf { n -> n.any { it.isLetter() } }
            ?.split(' ', '-')?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }
        val palette = avatarPalettes[((name ?: "").hashCode() and 0x7fffffff) % avatarPalettes.size]
        Box(
            Modifier.size(size).background(if (initials != null) Brush.linearGradient(palette) else Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHigh),
            )),
            contentAlignment = Alignment.Center,
        ) {
            if (initials != null) {
                Text(initials, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.38f).sp))
            } else {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.55f))
            }
        }
        if (photoUri != null) {
            AsyncImage(model = photoUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        }
    }
}

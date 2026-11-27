@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ivrtop.dialer.feature.favorites

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.Avatar
import com.ivrtop.dialer.core.designsystem.component.EmptyState
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.errorMessageRes
import com.ivrtop.dialer.core.designsystem.theme.IvrtopThemeExt
import com.ivrtop.dialer.core.models.PresenceState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Composable
fun FavoritesScreen(
    onCallStarted: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: FavoritesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var local by remember { mutableStateOf(state.items) }
    var editing by remember { mutableStateOf<FavoriteItem?>(null) }
    LaunchedEffect(state.items) { local = state.items }
    LaunchedEffect(Unit) {
        vm.events.collect { (id, err) ->
            id?.let(onCallStarted)
            err?.let { Toast.makeText(context, errorMessageRes(it), Toast.LENGTH_LONG).show() }
        }
    }

    if (state.loaded && local.isEmpty()) {
        EmptyState(stringResource(R.string.favorites_empty), stringResource(R.string.favorites_empty_body), Icons.Rounded.Star, modifier)
        return
    }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        local = local.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(local, key = { it.favorite.id }) { item ->
            ReorderableItem(reorderState, key = item.favorite.id) { dragging ->
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .shadow(if (dragging) 12.dp else 0.dp, RoundedCornerShape(28.dp))
                        .longPressDraggableHandle(onDragStopped = { vm.reorder(local.map { it.favorite.id }) })
                        .clickable(onClickLabel = stringResource(R.string.action_call)) { vm.call(item.favorite) },
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Avatar(item.favorite.displayName, item.photo, size = 80.dp)
                            item.presence?.let { p ->
                                val c = when (p) {
                                    PresenceState.AVAILABLE -> IvrtopThemeExt.colors.online
                                    PresenceState.BUSY, PresenceState.DND -> IvrtopThemeExt.colors.missed
                                    PresenceState.RINGING -> IvrtopThemeExt.colors.warning
                                    else -> MaterialTheme.colorScheme.outline
                                }
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(18.dp)
                                        .border(3.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                                        .background(c, CircleShape),
                                )
                            }
                            item.favorite.speedDialDigit?.let { d ->
                                Text(
                                    d.toString(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.TopStart).size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                        }
                        Text(item.favorite.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
                        TextButton(onClick = { editing = item }) {
                            Text(stringResource(R.string.more), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        val f = item.favorite
        ModalBottomSheet(onDismissRequest = { editing = null }) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(f.displayName, style = MaterialTheme.typography.titleLarge)
                LtrText(PhoneNumbers.format(f.number), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text(stringResource(R.string.assign_speed_dial), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = f.speedDialDigit == null, onClick = { vm.setSpeedDial(f, null); editing = null }, label = { Text(stringResource(R.string.none)) })
                    (2..9).forEach { d ->
                        FilterChip(selected = f.speedDialDigit == d, onClick = { vm.setSpeedDial(f, d); editing = null }, label = { Text(d.toString()) })
                    }
                }
                if (state.accounts.size > 1) {
                    Text(stringResource(R.string.select_account), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.accounts.forEach { a ->
                            FilterChip(selected = f.preferredAccountId == a.id, onClick = { vm.setPreferredAccount(f, if (f.preferredAccountId == a.id) null else a.id); editing = null }, label = { Text(a.label) })
                        }
                    }
                }
                if (PhoneNumbers.isExtension(f.number)) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BLF", modifier = Modifier.weight(1f))
                        Switch(checked = f.blfEnabled, onCheckedChange = { vm.setBlf(f, it); editing = null })
                    }
                }
                TextButton(onClick = { vm.remove(f); editing = null }) { Text(stringResource(R.string.remove_from_favorites), color = MaterialTheme.colorScheme.error) }
                Text(stringResource(R.string.reorder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

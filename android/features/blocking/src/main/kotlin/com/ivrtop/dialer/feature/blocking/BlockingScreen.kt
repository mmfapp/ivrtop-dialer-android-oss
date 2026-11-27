package com.ivrtop.dialer.feature.blocking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ivrtop.dialer.core.data.BlockRepository
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.EmptyState
import com.ivrtop.dialer.core.designsystem.component.InfoBanner
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.SectionHeader
import com.ivrtop.dialer.core.designsystem.component.StatusTone
import com.ivrtop.dialer.core.models.BlockKind
import com.ivrtop.dialer.core.models.BlockListType
import com.ivrtop.dialer.core.models.BlockRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockingViewModel @Inject constructor(private val repo: BlockRepository) : ViewModel() {
    val rules = repo.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun add(pattern: String, kind: BlockKind, list: BlockListType) = viewModelScope.launch { repo.add(pattern, kind, list) }
    fun remove(id: Long) = viewModelScope.launch { repo.remove(id) }
    fun setAnonymous(on: Boolean) = viewModelScope.launch {
        val existing = rules.value.firstOrNull { it.kind == BlockKind.ANONYMOUS }
        if (on && existing == null) repo.add("anonymous", BlockKind.ANONYMOUS)
        if (!on && existing != null) repo.remove(existing.id)
    }
}

@Composable
fun BlockingScreen(vm: BlockingViewModel = hiltViewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(BlockKind.EXACT) }
    var list by remember { mutableStateOf(BlockListType.DENY) }
    val anonymousOn = rules.any { it.kind == BlockKind.ANONYMOUS }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoBanner(stringResource(R.string.block_local_note), tone = StatusTone.OK)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.block_anonymous), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = anonymousOn, onCheckedChange = vm::setAnonymous)
                }
                OutlinedTextField(
                    value = input, onValueChange = { input = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text(stringResource(R.string.block_add)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == BlockKind.EXACT, { kind = BlockKind.EXACT }, { Text(stringResource(R.string.block_kind_exact)) })
                    FilterChip(kind == BlockKind.PREFIX, { kind = BlockKind.PREFIX }, { Text(stringResource(R.string.block_kind_prefix)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(list == BlockListType.DENY, { list = BlockListType.DENY }, { Text(stringResource(R.string.deny_list)) })
                    FilterChip(list == BlockListType.ALLOW, { list = BlockListType.ALLOW }, { Text(stringResource(R.string.allow_list)) })
                }
                Button(onClick = { vm.add(input, kind, list); input = "" }, enabled = input.length >= 2) {
                    Icon(Icons.Rounded.Block, null); Text(stringResource(R.string.block_number), Modifier.padding(start = 8.dp))
                }
            }
        }
        val visible = rules.filter { it.kind != BlockKind.ANONYMOUS }
        if (visible.isEmpty()) item { EmptyState(stringResource(R.string.block_empty), icon = Icons.Rounded.Block) }
        listOf(BlockListType.DENY to R.string.deny_list, BlockListType.ALLOW to R.string.allow_list).forEach { (type, title) ->
            val section = visible.filter { it.list == type }
            if (section.isNotEmpty()) {
                item { SectionHeader(stringResource(title)) }
                items(section, key = { it.id }) { r -> RuleRow(r) { vm.remove(r.id) } }
            }
        }
    }
}

@Composable
private fun RuleRow(r: BlockRule, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { LtrText(if (r.kind == BlockKind.PREFIX) r.pattern + "…" else r.pattern) },
        supportingContent = { Text(stringResource(if (r.kind == BlockKind.PREFIX) R.string.block_kind_prefix else R.string.block_kind_exact)) },
        trailingContent = { IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, stringResource(R.string.unblock_number)) } },
    )
}

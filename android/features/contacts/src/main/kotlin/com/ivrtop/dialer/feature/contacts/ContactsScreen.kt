package com.ivrtop.dialer.feature.contacts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.ivrtop.dialer.core.models.ContactSource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onCallStarted: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: ContactsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPermissionResult() }

    LaunchedEffect(Unit) {
        vm.events.collect { (callId, err) ->
            callId?.let(onCallStarted)
            err?.let { Toast.makeText(context, errorMessageRes(it), Toast.LENGTH_LONG).show() }
        }
    }

    if (!state.hasPermission) {
        EmptyState(
            title = stringResource(R.string.contacts_permission_title),
            body = stringResource(R.string.contacts_permission_body),
            icon = Icons.Rounded.Contacts,
            modifier = modifier,
        ) {
            Button(onClick = { permLauncher.launch(Manifest.permission.READ_CONTACTS) }) { Text(stringResource(R.string.contacts_permission_btn)) }
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))
            }) { Icon(Icons.Rounded.PersonAdd, stringResource(R.string.add_contact)) }
        }
        if (state.contacts.isEmpty()) {
            EmptyState(stringResource(R.string.contacts_empty), icon = Icons.Rounded.Contacts)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            val grouped = state.contacts.groupBy { it.displayName.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }
            grouped.forEach { (letter, list) ->
                stickyHeader(key = "h_$letter") {
                    Text(
                        letter.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
                items(list, key = { it.source.name + it.id }) { c ->
                    Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == c.id) null else c.id }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Avatar(c.displayName, c.thumbnailUri, size = 44.dp)
                            Text(c.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            c.phones.firstOrNull()?.let { p ->
                                IconButton(onClick = { vm.call(c, p) }) {
                                    Icon(Icons.Rounded.Call, stringResource(R.string.action_call), tint = IvrtopThemeExt.colors.callGreen)
                                }
                            }
                        }
                        AnimatedVisibility(expanded == c.id) {
                            Column(Modifier.padding(start = 74.dp, end = 16.dp, bottom = 8.dp)) {
                                c.phones.forEach { p ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            LtrText(PhoneNumbers.format(p.number), style = MaterialTheme.typography.bodyLarge)
                                            p.label?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                        val fav = p.number in state.favoriteNumbers
                                        IconButton(onClick = { vm.toggleFavorite(c, p) }) {
                                            Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                                stringResource(if (fav) R.string.remove_from_favorites else R.string.add_to_favorites))
                                        }
                                        IconButton(onClick = { vm.call(c, p) }) {
                                            Icon(Icons.Rounded.Call, stringResource(R.string.action_call), tint = IvrtopThemeExt.colors.callGreen)
                                        }
                                    }
                                }
                                if (c.source == ContactSource.DEVICE && c.lookupKey != null) {
                                    Row(
                                        Modifier.clickable {
                                            val uri = ContactsContract.Contacts.getLookupUri(c.id.toLong(), c.lookupKey)
                                            context.startActivity(Intent(Intent.ACTION_EDIT, uri ?: Uri.EMPTY))
                                        }.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary)
                                        Text(stringResource(R.string.edit_contact), color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

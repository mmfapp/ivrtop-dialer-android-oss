package com.ivrtop.dialer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivrtop.dialer.MainUi
import com.ivrtop.dialer.core.common.phone.PhoneNumbers
import com.ivrtop.dialer.core.designsystem.R
import com.ivrtop.dialer.core.designsystem.component.AccountColorDot
import com.ivrtop.dialer.core.designsystem.component.BrandGradientBar
import com.ivrtop.dialer.core.designsystem.component.BrandLogoChip
import com.ivrtop.dialer.core.designsystem.component.LtrText
import com.ivrtop.dialer.core.designsystem.component.StatusPill

/** Logo · active account (name + number) · connection status · DND indicator · account switch. */
@Composable
fun AppHeader(ui: MainUi, onSwitchAccount: (String) -> Unit, onDndClick: () -> Unit) {
    val acc = ui.activeAccount
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandLogoChip(height = 40.dp)
            Column(Modifier.weight(1f)) {
                if (acc != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountColorDot(acc.colorArgb)
                        Text(acc.displayName.ifBlank { acc.username }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    LtrText(PhoneNumbers.format(acc.phoneNumber ?: acc.username), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (ui.dndActive) {
                IconButton(onClick = onDndClick) {
                    Icon(Icons.Rounded.DoNotDisturbOn, stringResource(R.string.dnd_active_badge), tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (ui.accounts.size > 1) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.switch_account)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ui.accounts.filter { it.enabled }.forEach { a ->
                            DropdownMenuItem(
                                text = { Text(a.label) },
                                leadingIcon = { AccountColorDot(a.colorArgb) },
                                trailingIcon = { StatusPill(ui.registration[a.id]) },
                                onClick = { menu = false; onSwitchAccount(a.id) },
                            )
                        }
                    }
                }
            }
        }
        if (acc != null) {
            StatusPill(ui.registration[acc.id], dnd = acc.dnd, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        }
        BrandGradientBar()
    }
}

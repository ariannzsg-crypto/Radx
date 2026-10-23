package com.radx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radx.app.BuildConfig
import com.radx.app.R
import com.radx.app.data.AppSettings
import com.radx.app.data.CoreKind
import com.radx.app.data.TunnelMode
import com.radx.app.ui.UiState
import com.radx.app.ui.components.RadxCard
import com.radx.app.ui.components.SectionHeader
import com.radx.app.ui.theme.RadxCyan
import com.radx.app.ui.theme.RadxSuccess
import com.radx.app.ui.theme.RadxTextSecondary

@Composable
fun SettingsScreen(
    state: UiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    var localDns by rememberSaveable { mutableStateOf(state.settings.localDns) }
    var remoteDns by rememberSaveable { mutableStateOf(state.settings.remoteDns) }

    LaunchedEffect(state.settings.localDns) { localDns = state.settings.localDns }
    LaunchedEffect(state.settings.remoteDns) { remoteDns = state.settings.remoteDns }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item {
            RadxCard {
                SectionHeader(title = stringResource(R.string.settings_core))
                Spacer(Modifier.height(8.dp))
                OptionRow(
                    title = stringResource(R.string.settings_core_singbox),
                    subtitle = state.coreVersion.takeIf { it.isNotBlank() && it != "not bundled" },
                    selected = state.settings.core == CoreKind.SING_BOX,
                    enabled = state.coreBundled,
                    onClick = { onUpdate { it.copy(core = CoreKind.SING_BOX) } },
                )
                OptionRow(
                    title = stringResource(R.string.settings_core_clash),
                    subtitle = stringResource(R.string.settings_core_clash_unavailable),
                    selected = state.settings.core == CoreKind.CLASH_META,
                    enabled = false,
                    onClick = {},
                )
            }
        }

        item {
            RadxCard {
                SectionHeader(title = stringResource(R.string.settings_tunnel_mode))
                Spacer(Modifier.height(8.dp))
                OptionRow(
                    title = stringResource(R.string.tunnel_vpn),
                    subtitle = stringResource(R.string.tunnel_vpn_desc),
                    selected = state.settings.tunnelMode == TunnelMode.VPN,
                    enabled = true,
                    onClick = { onUpdate { it.copy(tunnelMode = TunnelMode.VPN) } },
                )
                OptionRow(
                    title = stringResource(R.string.tunnel_proxy),
                    subtitle = stringResource(R.string.tunnel_proxy_desc),
                    selected = state.settings.tunnelMode == TunnelMode.PROXY,
                    enabled = true,
                    onClick = { onUpdate { it.copy(tunnelMode = TunnelMode.PROXY) } },
                )
            }
        }

        item {
            RadxCard {
                SectionHeader(title = stringResource(R.string.settings_network))
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_allow_lan),
                    subtitle = stringResource(R.string.settings_allow_lan_desc),
                    checked = state.settings.allowLan,
                    onCheckedChange = { value -> onUpdate { it.copy(allowLan = value) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_ipv6),
                    subtitle = stringResource(R.string.settings_ipv6_desc),
                    checked = state.settings.ipv6,
                    onCheckedChange = { value -> onUpdate { it.copy(ipv6 = value) } },
                )
            }
        }

        item {
            RadxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        title = "DNS",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onUpdate {
                                it.copy(
                                    localDns = localDns.trim().ifBlank { "223.5.5.5" },
                                    remoteDns = remoteDns.trim().ifBlank { "https://1.1.1.1/dns-query" },
                                )
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.apply),
                            tint = RadxCyan,
                        )
                    }
                }
                OutlinedTextField(
                    value = localDns,
                    onValueChange = { localDns = it },
                    label = { Text(stringResource(R.string.settings_dns_local)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = remoteDns,
                    onValueChange = { remoteDns = it },
                    label = { Text(stringResource(R.string.settings_dns_remote)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            RadxCard {
                SwitchRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = state.settings.showNotification,
                    onCheckedChange = { value -> onUpdate { it.copy(showNotification = value) } },
                )
            }
        }

        item {
            RadxCard {
                SectionHeader(title = stringResource(R.string.settings_about))
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    label = stringResource(R.string.settings_version),
                    value = BuildConfig.VERSION_NAME,
                )
                InfoRow(
                    label = stringResource(R.string.settings_core_status),
                    value = stringResource(if (state.coreBundled) R.string.core_ready else R.string.core_missing),
                    highlight = state.coreBundled,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RadxTextSecondary,
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = { if (enabled) onClick() },
            enabled = enabled,
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    RadxTextSecondary
                },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RadxTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) RadxSuccess else MaterialTheme.colorScheme.onSurface,
        )
    }
}

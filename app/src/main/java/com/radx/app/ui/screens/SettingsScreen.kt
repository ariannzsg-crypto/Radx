package com.radx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.radx.app.BuildConfig
import com.radx.app.R
import com.radx.app.config.SingBoxConfigBuilder
import com.radx.app.core.XrayRunner
import com.radx.app.data.AccentColor
import com.radx.app.data.AppSettings
import com.radx.app.data.CoreKind
import com.radx.app.data.LogLevel
import com.radx.app.data.TunnelMode
import com.radx.app.ui.UiState
import com.radx.app.ui.components.GradientTitle
import com.radx.app.ui.components.RadxCard
import com.radx.app.ui.components.SectionHeader
import com.radx.app.ui.theme.LocalRadxAccent
import com.radx.app.ui.theme.RadxCyan
import com.radx.app.ui.theme.RadxSuccess
import com.radx.app.ui.theme.RadxTextSecondary
import com.radx.app.ui.theme.accentOf

@Composable
fun SettingsScreen(
    state: UiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val xrayReady = remember { XrayRunner.isAvailable(context) }
    var localDns by rememberSaveable { mutableStateOf(state.settings.localDns) }
    var remoteDns by rememberSaveable { mutableStateOf(state.settings.remoteDns) }
    var proxyPort by rememberSaveable { mutableStateOf(state.settings.proxyPort.toString()) }

    LaunchedEffect(state.settings.localDns) { localDns = state.settings.localDns }
    LaunchedEffect(state.settings.remoteDns) { remoteDns = state.settings.remoteDns }
    LaunchedEffect(state.settings.proxyPort) { proxyPort = state.settings.proxyPort.toString() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientTitle(text = stringResource(R.string.settings))
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
                SectionHeader(title = stringResource(R.string.settings_appearance))
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_glass),
                    subtitle = stringResource(R.string.settings_glass_desc),
                    checked = state.settings.glassEffects,
                    onCheckedChange = { value -> onUpdate { it.copy(glassEffects = value) } },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_accent),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentColor.entries.forEach { accent ->
                        AccentSwatch(
                            accent = accent,
                            selected = state.settings.accent == accent,
                            onClick = { onUpdate { it.copy(accent = accent) } },
                        )
                    }
                }
            }
        }

        item {
            RadxCard {
                SectionHeader(title = stringResource(R.string.settings_behaviour))
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_auto_connect),
                    subtitle = stringResource(R.string.settings_auto_connect_desc),
                    checked = state.settings.autoConnect,
                    onCheckedChange = { value -> onUpdate { it.copy(autoConnect = value) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_auto_refresh),
                    subtitle = stringResource(R.string.settings_auto_refresh_desc),
                    checked = state.settings.autoRefresh,
                    onCheckedChange = { value -> onUpdate { it.copy(autoRefresh = value) } },
                )
            }
        }

        item {
            RadxCard {
                SectionHeader(title = stringResource(R.string.settings_nodes))
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_auto_ping),
                    subtitle = stringResource(R.string.settings_auto_ping_desc),
                    checked = state.settings.autoPing,
                    onCheckedChange = { value -> onUpdate { it.copy(autoPing = value) } },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_ping_timeout),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        1_000 to R.string.settings_timeout_1s,
                        3_000 to R.string.settings_timeout_3s,
                        5_000 to R.string.settings_timeout_5s,
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = state.settings.pingTimeoutMs == value,
                            onClick = { onUpdate { it.copy(pingTimeoutMs = value) } },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = proxyPort,
                    onValueChange = { text -> proxyPort = text.filter { it.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.settings_proxy_port)) },
                    supportingText = { Text(stringResource(R.string.settings_proxy_port_desc)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val port = proxyPort.toIntOrNull()?.coerceIn(
                                    SingBoxConfigBuilder.MIN_PROXY_PORT,
                                    SingBoxConfigBuilder.MAX_PROXY_PORT,
                                ) ?: state.settings.proxyPort
                                proxyPort = port.toString()
                                onUpdate { it.copy(proxyPort = port) }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.apply),
                                tint = RadxCyan,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            RadxCard {
                SectionHeader(title = stringResource(R.string.settings_advanced))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_mtu),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_mtu_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppSettings.MIN_MTU to R.string.settings_mtu_1280,
                        1500 to R.string.settings_mtu_1500,
                        AppSettings.MAX_MTU to R.string.settings_mtu_9000,
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = state.settings.tunMtu == value,
                            onClick = { onUpdate { it.copy(tunMtu = value) } },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_bypass_lan),
                    subtitle = stringResource(R.string.settings_bypass_lan_desc),
                    checked = state.settings.bypassLan,
                    onCheckedChange = { value -> onUpdate { it.copy(bypassLan = value) } },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_block_quic),
                    subtitle = stringResource(R.string.settings_block_quic_desc),
                    checked = state.settings.blockQuic,
                    onCheckedChange = { value -> onUpdate { it.copy(blockQuic = value) } },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_log_level),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        LogLevel.ERROR to R.string.settings_log_error,
                        LogLevel.WARN to R.string.settings_log_warn,
                        LogLevel.INFO to R.string.settings_log_info,
                        LogLevel.DEBUG to R.string.settings_log_debug,
                    ).forEach { (level, label) ->
                        FilterChip(
                            selected = state.settings.logLevel == level,
                            onClick = { onUpdate { it.copy(logLevel = level) } },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
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
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
                InfoRow(
                    label = stringResource(R.string.settings_core_status),
                    value = stringResource(if (state.coreBundled) R.string.core_ready else R.string.core_missing),
                    highlight = state.coreBundled,
                )
                InfoRow(
                    label = stringResource(R.string.settings_xray_status),
                    value = stringResource(if (xrayReady) R.string.core_ready else R.string.core_missing),
                    highlight = xrayReady,
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
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = accentOf(accent)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(palette.primary)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    LocalRadxAccent.current.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = palette.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
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

package com.radx.app.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.radx.app.data.LogLevel
import com.radx.app.data.TunnelMode
import com.radx.app.ui.UiState
import com.radx.app.ui.components.GradientTitle
import com.radx.app.ui.components.RadxCard
import com.radx.app.ui.theme.LocalRadxAccent
import com.radx.app.ui.theme.RadxSuccess
import com.radx.app.ui.theme.RadxTextSecondary
import com.radx.app.ui.theme.accentOf

/**
 * Every knob the tunnel reads, grouped the way the app is actually configured:
 * interface, VPN, core, mux, fragmentation, then the rest.
 *
 * A section only exists when its options reach the generated config, so nothing here
 * is a switch that does not change the tunnel.
 */
@Composable
fun SettingsScreen(
    state: UiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val xrayReady = remember { XrayRunner.isAvailable(context) }
    val settings = state.settings

    var uiOpen by rememberSaveable { mutableStateOf(true) }
    var vpnOpen by rememberSaveable { mutableStateOf(true) }
    var coreOpen by rememberSaveable { mutableStateOf(false) }
    var muxOpen by rememberSaveable { mutableStateOf(false) }
    var fragmentOpen by rememberSaveable { mutableStateOf(false) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var languageOpen by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientTitle(text = stringResource(R.string.settings))
        }

        item {
            Section(
                title = stringResource(R.string.settings_ui),
                open = uiOpen,
                onToggle = { uiOpen = !uiOpen },
            ) {
                SwitchSetting(
                    title = stringResource(R.string.settings_speed_display),
                    subtitle = stringResource(R.string.settings_speed_display_desc),
                    checked = settings.speedDisplay,
                    onCheckedChange = { value -> onUpdate { it.copy(speedDisplay = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_delete_confirm),
                    subtitle = stringResource(R.string.settings_delete_confirm_desc),
                    checked = settings.deleteConfirm,
                    onCheckedChange = { value -> onUpdate { it.copy(deleteConfirm = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_two_columns),
                    subtitle = stringResource(R.string.settings_two_columns_desc),
                    checked = settings.twoColumnLayout,
                    onCheckedChange = { value -> onUpdate { it.copy(twoColumnLayout = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = { value -> onUpdate { it.copy(dynamicColor = value) } },
                )
                ValueSetting(
                    title = stringResource(R.string.settings_language),
                    value = languageLabel(settings.language),
                    onClick = { languageOpen = true },
                )
            }
        }

        item {
            Section(
                title = stringResource(R.string.settings_vpn),
                open = vpnOpen,
                onToggle = { vpnOpen = !vpnOpen },
            ) {
                SwitchSetting(
                    title = stringResource(R.string.settings_ipv6),
                    subtitle = stringResource(R.string.settings_ipv6_desc),
                    checked = settings.ipv6,
                    onCheckedChange = { value -> onUpdate { it.copy(ipv6 = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_prefer_ipv6),
                    subtitle = stringResource(R.string.settings_prefer_ipv6_desc),
                    checked = settings.preferIpv6,
                    enabled = settings.ipv6,
                    onCheckedChange = { value -> onUpdate { it.copy(preferIpv6 = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_local_dns),
                    subtitle = stringResource(R.string.settings_local_dns_desc),
                    checked = settings.localDnsEnabled,
                    onCheckedChange = { value -> onUpdate { it.copy(localDnsEnabled = value) } },
                )
                // FakeDNS rewrites every lookup to a synthetic address, which needs the
                // core to answer and un-answer the mapping per connection; this build has
                // no such hook, so the row stays visible but off.
                SwitchSetting(
                    title = stringResource(R.string.settings_fakedns),
                    subtitle = stringResource(R.string.settings_fakedns_unavailable),
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
                TextSetting(
                    title = stringResource(R.string.settings_vpn_dns),
                    subtitle = stringResource(R.string.settings_vpn_dns_desc),
                    value = settings.vpnDns,
                    onApply = { value ->
                        onUpdate { it.copy(vpnDns = value.ifBlank { "1.1.1.1" }) }
                    },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_bypass_lan),
                    subtitle = stringResource(R.string.settings_bypass_lan_desc),
                    checked = settings.bypassLan,
                    onCheckedChange = { value -> onUpdate { it.copy(bypassLan = value) } },
                )
                TextSetting(
                    title = stringResource(R.string.settings_interface_address),
                    subtitle = stringResource(R.string.settings_interface_address_desc),
                    value = settings.tunAddress,
                    onApply = { value ->
                        onUpdate { it.copy(tunAddress = value.ifBlank { "10.10.14.1" }) }
                    },
                )
                Text(
                    text = stringResource(R.string.settings_mtu),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = stringResource(R.string.settings_mtu_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = listOf(
                        AppSettings.MIN_MTU.toString() to stringResource(R.string.settings_mtu_1280),
                        "1500" to stringResource(R.string.settings_mtu_1500),
                        AppSettings.MAX_MTU.toString() to stringResource(R.string.settings_mtu_9000),
                    ),
                    selected = settings.tunMtu.toString(),
                    onSelect = { value ->
                        onUpdate { it.copy(tunMtu = value.toIntOrNull() ?: it.tunMtu) }
                    },
                )
            }
        }

        item {
            Section(
                title = stringResource(R.string.settings_core_section),
                open = coreOpen,
                onToggle = { coreOpen = !coreOpen },
            ) {
                SwitchSetting(
                    title = stringResource(R.string.settings_sniffing),
                    subtitle = stringResource(R.string.settings_sniffing_desc),
                    checked = settings.sniffing,
                    onCheckedChange = { value -> onUpdate { it.copy(sniffing = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_route_only),
                    subtitle = stringResource(R.string.settings_route_only_desc),
                    checked = settings.routeOnly,
                    enabled = settings.sniffing,
                    onCheckedChange = { value -> onUpdate { it.copy(routeOnly = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_local_proxy),
                    subtitle = stringResource(R.string.settings_local_proxy_desc),
                    checked = settings.localProxy,
                    onCheckedChange = { value -> onUpdate { it.copy(localProxy = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_allow_lan),
                    subtitle = stringResource(R.string.settings_allow_lan_desc),
                    checked = settings.allowLan,
                    enabled = settings.localProxy,
                    onCheckedChange = { value -> onUpdate { it.copy(allowLan = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_dynamic_port),
                    subtitle = stringResource(R.string.settings_dynamic_port_desc),
                    checked = settings.dynamicProxyPort,
                    enabled = settings.localProxy,
                    onCheckedChange = { value -> onUpdate { it.copy(dynamicProxyPort = value) } },
                )
                TextSetting(
                    title = stringResource(R.string.settings_proxy_port),
                    subtitle = stringResource(R.string.settings_proxy_port_desc),
                    value = settings.proxyPort.toString(),
                    keyboard = KeyboardType.Number,
                    numeric = true,
                    enabled = settings.localProxy && !settings.dynamicProxyPort,
                    onApply = { value ->
                        val port = value.toIntOrNull()
                            ?.coerceIn(SingBoxConfigBuilder.MIN_PROXY_PORT, SingBoxConfigBuilder.MAX_PROXY_PORT)
                            ?: settings.proxyPort
                        onUpdate { it.copy(proxyPort = port) }
                    },
                )
                TextSetting(
                    title = stringResource(R.string.settings_proxy_user),
                    value = settings.proxyUser,
                    enabled = settings.localProxy,
                    onApply = { value ->
                        onUpdate {
                            it.copy(proxyUser = value.trim(), proxyPass = if (value.isBlank()) "" else it.proxyPass)
                        }
                    },
                )
                TextSetting(
                    title = stringResource(R.string.settings_proxy_pass),
                    value = settings.proxyPass,
                    enabled = settings.localProxy && settings.proxyUser.isNotBlank(),
                    onApply = { value -> onUpdate { it.copy(proxyPass = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_socks_udp),
                    subtitle = stringResource(R.string.settings_socks_udp_desc),
                    checked = settings.socksUdp,
                    onCheckedChange = { value -> onUpdate { it.copy(socksUdp = value) } },
                )
                TextSetting(
                    title = stringResource(R.string.settings_dns_remote),
                    value = settings.remoteDns,
                    onApply = { value ->
                        onUpdate { it.copy(remoteDns = value.ifBlank { "https://1.1.1.1/dns-query" }) }
                    },
                )
                TextSetting(
                    title = stringResource(R.string.settings_dns_local),
                    value = settings.localDns,
                    enabled = settings.localDnsEnabled,
                    onApply = { value -> onUpdate { it.copy(localDns = value.ifBlank { "223.5.5.5" }) } },
                )
                TextSetting(
                    title = stringResource(R.string.settings_dns_direct),
                    subtitle = stringResource(R.string.settings_dns_direct_desc),
                    value = settings.directDns,
                    onApply = { value -> onUpdate { it.copy(directDns = value.trim()) } },
                )
                TextSetting(
                    title = stringResource(R.string.settings_dns_hosts),
                    subtitle = stringResource(R.string.settings_dns_hosts_desc),
                    value = settings.dnsHosts,
                    onApply = { value -> onUpdate { it.copy(dnsHosts = value.trim()) } },
                )
                Text(
                    text = stringResource(R.string.settings_log_level),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = listOf(
                        LogLevel.ERROR.name to stringResource(R.string.settings_log_error),
                        LogLevel.WARN.name to stringResource(R.string.settings_log_warn),
                        LogLevel.INFO.name to stringResource(R.string.settings_log_info),
                        LogLevel.DEBUG.name to stringResource(R.string.settings_log_debug),
                    ),
                    selected = settings.logLevel.name,
                    onSelect = { value ->
                        val level = LogLevel.entries.firstOrNull { it.name == value } ?: settings.logLevel
                        onUpdate { it.copy(logLevel = level) }
                    },
                )
            }
        }

        item {
            Section(
                title = stringResource(R.string.settings_mux),
                open = muxOpen,
                onToggle = { muxOpen = !muxOpen },
            ) {
                SwitchSetting(
                    title = stringResource(R.string.settings_mux_enable),
                    subtitle = stringResource(R.string.settings_mux_enable_desc),
                    checked = settings.muxEnabled,
                    onCheckedChange = { value -> onUpdate { it.copy(muxEnabled = value) } },
                )
                Text(
                    text = stringResource(R.string.settings_mux_streams),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = listOf("4", "8", "16", "32").map { it to it },
                    selected = settings.muxConcurrency.toString(),
                    onSelect = { value ->
                        onUpdate { it.copy(muxConcurrency = value.toIntOrNull() ?: it.muxConcurrency) }
                    },
                )
            }
        }

        item {
            Section(
                title = stringResource(R.string.settings_fragment),
                open = fragmentOpen,
                onToggle = { fragmentOpen = !fragmentOpen },
            ) {
                SwitchSetting(
                    title = stringResource(R.string.settings_fragment_enable),
                    subtitle = stringResource(R.string.settings_fragment_enable_desc),
                    checked = settings.fragmentEnabled,
                    onCheckedChange = { value -> onUpdate { it.copy(fragmentEnabled = value) } },
                )
                TextSetting(
                    title = stringResource(R.string.settings_fragment_size),
                    subtitle = "100-200",
                    value = settings.fragmentSize,
                    onApply = { value -> onUpdate { it.copy(fragmentSize = value.ifBlank { "100-200" }) } },
                )
                TextSetting(
                    title = stringResource(R.string.settings_fragment_interval),
                    subtitle = "10-20",
                    value = settings.fragmentInterval,
                    onApply = { value -> onUpdate { it.copy(fragmentInterval = value.ifBlank { "10-20" }) } },
                )
            }
        }

        item {
            Section(
                title = stringResource(R.string.settings_advanced),
                open = advancedOpen,
                onToggle = { advancedOpen = !advancedOpen },
            ) {
                Text(
                    text = stringResource(R.string.settings_tunnel_mode),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                OptionSetting(
                    title = stringResource(R.string.tunnel_vpn),
                    subtitle = stringResource(R.string.tunnel_vpn_desc),
                    selected = settings.tunnelMode == TunnelMode.VPN,
                    onClick = { onUpdate { it.copy(tunnelMode = TunnelMode.VPN) } },
                )
                OptionSetting(
                    title = stringResource(R.string.tunnel_proxy),
                    subtitle = stringResource(R.string.tunnel_proxy_desc),
                    selected = settings.tunnelMode == TunnelMode.PROXY,
                    onClick = { onUpdate { it.copy(tunnelMode = TunnelMode.PROXY) } },
                )
                Spacer(Modifier.height(6.dp))
                SwitchSetting(
                    title = stringResource(R.string.settings_block_quic),
                    subtitle = stringResource(R.string.settings_block_quic_desc),
                    checked = settings.blockQuic,
                    onCheckedChange = { value -> onUpdate { it.copy(blockQuic = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_auto_connect),
                    subtitle = stringResource(R.string.settings_auto_connect_desc),
                    checked = settings.autoConnect,
                    onCheckedChange = { value -> onUpdate { it.copy(autoConnect = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_auto_refresh),
                    subtitle = stringResource(R.string.settings_auto_refresh_desc),
                    checked = settings.autoRefresh,
                    onCheckedChange = { value -> onUpdate { it.copy(autoRefresh = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = settings.showNotification,
                    onCheckedChange = { value -> onUpdate { it.copy(showNotification = value) } },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_auto_ping),
                    subtitle = stringResource(R.string.settings_auto_ping_desc),
                    checked = settings.autoPing,
                    onCheckedChange = { value -> onUpdate { it.copy(autoPing = value) } },
                )
                Text(
                    text = stringResource(R.string.settings_ping_timeout),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = listOf(
                        "1000" to stringResource(R.string.settings_timeout_1s),
                        "3000" to stringResource(R.string.settings_timeout_3s),
                        "5000" to stringResource(R.string.settings_timeout_5s),
                    ),
                    selected = settings.pingTimeoutMs.toString(),
                    onSelect = { value ->
                        onUpdate { it.copy(pingTimeoutMs = value.toIntOrNull() ?: it.pingTimeoutMs) }
                    },
                )
                Spacer(Modifier.height(10.dp))
                SwitchSetting(
                    title = stringResource(R.string.settings_glass),
                    subtitle = stringResource(R.string.settings_glass_desc),
                    checked = settings.glassEffects,
                    onCheckedChange = { value -> onUpdate { it.copy(glassEffects = value) } },
                )
                Text(
                    text = stringResource(R.string.settings_accent),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentColor.entries.forEach { accent ->
                        AccentSwatch(
                            accent = accent,
                            selected = settings.accent == accent,
                            enabled = !settings.dynamicColor,
                            onClick = { onUpdate { it.copy(accent = accent) } },
                        )
                    }
                }
            }
        }

        item {
            RadxCard {
                Text(
                    text = stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.titleMedium,
                )
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

    if (languageOpen) {
        LanguageDialog(
            selected = settings.language,
            onDismiss = { languageOpen = false },
            onPick = { value ->
                onUpdate { it.copy(language = value) }
                languageOpen = false
            },
        )
    }
}

@Composable
private fun languageLabel(language: String): String = when (language) {
    "en" -> stringResource(R.string.language_en)
    "fa" -> stringResource(R.string.language_fa)
    else -> stringResource(R.string.language_auto)
}

@Composable
private fun Section(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    RadxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = RadxTextSecondary,
            )
        }
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun TextSetting(
    title: String,
    value: String,
    subtitle: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    numeric: Boolean = false,
    enabled: Boolean = true,
    onApply: (String) -> Unit,
) {
    // The field keeps its own draft so typing does not restart the tunnel on every key.
    var draft by rememberSaveable(value) { mutableStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            IconButton(onClick = { onApply(draft) }, enabled = enabled) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.apply),
                    tint = LocalRadxAccent.current.primary,
                )
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { text ->
                draft = if (numeric) text.filter { it.isDigit() }.take(5) else text
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ValueSetting(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = RadxTextSecondary,
        )
    }
}

@Composable
private fun OptionSetting(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = RadxTextSecondary,
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun LanguageDialog(
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                AppSettings.LANGUAGES.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == language, onClick = { onPick(language) })
                        Spacer(Modifier.width(6.dp))
                        Text(text = languageLabel(language), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun AccentSwatch(
    accent: AccentColor,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = accentOf(accent)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(palette.primary.copy(alpha = if (enabled) 1f else 0.35f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    LocalRadxAccent.current.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
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

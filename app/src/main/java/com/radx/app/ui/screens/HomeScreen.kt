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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radx.app.R
import com.radx.app.core.ConnectionState
import com.radx.app.core.TunnelInfo
import com.radx.app.data.Profile
import com.radx.app.data.TunnelMode
import com.radx.app.ui.Formatters
import com.radx.app.ui.UiState
import com.radx.app.ui.components.ConnectButton
import com.radx.app.ui.components.EmptyState
import com.radx.app.ui.components.NodeTypeBadge
import com.radx.app.ui.components.ProfileCard
import com.radx.app.ui.components.RadxCard
import com.radx.app.ui.components.SectionHeader
import com.radx.app.ui.components.StatusPill
import com.radx.app.ui.components.TrafficTile
import com.radx.app.ui.theme.RadxBlue
import com.radx.app.ui.theme.RadxCyan
import com.radx.app.ui.theme.RadxError
import com.radx.app.ui.theme.RadxSuccess
import com.radx.app.ui.theme.RadxTextSecondary
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: UiState,
    onToggle: () -> Unit,
    onAddConfig: () -> Unit,
    onOpenNodes: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onUpdateProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onCopyProfile: (Profile) -> Unit,
    onShareProfile: (Profile) -> Unit,
) {
    val connection = state.connection
    val connectedSince = (connection as? ConnectionState.Connected)?.since ?: 0L
    val elapsed = rememberElapsed(connectedSince)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = stringResource(R.string.about_text),
                        style = MaterialTheme.typography.labelSmall,
                        color = RadxTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(connected = state.connected, busy = state.busy)
            }
        }

        item {
            ConnectButton(
                connected = state.connected,
                busy = state.busy,
                label = stringResource(
                    if (state.connected || state.busy) R.string.disconnect else R.string.connect,
                ),
                onClick = onToggle,
            )
        }

        item {
            StatusSummary(connection = connection, elapsed = elapsed)
        }

        item {
            TunnelStructureCard(info = state.tunnelInfo)
        }

        item {
            ActiveNodeCard(state = state, onOpenNodes = onOpenNodes)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrafficTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ArrowDownward,
                    label = stringResource(R.string.download),
                    value = Formatters.rate(state.traffic.downloadRate),
                    total = Formatters.bytes(state.traffic.totalDownload),
                    tint = RadxCyan,
                )
                TrafficTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ArrowUpward,
                    label = stringResource(R.string.upload),
                    value = Formatters.rate(state.traffic.uploadRate),
                    total = Formatters.bytes(state.traffic.totalUpload),
                    tint = RadxBlue,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onAddConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_btn))
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.profiles),
                trailing = stringResource(R.string.nodes_count, state.selectedProfile?.nodes?.size ?: 0),
            )
        }

        if (state.profiles.isEmpty()) {
            item {
                EmptyState(
                    text = stringResource(R.string.no_profiles),
                    icon = Icons.Rounded.Dns,
                )
            }
        } else {
            items(state.profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    selected = profile.id == state.selectedProfile?.id,
                    busy = state.busyProfileId == profile.id,
                    onSelect = { onSelectProfile(profile.id) },
                    onUpdate = if (profile.isSubscription) {
                        { onUpdateProfile(profile) }
                    } else {
                        null
                    },
                    onDelete = { onDeleteProfile(profile) },
                    onCopy = { onCopyProfile(profile) },
                    onShare = { onShareProfile(profile) },
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatusSummary(connection: ConnectionState, elapsed: String) {
    val failed = connection as? ConnectionState.Failed
    val label = when (connection) {
        is ConnectionState.Connected -> stringResource(R.string.status_connected)
        is ConnectionState.Connecting -> stringResource(R.string.status_connecting)
        is ConnectionState.Disconnecting -> stringResource(R.string.status_disconnecting)
        is ConnectionState.Failed -> stringResource(R.string.status_error)
        else -> stringResource(R.string.status_disconnected)
    }
    val color = when (connection) {
        is ConnectionState.Connected -> RadxSuccess
        is ConnectionState.Failed -> RadxError
        is ConnectionState.Connecting, is ConnectionState.Disconnecting -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subtitle = when {
        failed != null -> failed.message
        connection is ConnectionState.Connected && connection.mode == TunnelMode.PROXY ->
            stringResource(R.string.proxy_running, "127.0.0.1:${connection.proxyPort}")
        connection is ConnectionState.Connected -> stringResource(R.string.protected_vpn)
        else -> stringResource(R.string.not_protected)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = RadxTextSecondary,
            textAlign = TextAlign.Center,
        )
        if (elapsed.isNotEmpty() && connection is ConnectionState.Connected) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.connected_time, elapsed),
                style = MaterialTheme.typography.labelSmall,
                color = RadxTextSecondary,
            )
        }
    }
}

@Composable
private fun ActiveNodeCard(state: UiState, onOpenNodes: () -> Unit) {
    val profile = state.selectedProfile
    val node = profile?.activeNode
    RadxCard(onClick = onOpenNodes) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.current_node),
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = node?.name ?: stringResource(R.string.no_node_selected),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile != null) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = RadxTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (node != null) {
                NodeTypeBadge(node.type)
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.change_node),
                tint = RadxTextSecondary,
            )
        }
    }
}

@Composable
private fun TunnelStructureCard(info: TunnelInfo?) {
    if (info == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    RadxCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Router,
                contentDescription = null,
                tint = RadxCyan,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tunnel_structure),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        if (info.isVpn) R.string.tunnel_vpn else R.string.tunnel_proxy,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(R.string.more),
                tint = RadxTextSecondary,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (info.isVpn) {
            DetailRow(
                label = stringResource(R.string.tunnel_interface),
                value = info.addressLines.joinToString(" · ").ifEmpty {
                    stringResource(R.string.tunnel_not_ready)
                },
            )
            DetailRow(
                label = stringResource(R.string.tunnel_routes),
                value = info.routeLines.joinToString(" · ").ifEmpty {
                    stringResource(R.string.tunnel_routes_auto)
                },
            )
            if (expanded) {
                DetailRow(stringResource(R.string.tunnel_mtu), info.mtu.toString())
                DetailRow(
                    label = stringResource(R.string.tunnel_dns),
                    value = info.dnsServers.joinToString(" · ").ifEmpty {
                        stringResource(R.string.tunnel_dns_system)
                    },
                )
                DetailRow(
                    label = stringResource(R.string.tunnel_routing),
                    value = stringResource(
                        R.string.tunnel_routing_value,
                        onOff(info.autoRoute),
                        onOff(info.strictRoute),
                    ),
                )
                DetailRow(
                    label = stringResource(R.string.tunnel_apps),
                    value = info.excludedApps.joinToString(" · ").ifEmpty {
                        stringResource(R.string.tunnel_apps_none)
                    },
                )
            }
        } else {
            DetailRow(
                label = stringResource(R.string.tunnel_proxy_address),
                value = "127.0.0.1:${info.proxyPort}",
            )
        }

        if (info.xrayNodes > 0) {
            DetailRow(
                label = stringResource(R.string.tunnel_xray_bridge),
                value = stringResource(
                    R.string.tunnel_xray_value,
                    info.xrayNodes,
                    "${info.xrayPorts.first()}–${info.xrayPorts.last()}",
                ),
            )
        }

        DetailRow(stringResource(R.string.tunnel_core), info.core)
    }
}

@Composable
private fun onOff(value: Boolean): String =
    stringResource(if (value) R.string.tunnel_on else R.string.tunnel_off)

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = RadxTextSecondary,
            modifier = Modifier.width(112.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun rememberElapsed(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        if (since <= 0L) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return if (since <= 0L) "" else Formatters.duration(now - since)
}

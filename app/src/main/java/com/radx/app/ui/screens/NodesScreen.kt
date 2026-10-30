package com.radx.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radx.app.R
import com.radx.app.data.ProxyNode
import com.radx.app.ui.NodeSort
import com.radx.app.ui.UiState
import com.radx.app.ui.sortedByPing
import com.radx.app.net.TrafficProbe
import com.radx.app.ui.components.DelayLabel
import com.radx.app.ui.components.EmptyState
import com.radx.app.ui.components.GlassRow
import com.radx.app.ui.components.GradientTitle
import com.radx.app.ui.components.NodeTypeBadge
import com.radx.app.ui.components.TrafficLabel
import com.radx.app.ui.theme.LocalRadxAccent
import com.radx.app.ui.theme.RadxCyan
import com.radx.app.ui.theme.RadxTextSecondary

@Composable
fun NodesScreen(
    state: UiState,
    onSelect: (String) -> Unit,
    onPing: () -> Unit,
    twoColumns: Boolean = false,
) {
    val profile = state.selectedProfile
    var query by rememberSaveable { mutableStateOf("") }
    var autoPinged by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(NodeSort.DEFAULT) }

    LaunchedEffect(profile?.id, state.settings.autoPing) {
        if (profile != null && !autoPinged && state.settings.autoPing) {
            autoPinged = true
            onPing()
        }
    }

    val nodes = remember(profile?.nodes, query, sort, state.delays) {
        profile?.nodes.orEmpty()
            .filter { node ->
                query.isBlank() ||
                    node.name.contains(query, ignoreCase = true) ||
                    node.server.contains(query, ignoreCase = true) ||
                    node.type.contains(query, ignoreCase = true)
            }
            .sortedByPing(state.delays, sort)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GradientTitle(text = stringResource(R.string.nodes_title))
                Text(
                    text = profile?.name ?: stringResource(R.string.no_profiles),
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onPing, enabled = !state.pinging && profile != null) {
                if (state.pinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = RadxCyan,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (state.pinging) R.string.pinging else R.string.ping_all))
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_nodes)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = RadxTextSecondary,
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        if (profile != null && nodes.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { sort = sort.next },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sort_by_ping, stringResource(sort.labelRes)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        when {
            profile == null -> EmptyState(
                text = stringResource(R.string.no_profiles),
                icon = Icons.Rounded.Dns,
            )

            nodes.isEmpty() -> EmptyState(
                text = stringResource(R.string.no_nodes),
                icon = Icons.Rounded.Dns,
            )

            twoColumns -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(nodes, key = { it.tag }) { node ->
                    NodeRow(
                        node = node,
                        selected = node.tag == profile.selectedTag,
                        delay = state.delays[node.tag],
                        pinging = state.pinging && state.delays[node.tag] == null,
                        traffic = state.trafficResults[node.name],
                        trafficChecking = state.trafficCheckingNode == node.name,
                        onClick = { onSelect(node.tag) },
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(nodes, key = { it.tag }) { node ->
                    NodeRow(
                        node = node,
                        modifier = Modifier.animateItem(),
                        selected = node.tag == profile.selectedTag,
                        delay = state.delays[node.tag],
                        pinging = state.pinging && state.delays[node.tag] == null,
                        // Only a node the app has actually pushed data through gets a
                        // verdict; the rest keep the handshake measurement.
                        traffic = state.trafficResults[node.name],
                        trafficChecking = state.trafficCheckingNode == node.name,
                        onClick = { onSelect(node.tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    node: ProxyNode,
    modifier: Modifier = Modifier,
    selected: Boolean,
    delay: Int?,
    pinging: Boolean,
    traffic: TrafficProbe.Result?,
    trafficChecking: Boolean,
    onClick: () -> Unit,
) {
    GlassRow(modifier = modifier, selected = selected, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        LocalRadxAccent.current.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NodeTypeBadge(node.type)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = node.server,
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        // For the node that is actually up, a real transfer outranks the handshake probe.
        if (trafficChecking || traffic != null) {
            TrafficLabel(result = traffic, checking = trafficChecking)
        } else {
            DelayLabel(delay = delay, pinging = pinging)
        }
    }
}

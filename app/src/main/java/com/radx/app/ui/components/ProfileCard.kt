package com.radx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radx.app.R
import com.radx.app.data.Profile
import com.radx.app.ui.Formatters
import com.radx.app.ui.theme.RadxCyan
import com.radx.app.ui.theme.RadxError
import com.radx.app.ui.theme.RadxTextSecondary

@Composable
fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onUpdate: (() -> Unit)?,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    GlassRow(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        selected = selected,
        onClick = onSelect,
    ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) RadxCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (profile.isSubscription) R.string.subscription_label else R.string.local_label,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = RadxTextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.nodes_count, profile.nodes.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = RadxTextSecondary,
                    )
                    if (profile.updatedAt > 0L) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadxTextSecondary,
                        )
                        Text(
                            text = Formatters.timestamp(profile.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = RadxTextSecondary,
                        )
                    }
                }
                val active = profile.activeNode
                if (active != null) {
                    Text(
                        text = active.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = RadxCyan,
                )
                Spacer(Modifier.width(10.dp))
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more),
                        tint = RadxTextSecondary,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    onUpdate?.let { update ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.update_subscription)) },
                            onClick = {
                                menuOpen = false
                                update()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.qr_show)) },
                        onClick = {
                            menuOpen = false
                            onShowQr()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_config)) },
                        onClick = {
                            menuOpen = false
                            onCopy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        onClick = {
                            menuOpen = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = RadxError) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
            }
        }
    }
}

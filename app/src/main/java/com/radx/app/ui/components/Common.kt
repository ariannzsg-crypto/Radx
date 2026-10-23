package com.radx.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radx.app.R
import com.radx.app.ui.Formatters
import com.radx.app.ui.theme.RadxCyan
import com.radx.app.ui.theme.RadxError
import com.radx.app.ui.theme.RadxSuccess
import com.radx.app.ui.theme.RadxTextSecondary
import com.radx.app.ui.theme.RadxWarning

@Composable
fun RadxCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = RadxTextSecondary,
            )
        }
    }
}

@Composable
fun StatusPill(connected: Boolean, busy: Boolean) {
    val color = when {
        connected -> RadxSuccess
        busy -> RadxWarning
        else -> RadxTextSecondary
    }
    val label = when {
        connected -> stringResource(R.string.status_connected)
        busy -> stringResource(R.string.status_connecting)
        else -> stringResource(R.string.status_disconnected)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun TrafficTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    total: String,
    tint: Color,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = total,
                    style = MaterialTheme.typography.labelSmall,
                    color = RadxTextSecondary,
                )
            }
        }
    }
}

@Composable
fun NodeTypeBadge(type: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = Formatters.protocolLabel(type),
            style = MaterialTheme.typography.labelSmall,
            color = RadxCyan,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun DelayLabel(delay: Int?, pinging: Boolean) {
    when {
        pinging -> CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = RadxCyan,
        )

        delay == null -> Text(
            text = stringResource(R.string.node_delay_timeout),
            style = MaterialTheme.typography.labelSmall,
            color = RadxTextSecondary,
        )

        else -> Text(
            text = stringResource(R.string.node_delay_ms, delay),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                delay <= 250 -> RadxSuccess
                delay <= 600 -> RadxWarning
                else -> RadxError
            },
        )
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = RadxTextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = RadxTextSecondary,
        )
    }
}

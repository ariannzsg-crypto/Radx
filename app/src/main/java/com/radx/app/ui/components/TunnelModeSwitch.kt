package com.radx.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radx.app.R
import com.radx.app.data.TunnelMode
import com.radx.app.ui.theme.RadxAccent
import com.radx.app.ui.theme.LocalRadxAccent
import com.radx.app.ui.theme.RadxTextSecondary

/**
 * The tunnel mode, as the two-way glass switch the home tab uses.
 *
 * It replaces a trip to the settings for the one choice people change most: whether the
 * device goes through a tun or a local port. The fill slides in with a colour animation
 * instead of swapping, so the state change is visible.
 */
@Composable
fun TunnelModeSwitch(
    selected: TunnelMode,
    enabled: Boolean,
    onSelect: (TunnelMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalRadxAccent.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), shape)
            .padding(5.dp),
    ) {
        ModeOption(
            title = stringResource(R.string.mode_proxy_short),
            icon = Icons.Rounded.SwapHoriz,
            active = selected == TunnelMode.PROXY,
            enabled = enabled,
            accent = accent,
            modifier = Modifier.weight(1f),
        ) { onSelect(TunnelMode.PROXY) }
        ModeOption(
            title = stringResource(R.string.mode_vpn_short),
            icon = Icons.Rounded.Shield,
            active = selected == TunnelMode.VPN,
            enabled = enabled,
            accent = accent,
            modifier = Modifier.weight(1f),
        ) { onSelect(TunnelMode.VPN) }
    }
}

@Composable
private fun ModeOption(
    title: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    accent: RadxAccent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (active) accent.primary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = tween(260),
        label = "mode_background",
    )
    val content by animateColorAsState(
        targetValue = when {
            active -> accent.primary
            enabled -> RadxTextSecondary
            else -> RadxTextSecondary.copy(alpha = 0.45f)
        },
        animationSpec = tween(260),
        label = "mode_content",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(enabled = enabled && !active, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

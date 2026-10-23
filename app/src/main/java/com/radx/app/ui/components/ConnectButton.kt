package com.radx.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.radx.app.ui.theme.RadxBlue
import com.radx.app.ui.theme.RadxCyan
import com.radx.app.ui.theme.RadxSurfaceElevated
import com.radx.app.ui.theme.RadxTextPrimary

@Composable
fun ConnectButton(
    connected: Boolean,
    busy: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "connect_pulse")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "connect_rotation",
    )
    val glow by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(600),
        label = "connect_glow",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(268.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val stroke = 11.dp.toPx()
            val radius = size.minDimension / 2 - stroke
            drawCircle(
                color = RadxCyan.copy(alpha = 0.10f * glow),
                radius = radius - 10.dp.toPx(),
            )
            drawCircle(
                color = Color(0xFF1B2A42),
                radius = radius,
                style = Stroke(width = stroke),
            )
            if (connected || busy) {
                rotate(if (busy) rotation else 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, RadxBlue, RadxCyan, Color.Transparent),
                        ),
                        startAngle = -90f,
                        sweepAngle = 300f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(stroke, stroke),
                        size = Size(size.width - stroke * 2, size.height - stroke * 2),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(172.dp)
                .clip(CircleShape)
                .background(
                    if (connected) {
                        Brush.linearGradient(listOf(RadxCyan, RadxBlue))
                    } else {
                        Brush.linearGradient(listOf(RadxSurfaceElevated, Color(0xFF121C2D)))
                    },
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = label,
                    tint = if (connected) Color(0xFF04222B) else RadxCyan,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (connected) Color(0xFF04222B) else RadxTextPrimary,
                )
            }
        }
    }
}

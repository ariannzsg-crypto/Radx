package com.radx.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.radx.app.ui.theme.LocalRadxAccent
import com.radx.app.ui.theme.RadxSurfaceElevated
import com.radx.app.ui.theme.RadxTextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The tunnel switch, drawn as a cloud.
 *
 * Idle it is a dim, quiet cloud with the power mark in it. Connected it becomes a
 * thundercloud: the accent gradient fills it, lightning flickers inside on an irregular
 * rhythm, and small sprites drift around it. Connecting keeps the cloud unlit and spins
 * the arc, so the three states are readable at a glance instead of by label only.
 */
@Composable
fun ConnectButton(
    connected: Boolean,
    busy: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalRadxAccent.current
    val glass = LocalGlassEffects.current
    // Read outside the canvas: the draw scope is not a composable context.
    val outline = MaterialTheme.colorScheme.outline

    val storm = rememberInfiniteTransition(label = "storm")
    val rotation by storm.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "storm_rotation",
    )
    // Two flashes close together, then a pause: an even pulse would read as a beacon,
    // not as lightning.
    val strike by storm.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2900
                0f at 0 using LinearEasing
                0.9f at 80
                0.1f at 150
                1f at 220
                0.2f at 300
                0f at 430
                0f at 2900
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "storm_strike",
    )
    val lit by animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = tween(650),
        label = "cloud_lit",
    )
    // A slow breath keeps the idle cloud alive without asking for attention.
    val breathe by storm.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.008f,
        animationSpec = infiniteRepeatable(
            tween(4600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "cloud_breathe",
    )
    val flash = if (connected) strike else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(268.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                },
        ) {
            val cloudWidth = size.width * 0.96f
            val cloudHeight = size.height * 0.66f
            val left = (size.width - cloudWidth) / 2f
            val top = (size.height - cloudHeight) / 2f

            // Halo: quiet while idle, a lit storm sky once the tunnel is up.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.primary.copy(alpha = 0.16f * lit + 0.16f * flash),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension * 0.54f,
                ),
                radius = size.minDimension * 0.54f,
            )

            // Sprites around the cloud, the way the reference shows charge in the air.
            if (lit > 0.05f) {
                val sprites = 16
                repeat(sprites) { index ->
                    val phase = index / sprites.toFloat() * 2f * PI.toFloat()
                    val drift = sin(phase + rotation / 60f) * 6.dp.toPx()
                    val orbit = size.minDimension * (0.44f + 0.04f * ((index % 3) / 2f)) + drift
                    val dot = Offset(
                        x = center.x + cos(phase) * orbit,
                        y = center.y + sin(phase) * orbit * 0.86f,
                    )
                    drawCircle(
                        color = accent.primary.copy(
                            alpha = (0.10f + 0.45f * flash) * lit * if (index % 2 == 0) 1f else 0.6f,
                        ),
                        radius = (1.2f + 1.6f * flash) * density,
                        center = dot,
                    )
                }
            }

            val cloud = cloudPath(cloudWidth, cloudHeight).apply { translate(Offset(left, top)) }

            drawPath(
                path = cloud,
                brush = when {
                    lit > 0.02f -> Brush.verticalGradient(
                        colors = listOf(
                            lerp(cloudIdleTop(glass), accent.primary, lit),
                            lerp(cloudIdleBottom(glass), accent.secondary, lit),
                        ),
                        startY = top,
                        endY = top + cloudHeight,
                    )

                    else -> Brush.verticalGradient(
                        colors = listOf(cloudIdleTop(glass), cloudIdleBottom(glass)),
                        startY = top,
                        endY = top + cloudHeight,
                    )
                },
            )
            // The whole cloud brightens for a moment on each strike.
            if (flash > 0.01f) {
                drawPath(path = cloud, color = Color.White.copy(alpha = 0.28f * flash))
            }
            drawPath(
                path = cloud,
                color = if (lit > 0.5f) {
                    Color.White.copy(alpha = 0.35f)
                } else if (glass) {
                    Color(0x47FFFFFF)
                } else {
                    outline
                },
                style = Stroke(width = 1.4.dp.toPx()),
            )

            if (busy) {
                rotate(rotation) {
                    val stroke = 5.dp.toPx()
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, accent.secondary, accent.primary, Color.Transparent),
                        ),
                        startAngle = -90f,
                        sweepAngle = 280f,
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
                .size(232.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (connected) Icons.Rounded.Bolt else Icons.Rounded.PowerSettingsNew,
                    contentDescription = label,
                    tint = if (lit > 0.5f) accent.onPrimary else accent.primary,
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            alpha = if (connected) 0.72f + 0.28f * flash else 1f
                            val pop = 1f + 0.07f * flash
                            scaleX = pop
                            scaleY = pop
                        },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (lit > 0.5f) accent.onPrimary else RadxTextPrimary,
                )
            }
        }
    }
}

/** Flat cloud when the interface is not glassy, a frosted one when it is. */
private fun cloudIdleTop(glass: Boolean): Color =
    if (glass) Color(0x2EFFFFFF) else RadxSurfaceElevated

private fun cloudIdleBottom(glass: Boolean): Color =
    if (glass) Color(0x0DFFFFFF) else Color(0xFF121C2D)

private fun lerp(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction.coerceIn(0f, 1f))

/**
 * A cloud as one outline: a rounded base with three lobes merged into it.
 *
 * Merging (instead of drawing four overlapping shapes) gives the border a single
 * continuous edge, which is what keeps the stroke from showing the seams.
 */
private fun cloudPath(width: Float, height: Float): Path {
    val merged = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(width * 0.05f, height * 0.42f, width * 0.95f, height * 0.84f),
                cornerRadius = CornerRadius(height * 0.21f),
            ),
        )
    }
    listOf(
        Triple(width * 0.32f, height * 0.47f, height * 0.24f),
        Triple(width * 0.51f, height * 0.30f, height * 0.30f),
        Triple(width * 0.71f, height * 0.48f, height * 0.22f),
    ).forEach { (cx, cy, radius) ->
        val lobe = Path().apply {
            addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
        }
        val next = Path()
        next.op(merged, lobe, PathOperation.Union)
        merged.reset()
        merged.addPath(next)
    }
    return merged
}

package com.radx.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
 * The tunnel switch, drawn as a floating, sculpted cloud.
 *
 * The cloud never lands: it drifts up and down on a slow cycle while rocking a few
 * degrees in each axis, so the perspective keeps shifting. The pool of shadow underneath
 * stays on the ground and opens up as the cloud lifts, which is what sells the float.
 *
 * Depth is built up in layers rather than with a flat silhouette: a shaded slab behind
 * the face gives the body thickness, every puff gets its own highlight and contact shadow
 * so the lobes read as spheres, the belly darkens toward the bottom, and the rim light is
 * bright at the top and faint where it meets the shadow.
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
            tween(4600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "cloud_breathe",
    )
    // The hover cycle: 0 is the cloud at its lowest, 1 at its highest.
    val hover by storm.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "cloud_hover",
    )
    // A lazy rock in both axes, out of phase, so the shading on the volume moves.
    val rockX by storm.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(5200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "cloud_rock_x",
    )
    val rockY by storm.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            tween(6600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "cloud_rock_y",
    )
    // Negative moves the whole group up. The shadow compensates for this below so it
    // stays put while the cloud rises away from it. Kept in dp and converted where the
    // density scope is available: both the layer and the draw scope need pixels.
    val floatY = 9.dp * (0.5f - hover) * 2f
    val flash = if (connected) strike else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(268.dp)
            .graphicsLayer { translationY = floatY.toPx() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                    rotationX = rockX * 5f
                    rotationY = rockY * 4f
                    cameraDistance = 11f * density
                },
        ) {
            val cloudWidth = size.width * 0.96f
            val cloudHeight = size.height * 0.66f
            val left = (size.width - cloudWidth) / 2f
            val top = (size.height - cloudHeight) / 2f
            // The ground the shadow rests on, held still while the cloud hovers.
            val ground = size.height * 0.82f
            val shadowY = ground - floatY.toPx()
            val shadowReach = cloudWidth * 0.21f * (1f - 0.16f * hover)
            val shadowAlpha = 0.30f - 0.13f * hover

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

            // Ground shadow: three overlapping pools read as one soft, wide ellipse.
            // It shrinks and fades as the cloud climbs, which is what makes the hover
            // feel like height instead of a translation.
            listOf(-1f, 0f, 1f).forEach { side ->
                val spot = Offset(center.x + side * shadowReach, shadowY)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = shadowAlpha * if (side == 0f) 1f else 0.8f),
                            Color.Transparent,
                        ),
                        center = spot,
                        radius = shadowReach,
                    ),
                    radius = shadowReach,
                    center = spot,
                )
            }
            if (lit > 0.05f) {
                // The thundercloud throws its own glow onto the ground under it.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.primary.copy(alpha = 0.14f + 0.22f * flash),
                            Color.Transparent,
                        ),
                        center = Offset(center.x, shadowY),
                        radius = shadowReach * 1.4f,
                    ),
                    radius = shadowReach * 1.4f,
                    center = Offset(center.x, shadowY),
                )
            }

            val cloud = cloudPath(cloudWidth, cloudHeight).apply { translate(Offset(left, top)) }

            // Thickness: a shaded slab drawn behind the face, so the silhouette has a
            // body instead of being a sticker.
            val depth = 7.dp.toPx()
            val slabPath = Path().apply {
                addPath(cloud)
                translate(Offset(0f, depth))
            }
            drawPath(
                path = slabPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lerp(
                            from = if (glass) Color(0x8A05070E) else Color(0xFF05070E),
                            to = lerp(accent.secondary, Color.Black, 0.55f),
                            lit,
                        ),
                        lerp(
                            from = if (glass) Color(0x5903050A) else Color(0xFF03050A),
                            to = lerp(accent.secondary, Color.Black, 0.78f),
                            lit,
                        ),
                    ),
                    startY = top,
                    endY = top + cloudHeight + depth,
                ),
            )

            // The face: light at the crown, mid tone in the belly, deep shade at the base.
            drawPath(
                path = cloud,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lerp(cloudIdleTop(glass), lerp(accent.primary, Color.White, 0.30f), lit),
                        lerp(cloudIdleMid(glass), accent.primary, lit),
                        lerp(cloudIdleBottom(glass), lerp(accent.secondary, Color.Black, 0.28f), lit),
                    ),
                    startY = top,
                    endY = top + cloudHeight,
                ),
            )

            // Every puff lit from the upper left and shaded where it meets the body.
            // Each pool fades to nothing inside its own lobe, so no seam can show.
            lobes(cloudWidth, cloudHeight).forEach { (cx, cy, radius) ->
                val puff = Offset(left + cx, top + cy)
                val litFrom = Offset(puff.x - radius * 0.22f, puff.y - radius * 0.26f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f + 0.20f * lit),
                            Color.Transparent,
                        ),
                        center = litFrom,
                        radius = radius * 0.66f,
                    ),
                    radius = radius,
                    center = puff,
                )
                val restingOn = Offset(puff.x + radius * 0.10f, puff.y + radius * 0.40f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f - 0.10f * lit),
                            Color.Transparent,
                        ),
                        center = restingOn,
                        radius = radius * 0.58f,
                    ),
                    radius = radius,
                    center = puff,
                )
            }
            // Round out the belly so the base curves away from the light too.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.26f),
                        Color.Transparent,
                    ),
                    center = Offset(left + cloudWidth * 0.5f, top + cloudHeight * 0.64f),
                    radius = cloudWidth * 0.14f,
                ),
                radius = cloudWidth * 0.14f,
                center = Offset(left + cloudWidth * 0.5f, top + cloudHeight * 0.64f),
            )
            // A small specular on the crown: the one cue that says the surface is wet.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.30f + 0.35f * lit) * (0.55f + 0.45f * lit)),
                        Color.Transparent,
                    ),
                    center = Offset(left + cloudWidth * 0.51f, top + cloudHeight * 0.24f),
                    radius = cloudWidth * 0.055f,
                ),
                radius = cloudWidth * 0.055f,
                center = Offset(left + cloudWidth * 0.51f, top + cloudHeight * 0.24f),
            )

            // The whole cloud brightens for a moment on each strike.
            if (flash > 0.01f) {
                drawPath(path = cloud, color = Color.White.copy(alpha = 0.28f * flash))
            }
            // Rim light: bright along the crown, almost gone where the body meets shadow.
            drawPath(
                path = cloud,
                brush = when {
                    lit > 0.5f -> Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.12f),
                        ),
                        startY = top,
                        endY = top + cloudHeight,
                    )

                    glass -> Brush.verticalGradient(
                        colors = listOf(Color(0x66FFFFFF), Color(0x1FFFFFFF)),
                        startY = top,
                        endY = top + cloudHeight,
                    )

                    else -> Brush.verticalGradient(
                        colors = listOf(Color(0x33FFFFFF), outline.copy(alpha = 0.55f)),
                        startY = top,
                        endY = top + cloudHeight,
                    )
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

private fun cloudIdleMid(glass: Boolean): Color =
    if (glass) Color(0x1AFFFFFF) else Color(0xFF0E1728)

private fun cloudIdleBottom(glass: Boolean): Color =
    if (glass) Color(0x0DFFFFFF) else Color(0xFF121C2D)

private fun lerp(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction.coerceIn(0f, 1f))

/**
 * The three lobes as (center x, center y, radius) in the cloud's own coordinate space.
 *
 * Shared by the outline and by the shading, so every highlight sits exactly on the puff
 * it belongs to.
 */
private fun lobes(width: Float, height: Float): List<Triple<Float, Float, Float>> = listOf(
    Triple(width * 0.32f, height * 0.47f, height * 0.24f),
    Triple(width * 0.51f, height * 0.30f, height * 0.30f),
    Triple(width * 0.71f, height * 0.48f, height * 0.22f),
)

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
    lobes(width, height).forEach { (cx, cy, radius) ->
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

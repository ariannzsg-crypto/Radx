package com.radx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radx.app.ui.theme.LocalRadxAccent
import com.radx.app.ui.theme.RadxBackground
import com.radx.app.ui.theme.RadxTextSecondary

/** Whether surfaces are frosted and translucent instead of flat. */
val LocalGlassEffects = staticCompositionLocalOf { true }

private val GlassHighlight = listOf(Color(0x1AFFFFFF), Color(0x05FFFFFF))
private val GlassEdge = listOf(Color(0x38FFFFFF), Color(0x0DFFFFFF))

/**
 * Ambient backdrop.
 *
 * The glass panels are translucent, so what reads as "glass" is what sits behind them:
 * a near-black base with soft accent glows that bleed through every card.
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlassEffects.current
    val accent = LocalRadxAccent.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RadxBackground)
            .drawBehind {
                if (!glass) return@drawBehind
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.primary.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.04f),
                        radius = size.width * 0.85f,
                    ),
                    radius = size.width * 0.85f,
                    center = Offset(size.width * 0.18f, size.height * 0.04f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.secondary.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.38f),
                        radius = size.width * 0.8f,
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.95f, size.height * 0.38f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.glow.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 0.92f),
                        radius = size.width,
                    ),
                    radius = size.width,
                    center = Offset(size.width * 0.25f, size.height * 0.92f),
                )
            },
        content = content,
    )
}

/** A frosted panel: translucent fill, a light edge and the backdrop showing through. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlassEffects.current
    val accent = LocalRadxAccent.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = if (glass) {
                    Brush.linearGradient(
                        colors = GlassHighlight + accent.primary.copy(alpha = 0.03f),
                    )
                } else {
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
                },
            )
            .border(
                width = 1.dp,
                brush = if (glass) {
                    Brush.linearGradient(GlassEdge)
                } else {
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        ),
                    )
                },
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        content = content,
    )
}

/**
 * Floating bottom navigation.
 *
 * A single glass pill instead of Material's opaque bar, so the backdrop stays visible
 * under and around it.
 */
@Composable
fun GlassNavBar(
    items: List<Pair<Int, ImageVector>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalRadxAccent.current
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF)),
                ),
            )
            .border(1.dp, Brush.linearGradient(GlassEdge), shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (titleRes, icon) ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (active) {
                            Brush.verticalGradient(
                                listOf(accent.primary.copy(alpha = 0.22f), accent.secondary.copy(alpha = 0.10f)),
                            )
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(titleRes),
                    tint = if (active) accent.primary else RadxTextSecondary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) accent.primary else RadxTextSecondary,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
            if (index != items.lastIndex) Spacer(Modifier.width(2.dp))
        }
    }
}

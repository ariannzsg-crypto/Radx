package com.radx.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shared timing, so every surface enters in the same rhythm instead of each screen
 * inventing its own duration.
 */
object Motion {
    const val ENTER_MS = 320
    const val STAGGER_MS = 45L

    /** Beyond this the whole list would crawl in; the rest just appear with the group. */
    const val MAX_STAGGER = 6
}

/**
 * Fades and lifts [content] in once, [index] steps late.
 *
 * The content is composed from the first frame and only its alpha/offset animate, so a
 * list does not reflow as each row grows in. The "already played" flag is saveable, so
 * scrolling an item out and back or rotating does not replay the entrance.
 */
@Composable
fun Appear(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var played by rememberSaveable { mutableStateOf(false) }
    var shown by remember { mutableStateOf(played) }
    LaunchedEffect(Unit) {
        if (!played) {
            played = true
            delay(index.coerceIn(0, Motion.MAX_STAGGER) * Motion.STAGGER_MS)
            shown = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = FastOutSlowInEasing),
        label = "appear",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 20.dp.toPx()
        },
    ) {
        content()
    }
}

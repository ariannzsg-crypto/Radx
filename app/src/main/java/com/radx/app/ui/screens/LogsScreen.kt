package com.radx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radx.app.R
import com.radx.app.core.CoreLog
import com.radx.app.core.LogEntry
import com.radx.app.core.LogSource
import com.radx.app.ui.components.GradientTitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live tail of both core logs.
 *
 * The list follows the newest line while the user has not scrolled away, and every
 * line can be copied out as text so a stuck connection can be reported.
 */
@Composable
fun LogsScreen(
    onCopy: (String) -> Unit,
) {
    val entries by CoreLog.snapshot.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL.name) }
    var follow by rememberSaveable { mutableStateOf(true) }

    val selected = LogFilter.valueOf(filter)
    val visible = remember(entries, selected) { selected.apply(entries) }
    val listState = rememberLazyListState()

    LaunchedEffect(visible.size, follow) {
        if (follow && visible.isNotEmpty()) {
            listState.scrollToItem(visible.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientTitle(
                text = stringResource(R.string.logs_title),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { follow = !follow }) {
                Icon(
                    imageVector = Icons.Rounded.VerticalAlignBottom,
                    contentDescription = stringResource(R.string.logs_follow),
                    tint = if (follow) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(
                onClick = { onCopy(visible.joinToString("\n") { it.asLine() }) },
                enabled = visible.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.logs_copy),
                )
            }
            IconButton(
                onClick = { CoreLog.clear() },
                enabled = entries.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.logs_clear),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogFilter.entries.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { filter = option.name },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }

        if (visible.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
            ) {
                itemsIndexed(visible) { _, entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val accent = when (entry.level) {
        "error" -> MaterialTheme.colorScheme.error
        "warn" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = entry.formattedTime(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (entry.source == LogSource.XRAY) " X " else " S ",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = accent,
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = accent,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class LogFilter(val labelRes: Int) {
    ALL(R.string.logs_filter_all),
    SING_BOX(R.string.logs_filter_singbox),
    XRAY(R.string.logs_filter_xray),
    ERRORS(R.string.logs_filter_errors),
    ;

    fun apply(entries: List<LogEntry>): List<LogEntry> = when (this) {
        ALL -> entries
        SING_BOX -> entries.filter { it.source == LogSource.SING_BOX }
        XRAY -> entries.filter { it.source == LogSource.XRAY }
        ERRORS -> entries.filter { it.level == "error" }
    }
}

private fun LogEntry.formattedTime(): String =
    timeFormat.format(Date(at))

private fun LogEntry.asLine(): String {
    val tag = if (source == LogSource.XRAY) "xray" else "sing-box"
    return "${formattedTime()} [$tag] $message"
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

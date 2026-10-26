package com.radx.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which core wrote a log line. */
enum class LogSource { SING_BOX, XRAY }

data class LogEntry(
    val at: Long,
    val source: LogSource,
    val level: String,
    val message: String,
)

/**
 * In-memory tail of both core logs.
 *
 * sing-box writes through [com.radx.app.libbox]'s platform callback and Xray through
 * its own process output, so both land here and the log screen can show them
 * together. Only the last [MAX_ENTRIES] lines are kept; the Xray process also keeps
 * a file on disk for error reports.
 */
object CoreLog {

    const val MAX_ENTRIES = 600

    private val entries = ArrayDeque<LogEntry>()
    private val _snapshot = MutableStateFlow<List<LogEntry>>(emptyList())

    val snapshot: StateFlow<List<LogEntry>> = _snapshot.asStateFlow()

    fun add(source: LogSource, message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        val entry = LogEntry(System.currentTimeMillis(), source, levelOf(text), text)
        synchronized(entries) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            _snapshot.value = entries.toList()
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
            _snapshot.value = emptyList()
        }
    }

    /** Both cores prefix their lines with the level, so it only has to be scraped. */
    private fun levelOf(message: String): String {
        val upper = message.uppercase()
        return when {
            "FATAL" in upper || "ERROR" in upper || "FAILED" in upper -> "error"
            "WARN" in upper -> "warn"
            else -> "info"
        }
    }
}

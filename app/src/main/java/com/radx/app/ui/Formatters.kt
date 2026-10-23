package com.radx.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    fun bytes(value: Long): String {
        if (value <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var index = 0
        while (size >= 1024.0 && index < units.lastIndex) {
            size /= 1024.0
            index++
        }
        return if (index == 0) {
            "${size.toLong()} ${units[index]}"
        } else {
            String.format(Locale.US, "%.1f %s", size, units[index])
        }
    }

    fun rate(bytesPerSecond: Long): String = "${bytes(bytesPerSecond)}/s"

    fun duration(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0L))
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun timestamp(millis: Long): String {
        if (millis <= 0L) return "—"
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(millis))
    }

    fun protocolLabel(type: String): String = type.uppercase(Locale.US)
}

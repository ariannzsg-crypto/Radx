package com.radx.app.core

import android.net.TrafficStats
import android.os.Process

/**
 * Approximate upload/download accounting.
 *
 * In VPN mode the core runs inside this process, so the app's own per-UID byte
 * counters track tunnel traffic closely. Rates are computed from deltas between
 * samples, which is what the UI shows while connected.
 */
class TrafficMonitor {

    data class Snapshot(
        val uploadRate: Long = 0L,
        val downloadRate: Long = 0L,
        val totalUpload: Long = 0L,
        val totalDownload: Long = 0L,
    )

    private var lastSampleAt: Long = 0L
    private var lastTx: Long = 0L
    private var lastRx: Long = 0L
    private var baselineTx: Long = 0L
    private var baselineRx: Long = 0L

    fun reset() {
        val tx = currentTx()
        val rx = currentRx()
        lastTx = tx
        lastRx = rx
        baselineTx = tx
        baselineRx = rx
        lastSampleAt = System.currentTimeMillis()
    }

    fun sample(now: Long = System.currentTimeMillis()): Snapshot {
        if (lastSampleAt == 0L) {
            reset()
            return Snapshot()
        }
        val tx = currentTx()
        val rx = currentRx()
        val elapsed = (now - lastSampleAt).coerceAtLeast(1L)
        val uploadRate = ((tx - lastTx).coerceAtLeast(0L) * 1000L) / elapsed
        val downloadRate = ((rx - lastRx).coerceAtLeast(0L) * 1000L) / elapsed
        lastTx = tx
        lastRx = rx
        lastSampleAt = now
        return Snapshot(
            uploadRate = uploadRate,
            downloadRate = downloadRate,
            totalUpload = (tx - baselineTx).coerceAtLeast(0L),
            totalDownload = (rx - baselineRx).coerceAtLeast(0L),
        )
    }

    private fun currentTx(): Long = runCatching {
        TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0L)
    }.getOrDefault(0L)

    private fun currentRx(): Long = runCatching {
        TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0L)
    }.getOrDefault(0L)
}

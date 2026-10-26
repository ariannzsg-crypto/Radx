package com.radx.app.core

import android.content.Context
import android.util.Log
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Runs the bundled Xray core as its own process.
 *
 * The binary ships inside the APK as `lib/<abi>/libxray.so`: Android only executes
 * files that came with the APK, and the native library directory is where those land
 * with the execute bit set. A separate process keeps the Xray and sing-box Go
 * runtimes apart (both bind their own `go.Seq` runtime otherwise) and lets sing-box
 * own the tun while Xray only listens on loopback SOCKS.
 *
 * Xray's sockets bypass the tunnel because the app's UID is excluded from the VPN, so
 * it reaches the upstream directly without extra `protect()` plumbing.
 */
object XrayRunner {

    private const val TAG = "XrayRunner"
    private const val BINARY_NAME = "libxray.so"

    class Session(val process: Process, val directory: File)

    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    fun isAvailable(context: Context): Boolean {
        val binary = binary(context)
        return binary.isFile && binary.canExecute()
    }

    /**
     * Starts the core and waits until its SOCKS ports answer.
     *
     * @param expectPort first local SOCKS port of the generated config, used as a
     * readiness probe; pass null to skip the wait.
     * @throws IllegalStateException when the process cannot start or exits immediately.
     */
    fun start(context: Context, configJson: String, expectPort: Int? = null): Session {
        val directory = File(context.filesDir, "xray").apply { mkdirs() }
        val configFile = File(directory, "config.json").apply { writeText(configJson) }
        val logFile = File(directory, "xray.log").apply { writeText("") }

        val process = try {
            ProcessBuilder(binary(context).absolutePath, "run", "-c", configFile.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                .start()
        } catch (error: Exception) {
            throw IllegalStateException("Cannot start the Xray core: ${error.message}", error)
        }

        val session = Session(process, directory)
        if (expectPort != null) {
            val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive) break
                if (canConnect(expectPort)) return session
                Thread.sleep(120)
            }
        } else {
            Thread.sleep(300)
        }

        if (!process.isAlive) {
            stop(session)
            throw IllegalStateException("The Xray core exited: ${logTail(directory)}")
        }
        Log.i(TAG, "xray core is up after ${READY_TIMEOUT_MS}ms budget")
        return session
    }

    fun stop(session: Session?) {
        if (session == null) return
        runCatching { session.process.destroy() }
        val stopped = runCatching { session.process.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)
        if (!stopped) runCatching { session.process.destroyForcibly() }
    }

    /** Last lines of the core log, for error messages. */
    fun logTail(directory: File, maxLines: Int = 6): String = runCatching {
        File(directory, "xray.log").readLines()
            .filter { it.isNotBlank() }
            .takeLast(maxLines)
            .joinToString(" | ")
            .take(400)
    }.getOrDefault("")

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 300)
            true
        }
    }.getOrDefault(false)

    private const val READY_TIMEOUT_MS = 6_000L
}

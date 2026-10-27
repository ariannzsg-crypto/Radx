package com.radx.app.core

import android.content.Context
import android.util.Log
import com.radx.app.config.XrayConfigBuilder
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

    /**
     * Raised when the core exits before it can serve. [rejectedOutboundIndex] is the
     * bridge node the core named while loading the config, if it failed that way.
     */
    class CoreStartException(
        message: String,
        val rejectedOutboundIndex: Int?,
    ) : IllegalStateException(message)

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
                .start()
        } catch (error: Exception) {
            throw IllegalStateException("Cannot start the Xray core: ${error.message}", error)
        }

        // The core output feeds both the log screen and the on-disk log that error
        // messages quote.
        val writer = logFile.bufferedWriter()
        Thread({
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    runCatching {
                        writer.write(line)
                        writer.newLine()
                        writer.flush()
                    }
                    CoreLog.add(LogSource.XRAY, line)
                }
            }
            runCatching { writer.close() }
        }, "xray-log").apply { isDaemon = true }.start()

        CoreLog.add(LogSource.XRAY, "core started")

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
            val reason = "The Xray core exited: ${failureSummary(directory)}"
            CoreLog.add(LogSource.XRAY, reason)
            throw CoreStartException(reason, rejectedIndex(directory))
        }
        // A slow device may answer later than the probe window; sing-box retries the
        // SOCKS dial per connection, so letting the session live is the right call.
        Log.i(TAG, "xray core started")
        return session
    }

    fun stop(session: Session?) {
        if (session == null) return
        runCatching { session.process.destroy() }
        val stopped = runCatching { session.process.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)
        if (!stopped) runCatching { session.process.destroyForcibly() }
        CoreLog.add(LogSource.XRAY, "core stopped")
    }

    /** Last lines of the core log, for error messages. */
    fun logTail(directory: File, maxLines: Int = 6): String = runCatching {
        File(directory, "xray.log").readLines()
            .filter { it.isNotBlank() }
            .takeLast(maxLines)
            .joinToString(" | ")
            .take(400)
    }.getOrDefault("")

    /**
     * The failure line carries the node tags at its start and the innermost cause at its
     * end, so a long line keeps both ends instead of being cut in the middle.
     */
    private fun failureSummary(directory: File, maxChars: Int = 520): String = runCatching {
        val lines = File(directory, "xray.log").readLines().filter { it.isNotBlank() }
        val failure = lines.lastOrNull { it.contains("Failed to start") }
            ?: lines.takeLast(4).joinToString(" | ")
        if (failure.length <= maxChars) {
            failure
        } else {
            failure.take(maxChars / 2) + " … " + failure.takeLast(maxChars / 2)
        }
    }.getOrDefault("")

    /** The bridge node index the core refused, read from the whole on-disk log. */
    private fun rejectedIndex(directory: File): Int? = runCatching {
        XrayConfigBuilder.rejectedOutboundIndex(File(directory, "xray.log").readText())
    }.getOrNull()

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 300)
            true
        }
    }.getOrDefault(false)

    private const val READY_TIMEOUT_MS = 6_000L
}

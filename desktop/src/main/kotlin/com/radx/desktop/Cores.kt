package com.radx.desktop

import com.radx.app.config.SingBoxConfigBuilder
import com.radx.app.config.XrayBridge
import com.radx.app.config.XrayConfigBuilder
import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.ProxyNode
import com.radx.app.data.TunnelMode
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Where the bundled sing-box/Xray executables are looked up. */
object Cores {

    const val SING_BOX = "sing-box.exe"
    const val XRAY = "xray.exe"

    /**
     * Looks for [name] in `--cores`, `RADX_CORES`, the `cores/` folder of the unpacked
     * tool, the working directory and finally `PATH`.
     */
    fun find(name: String, explicit: String? = null): Path? {
        val candidates = buildList {
            explicit?.takeIf { it.isNotBlank() }?.let { add(Paths.get(it).resolve(name)) }
            System.getenv("RADX_CORES")?.takeIf { it.isNotBlank() }?.let { add(Paths.get(it).resolve(name)) }
            installHome()?.let { add(it.resolve("cores").resolve(name)) }
            add(Paths.get(System.getProperty("user.dir")).resolve(name))
            add(Paths.get(System.getProperty("user.dir")).resolve("cores").resolve(name))
            System.getenv("PATH")?.split(java.io.File.pathSeparator).orEmpty().forEach { entry ->
                if (entry.isNotBlank()) add(Paths.get(entry).resolve(name))
            }
        }
        return candidates.firstOrNull { Files.isRegularFile(it) }
    }

    /**
     * Root of an unpacked distribution: the tool's jar sits in `lib/`, so its parent's
     * parent holds `bin/` and `cores/`.
     */
    private fun installHome(): Path? = runCatching {
        val location = Paths.get(Cores::class.java.protectionDomain.codeSource.location.toURI())
        val lib = if (Files.isDirectory(location)) return null else location.parent ?: return null
        if (lib.fileName?.toString() != "lib") return null
        lib.parent
    }.getOrNull()

    /** A running core plus the log file it writes to. */
    class Core(val label: String, val handle: java.lang.Process, val log: Path?) {
        fun alive(): Boolean = handle.isAlive

        fun stop() {
            if (!handle.isAlive) return
            runCatching { handle.destroy() }
            val exited = runCatching { handle.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)
            if (!exited) runCatching { handle.destroyForcibly() }
        }
    }

    /** The core named an outbound it refuses to load; [index] is its bridge node. */
    class RefusedOutbound(val index: Int, message: String) : IllegalStateException(message)
}

/**
 * Everything needed to run one profile: the two configs, the node that will be used and
 * the nodes that had to be left out.
 */
class Tunnel(
    val profile: Profile,
    val settings: AppSettings,
    val node: ProxyNode,
    val singBoxConfig: String,
    val xrayConfig: String?,
    val skipped: Map<Int, String>,
) {
    /** First bridge port, used to tell when the Xray core is listening. */
    val bridgeProbePort: Int? = profile.nodes.withIndex()
        .firstOrNull { it.value.viaXray && it.index !in skipped.keys }
        ?.let { XrayBridge.port(it.index) }

    companion object {

        /**
         * Builds the configs, keeping every node both cores can actually load.
         *
         * [extraSkip] carries nodes a previous attempt found unusable, and [note] reports
         * what was left out so the user is never surprised by a silently missing node.
         */
        fun prepare(
            profile: Profile,
            settings: AppSettings,
            nodeTag: String?,
            allowXray: Boolean,
            extraSkip: Set<Int> = emptySet(),
            note: (String) -> Unit = {},
        ): Tunnel {
            require(profile.nodes.isNotEmpty()) { "This profile has no nodes" }

            val skipped = linkedMapOf<Int, String>()
            profile.nodes.withIndex().forEach { (index, node) ->
                if (!node.viaXray) return@forEach
                val reason = XrayConfigBuilder.rejectionReason(node)
                if (reason != null) {
                    skipped[index] = reason
                } else if (!allowXray) {
                    skipped[index] = "the Xray core is disabled"
                } else if (index in extraSkip) {
                    skipped[index] = "the core refused this node earlier"
                }
            }
            skipped.forEach { (index, reason) ->
                note("${profile.nodes[index].name} is skipped: $reason")
            }

            val xrayConfig = if (allowXray) XrayConfigBuilder.build(profile.nodes, skipped.keys) else null
            if (allowXray && xrayConfig == null) {
                profile.nodes.withIndex().forEach { (index, node) ->
                    if (node.viaXray) skipped[index] = "the Xray core is not available"
                }
            }

            val wanted = nodeTag?.let { tag -> profile.nodes.firstOrNull { it.tag == tag } }
                ?: profile.nodes.firstOrNull { it.tag == profile.selectedTag }
                ?: profile.nodes.first()
            val wantedIndex = profile.nodes.indexOf(wanted)
            val node = if (wantedIndex in skipped.keys) {
                val fallback = profile.nodes.withIndex().firstOrNull { it.index !in skipped.keys }?.value
                    ?: throw IllegalStateException("Every node of this profile needs the Xray core")
                note("${wanted.name} is skipped, using ${fallback.name} instead")
                fallback
            } else {
                wanted
            }

            val config = SingBoxConfigBuilder.build(
                profile = profile.copy(selectedTag = node.tag),
                settings = settings,
                xrayAvailable = xrayConfig != null,
                xraySkip = skipped.keys,
            )
            return Tunnel(profile, settings, node, config, xrayConfig, skipped)
        }
    }
}

/** Starts a core with merged output: to the console and to `label`'s log file. */
internal fun startCore(
    label: String,
    executable: Path,
    arguments: List<String>,
    workingDirectory: Path,
    logFile: Path,
    quiet: Boolean,
): Cores.Core {
    val process = ProcessBuilder(listOf(executable.toAbsolutePath().toString()) + arguments)
        .directory(workingDirectory.toFile())
        .redirectErrorStream(true)
        .start()
    Files.writeString(logFile, "")
    val writer = Files.newBufferedWriter(logFile)
    thread(isDaemon = true, name = "$label-log") {
        runCatching {
            process.inputStream.bufferedReader().forEachLine { line ->
                runCatching {
                    writer.write(line)
                    writer.newLine()
                    writer.flush()
                }
                if (!quiet) println("[$label] $line")
            }
        }
        runCatching { writer.close() }
    }
    return Cores.Core(label, process, logFile)
}

internal fun canConnect(port: Int, timeoutMs: Int = 400): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), timeoutMs) }
    true
}.getOrDefault(false)

/** Waits until [probe] answers or the process dies; throws with the log tail otherwise. */
internal fun awaitReady(core: Cores.Core, label: String, probe: () -> Boolean) {
    val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        if (!core.alive()) break
        if (probe()) return
        Thread.sleep(120)
    }
    if (!core.alive()) {
        val reason = logTail(core.log)
        core.stop()
        throw IllegalStateException("The $label core exited: $reason")
    }
}

internal fun logTail(log: Path?, maxChars: Int = 600): String = runCatching {
    val lines = log?.let { Files.readAllLines(it) }.orEmpty().filter { it.isNotBlank() }
    val failure = lines.lastOrNull { it.contains("Failed to start") || it.contains("FATAL") }
        ?: lines.takeLast(3).joinToString(" | ")
    if (failure.length <= maxChars) failure else failure.take(maxChars / 2) + " … " + failure.takeLast(maxChars / 2)
}.getOrDefault("")

internal const val READY_TIMEOUT_MS = 8_000L

/** The local address the tunnel listens on, printed once the cores are up. */
internal fun listenAddress(settings: AppSettings): String =
    if (settings.tunnelMode == TunnelMode.PROXY) {
        "127.0.0.1:${settings.proxyPort}"
    } else {
        "tun (system wide)"
    }

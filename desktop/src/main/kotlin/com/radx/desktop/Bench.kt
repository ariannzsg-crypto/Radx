package com.radx.desktop

import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.ProxyNode
import com.radx.app.data.TunnelMode
import com.radx.app.net.TrafficProbe
import java.nio.file.Files

/**
 * Measures what a node actually carries instead of how fast its handshake is answered.
 *
 * A filtered link often completes the TCP handshake locally (or a foreign adapter
 * answers it), so the latency the client shows can look excellent on a node that passes
 * no traffic, and two nodes with the same ping can differ by 20x in throughput. The only
 * ranking that reflects what the user gets is a real download through the real tunnel.
 */
object Bench {

    val DEFAULT_URL: String get() = TrafficProbe.desktopUrl

    /** One node's result; [error] is set when the node could not be measured at all. */
    data class Sample(
        val index: Int,
        val node: ProxyNode,
        val mbps: Double,
        val bytes: Long,
        val millis: Long,
        val error: String? = null,
    )

    /**
     * Runs every node through its own tunnel and returns the samples, best first.
     *
     * [limit] caps how many nodes are measured, which matters for large subscriptions;
     * nodes without a position in [order] are left alone.
     */
    fun run(
        profile: Profile,
        settings: AppSettings,
        coresDirectory: String?,
        url: String = DEFAULT_URL,
        limit: Int? = null,
        skipXray: Boolean = false,
    ): List<Sample> {
        val positions = profile.nodes.indices.take(limit ?: profile.nodes.size)
        val samples = positions.map { index ->
            measure(profile, index, settings, coresDirectory, url, skipXray)
        }
        return samples.sortedWith(compareBy({ it.error != null }, { -it.mbps }))
    }

    private fun measure(
        profile: Profile,
        index: Int,
        settings: AppSettings,
        coresDirectory: String?,
        url: String,
        skipXray: Boolean,
    ): Sample {
        val node = profile.nodes[index]
        val proxySettings = settings.copy(tunnelMode = TunnelMode.PROXY)
        // The skip reasons are reported per node below, so the builder stays silent here.
        val tunnel = runCatching {
            Tunnel.prepare(profile, proxySettings, node.tag, allowXray = !skipXray, note = {})
        }.getOrElse { error ->
            return Sample(index, node, 0.0, 0, 0, error.message ?: error.javaClass.simpleName)
        }
        // The requested node may have been dropped (a core that refuses it, Xray disabled).
        if (tunnel.node.tag != node.tag) {
            return Sample(index, node, 0.0, 0, 0, tunnel.skipped[index] ?: "not supported by the cores")
        }

        val directory = DesktopPaths.run.resolve("bench").resolve("n$index")
        Files.createDirectories(directory)
        val singBox = Cores.find(Cores.SING_BOX, coresDirectory)
            ?: return Sample(index, node, 0.0, 0, 0, "sing-box.exe was not found")
        val singBoxFile = directory.resolve("sing-box.json")
        Files.writeString(singBoxFile, tunnel.singBoxConfig)

        var xray: Cores.Core? = null
        val core = try {
            tunnel.xrayConfig?.let { config ->
                val executable = Cores.find(Cores.XRAY, coresDirectory)
                    ?: throw IllegalStateException("xray.exe was not found")
                val file = directory.resolve("xray.json").also { Files.writeString(it, config) }
                xray = startCore(
                    label = "xray",
                    executable = executable,
                    arguments = listOf("run", "-c", file.toString()),
                    workingDirectory = directory,
                    logFile = directory.resolve("xray.log"),
                    quiet = true,
                )
                awaitReady(xray!!, "Xray") { tunnel.bridgeProbePort?.let { canConnect(it) } ?: true }
            }
            startCore(
                label = "sing-box",
                executable = singBox,
                arguments = listOf("run", "-c", singBoxFile.toString(), "-D", directory.toString()),
                workingDirectory = directory,
                logFile = directory.resolve("sing-box.log"),
                quiet = true,
            ).also { core ->
                awaitReady(core, "sing-box") { canConnect(proxySettings.proxyPort) }
            }
        } catch (error: Throwable) {
            xray?.stop()
            return Sample(index, node, 0.0, 0, 0, error.message ?: error.javaClass.simpleName)
        }

        return try {
            // A single reset would otherwise rank a healthy node with the dead ones, so
            // the shared probe gets a second attempt on the same tunnel.
            val result = TrafficProbe.measure(
                proxyPort = proxySettings.proxyPort,
                url = url,
                attempts = 2,
            )
            Sample(index, node, result.mbps, result.bytes, result.millis, result.error)
        } finally {
            core.stop()
            xray?.stop()
        }
    }

    /** The node the bench picked, or null when every node failed. */
    fun fastest(samples: List<Sample>): ProxyNode? = samples.firstOrNull { it.error == null && it.bytes > 0 }?.node
}

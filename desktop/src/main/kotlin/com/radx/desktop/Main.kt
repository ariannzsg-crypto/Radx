package com.radx.desktop

import com.radx.app.config.ConfigImporter
import com.radx.app.config.XrayConfigBuilder
import com.radx.app.data.AppSettings
import com.radx.app.data.LogLevel
import com.radx.app.data.Profile
import com.radx.app.data.ProxyNode
import com.radx.app.data.TunnelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.system.exitProcess

private val USAGE = """
radx — Radx for Windows and Linux (same config pipeline as the Android app)

  radx import <url|file|-> [--name NAME]   add a subscription, a file or stdin
  radx list                                show the saved configs
  radx nodes  [--profile N] [--select N]   show the nodes of a config
  radx ping   [--profile N] [--select-fastest]
                                           measure TCP latency for every node
  radx gen    [--profile N] [--node N] [--mode proxy|tun] [--port N] [--out DIR] [--check]
              [--mtu N] [--block-quic] [--no-bypass-lan] [--log-level error|warn|info|debug]
                                           write the sing-box and Xray configs
  radx up     [--profile N] [--node N] [--mode proxy|tun] [--port N] [--cores DIR] [--no-xray] [--quiet]
              [--mtu N] [--block-quic] [--no-bypass-lan] [--log-level LEVEL]
                                           run the tunnel until Ctrl+C
  radx version                             versions of the tool and the cores

Proxy mode listens on 127.0.0.1:7890 (SOCKS + HTTP) and needs no privileges;
TUN mode takes over the whole system and needs an elevated shell.
Cores are looked up in --cores, RADX_CORES, ./cores next to the tool and PATH.
State lives in %USERPROFILE%\.radx (override with RADX_HOME).
""".trimIndent()

fun main(args: Array<String>) {
    val cli = Args.parse(args.toList())
    if (cli.flag("help") || cli.positional.isEmpty()) {
        println(USAGE)
        return
    }
    val store = DesktopStore()
    try {
        when (cli.positional.first()) {
            "version", "--version" -> version()
            "import" -> importConfig(cli, store)
            "list", "profiles" -> listProfiles(store)
            "nodes" -> listNodes(cli, store)
            "ping" -> pingNodes(cli, store)
            "gen", "config" -> generate(cli, store)
            "up", "connect" -> connect(cli, store)
            else -> die("unknown command: ${cli.positional.first()} (try: radx --help)")
        }
    } catch (error: Throwable) {
        System.err.println("radx: ${error.message ?: error.javaClass.simpleName}")
        exitProcess(1)
    }
}

// ------------------------------------------------------------------ commands

private fun version() {
    val version = Args::class.java.getResourceAsStream("/radx-version.txt")
        ?.bufferedReader()
        ?.use { it.readText().trim() }
        ?.takeIf { it.isNotEmpty() }
        ?: "dev"
    println("radx $version (desktop)")
    println("sing-box core: ${Cores.find(Cores.SING_BOX) ?: "not found"}")
    println("xray core:     ${Cores.find(Cores.XRAY) ?: "not found"}")
}

private fun importConfig(cli: Args, store: DesktopStore) {
    val source = cli.positional.getOrNull(1) ?: die("usage: radx import <url|file|->")
    val text = when {
        source == "-" -> System.`in`.bufferedReader().readText()
        source.startsWith("http://") || source.startsWith("https://") -> source
        else -> {
            val file = Paths.get(source)
            if (!Files.isRegularFile(file)) die("no such file: $source")
            Files.readString(file)
        }
    }
    val imported = runBlocking { ConfigImporter().importText(text, cli.value("name")) }
    val profile = Profile(
        id = UUID.randomUUID().toString(),
        name = imported.suggestedName,
        kind = imported.kind,
        raw = imported.raw,
        subscriptionUrl = imported.subscriptionUrl,
        nodes = imported.nodes,
        selectedTag = imported.nodes.firstOrNull()?.tag,
        updatedAt = System.currentTimeMillis(),
    )
    store.upsert(profile)
    info("imported ${imported.nodes.size} node(s) as \"${profile.name}\"")
    imported.warnings.forEach { note(it) }
}

private fun listProfiles(store: DesktopStore) {
    val state = store.read()
    if (state.profiles.isEmpty()) {
        info("no config yet — add one with: radx import <url>")
        return
    }
    state.profiles.forEachIndexed { index, profile ->
        val marker = if (profile.id == state.selectedProfile?.id) "*" else " "
        println("$marker ${index + 1}. ${profile.name}  [${profile.kind.name.lowercase()}, ${profile.nodes.size} nodes]")
        profile.subscriptionUrl?.let { println("      $it") }
    }
}

private fun listNodes(cli: Args, store: DesktopStore) {
    val state = store.read()
    val profile = pickProfile(cli, state) ?: die("no config yet — add one with: radx import <url>")
    val select = cli.int("select")
    if (select != null) {
        val node = profile.nodes.getOrNull(select - 1) ?: die("no node #$select")
        store.upsert(profile.copy(selectedTag = node.tag))
        info("selected ${node.name}")
        return
    }
    printNodes(profile)
}

private fun printNodes(profile: Profile) {
    profile.nodes.forEachIndexed { index, node ->
        val marker = if (node.tag == profile.selectedTag) "*" else " "
        val core = if (node.viaXray) " [xray]" else ""
        val rejected = XrayConfigBuilder.rejectionReason(node)
        val problem = rejected?.let { "  ⚠ $it" }.orEmpty()
        println("$marker ${index + 1}. ${node.name}  [${node.type}] ${node.server}:${node.port}$core$problem")
    }
}

private fun pingNodes(cli: Args, store: DesktopStore) {
    val state = store.read()
    val profile = pickProfile(cli, state) ?: die("no config yet — add one with: radx import <url>")
    if (profile.nodes.isEmpty()) die("the profile has no nodes")
    val quiet = cli.flag("quiet")
    val results = runBlocking {
        profile.nodes.map { node ->
            async(Dispatchers.IO) { node to measure(node) }
        }.awaitAll()
    }
    val ordered = results.sortedWith(compareBy({ it.second == null }, { it.second ?: 0 }))
    ordered.forEach { (node, delay) ->
        val label = if (delay == null) "timeout" else "$delay ms"
        if (!quiet) println("%-46s %s".format(node.name.take(44), label))
    }
    if (cli.flag("select-fastest")) {
        val fastest = ordered.firstOrNull { it.second != null }?.first ?: die("every node timed out")
        store.upsert(profile.copy(selectedTag = fastest.tag))
        info("selected the fastest node: ${fastest.name}")
    }
}

private fun generate(cli: Args, store: DesktopStore) {
    val state = store.read()
    val profile = pickProfile(cli, state) ?: die("no config yet — add one with: radx import <url>")
    val settings = settingsFor(cli, state.settings)
    val tunnel = Tunnel.prepare(profile, settings, nodeTag(cli, profile), allowXray = !cli.flag("no-xray"), note = ::note)
    val out = cli.value("out")?.let { Paths.get(it) } ?: DesktopPaths.run
    Files.createDirectories(out)
    val singBoxFile = out.resolve("sing-box.json")
    Files.writeString(singBoxFile, tunnel.singBoxConfig)
    info("wrote $singBoxFile")
    val xrayFile = tunnel.xrayConfig?.let { config ->
        out.resolve("xray.json").also { Files.writeString(it, config); info("wrote $it") }
    }
    info("node: ${tunnel.node.name}  mode: ${settings.tunnelMode.name.lowercase()}")
    if (cli.flag("check")) checkConfigs(singBoxFile, xrayFile, cli.value("cores"))
}

private fun checkConfigs(singBoxFile: Path, xrayFile: Path?, cores: String?) {
    val singBox = Cores.find(Cores.SING_BOX, cores)
    if (singBox == null) {
        note("sing-box.exe was not found, so its config was not checked")
    } else {
        val code = runCore(singBox, listOf("check", "-c", singBoxFile.toString()))
        info("sing-box check -> ${if (code == 0) "OK" else "failed ($code)"}")
    }
    if (xrayFile != null) {
        val xray = Cores.find(Cores.XRAY, cores)
        if (xray == null) {
            note("xray.exe was not found, so its config was not checked")
        } else {
            val code = runCore(xray, listOf("run", "-test", "-c", xrayFile.toString()))
            info("xray -test -> ${if (code == 0) "OK" else "failed ($code)"}")
        }
    }
}

private fun connect(cli: Args, store: DesktopStore) {
    val state = store.read()
    val profile = pickProfile(cli, state) ?: die("no config yet — add one with: radx import <url>")
    if (profile.nodes.isEmpty()) die("the profile has no nodes")
    val settings = settingsFor(cli, state.settings)
    val quiet = cli.flag("quiet")
    val allowXray = !cli.flag("no-xray")
    val singBox = Cores.find(Cores.SING_BOX, cli.value("cores"))
        ?: die("sing-box.exe was not found. Pass --cores DIR or set RADX_CORES.")
    val runDirectory = DesktopPaths.run.apply { Files.createDirectories(this) }
    val extraSkip = mutableSetOf<Int>()

    while (true) {
        val tunnel = Tunnel.prepare(profile, settings, nodeTag(cli, profile), allowXray, extraSkip) { note(it) }
        val singBoxFile = runDirectory.resolve("sing-box.json")
        Files.writeString(singBoxFile, tunnel.singBoxConfig)

        var xray: Cores.Core? = null
        if (tunnel.xrayConfig != null) {
            val xrayExe = Cores.find(Cores.XRAY, cli.value("cores"))
                ?: die("This config needs the Xray core. Put xray.exe in cores/ or pass --cores DIR.")
            val xrayFile = runDirectory.resolve("xray.json")
            Files.writeString(xrayFile, tunnel.xrayConfig)
            try {
                xray = startXray(xrayExe, runDirectory, xrayFile, tunnel.bridgeProbePort, quiet)
            } catch (refused: Cores.RefusedOutbound) {
                if (extraSkip.add(refused.index)) {
                    note("the core refused ${profile.nodes.getOrNull(refused.index)?.name ?: "a node"}; retrying without it")
                    continue
                }
                throw refused
            }
        }

        try {
            runTunnel(tunnel, settings, singBox, singBoxFile, runDirectory, quiet)
        } finally {
            xray?.stop()
        }
        return
    }
}

// ------------------------------------------------------------------- runtime

private fun startXray(
    executable: Path,
    workingDirectory: Path,
    configFile: Path,
    probePort: Int?,
    quiet: Boolean,
): Cores.Core {
    val core = startCore(
        label = "xray",
        executable = executable,
        arguments = listOf("run", "-c", configFile.toString()),
        workingDirectory = workingDirectory,
        logFile = workingDirectory.resolve("xray.log"),
        quiet = quiet,
    )
    try {
        awaitReady(core, "Xray") { probePort == null || canConnect(probePort) }
    } catch (error: IllegalStateException) {
        val log = runCatching { Files.readString(core.log!!) }.getOrDefault("")
        val refused = XrayConfigBuilder.rejectedOutboundIndex(log)
        if (refused != null) throw Cores.RefusedOutbound(refused, error.message ?: "refused outbound")
        throw error
    }
    return core
}

private fun runTunnel(
    tunnel: Tunnel,
    settings: AppSettings,
    singBox: Path,
    configFile: Path,
    workingDirectory: Path,
    quiet: Boolean,
) {
    if (settings.tunnelMode == TunnelMode.VPN) {
        note("TUN mode takes over the system routing table and needs an elevated shell.")
    }
    val core = startCore(
        label = "sing-box",
        executable = singBox,
        arguments = listOf("run", "-c", configFile.toString(), "-D", workingDirectory.toString()),
        workingDirectory = workingDirectory,
        logFile = workingDirectory.resolve("sing-box.log"),
        quiet = quiet,
    )
    val hook = Thread { core.stop() }
    Runtime.getRuntime().addShutdownHook(hook)
    try {
        if (settings.tunnelMode == TunnelMode.PROXY) {
            awaitReady(core, "sing-box") { canConnect(settings.proxyPort) }
        } else {
            Thread.sleep(1500)
            if (!core.alive()) throw IllegalStateException("The sing-box core exited: ${logTail(core.log)}")
        }
        info("connected through ${tunnel.node.name} (${tunnel.profile.name})")
        info("listening on ${listenAddress(settings)} · mode ${settings.tunnelMode.name.lowercase()}")
        if (tunnel.skipped.isNotEmpty()) {
            info("${tunnel.skipped.size} node(s) left out: ${tunnel.skipped.values.distinct().joinToString("; ")}")
        }
        info("press Ctrl+C to stop")
        core.handle.waitFor()
    } finally {
        core.stop()
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    }
    info("stopped")
}

private fun runCore(executable: Path, arguments: List<String>): Int = runCatching {
    ProcessBuilder(listOf(executable.toAbsolutePath().toString()) + arguments)
        .redirectErrorStream(true)
        .start()
        .also { process -> process.inputStream.bufferedReader().forEachLine { line -> if (line.isNotBlank()) note(line.trim()) } }
        .waitFor()
}.getOrElse { error ->
    note("could not run ${executable.fileName}: ${error.message}")
    1
}

/** TCP connect time to the node, which is the same check the app's latency button does. */
private fun measure(node: ProxyNode): Int? {
    if (node.server.isBlank() || node.port !in 1..65535) return null
    return runCatching {
        val start = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(node.server, node.port), PING_TIMEOUT_MS) }
        ((System.nanoTime() - start) / 1_000_000L).toInt()
    }.getOrNull()
}

// -------------------------------------------------------------------- input

private fun pickProfile(cli: Args, state: DesktopState): Profile? {
    val index = cli.int("profile") ?: return state.selectedProfile
    return state.profiles.getOrNull(index - 1) ?: die("no config #$index (see: radx list)")
}

private fun nodeTag(cli: Args, profile: Profile): String? {
    val index = cli.int("node") ?: return null
    return profile.nodes.getOrNull(index - 1)?.tag ?: die("no node #$index")
}

private fun settingsFor(cli: Args, settings: AppSettings): AppSettings {
    val mode = when (cli.value("mode")?.lowercase()) {
        null -> settings.tunnelMode
        "proxy", "socks", "mixed" -> TunnelMode.PROXY
        "tun", "vpn" -> TunnelMode.VPN
        else -> die("unknown mode: ${cli.value("mode")} (use proxy or tun)")
    }
    val logLevel = when (cli.value("log-level")?.lowercase()) {
        null -> settings.logLevel
        "error" -> LogLevel.ERROR
        "warn" -> LogLevel.WARN
        "info" -> LogLevel.INFO
        "debug" -> LogLevel.DEBUG
        else -> die("unknown log level: ${cli.value("log-level")} (use error, warn, info or debug)")
    }
    val mtu = cli.int("mtu")?.coerceIn(AppSettings.MIN_MTU, AppSettings.MAX_MTU) ?: settings.tunMtu
    return settings.copy(
        tunnelMode = mode,
        proxyPort = cli.int("port") ?: settings.proxyPort,
        allowLan = settings.allowLan || cli.flag("lan"),
        tunMtu = mtu,
        bypassLan = if (cli.flag("no-bypass-lan")) false else settings.bypassLan,
        blockQuic = settings.blockQuic || cli.flag("block-quic"),
        logLevel = logLevel,
    )
}

private fun info(message: String) = println("radx: $message")

private fun note(message: String) = println("radx: · $message")

private fun die(message: String): Nothing {
    System.err.println("radx: $message")
    exitProcess(2)
}

private const val PING_TIMEOUT_MS = 3000

/** Minimal `--key value` / `--key=value` / `--flag` parser. */
class Args private constructor(
    val positional: List<String>,
    private val options: Map<String, String>,
    private val switches: Set<String>,
) {
    fun flag(name: String): Boolean = name in switches
    fun value(name: String): String? = options[name]
    fun int(name: String): Int? = options[name]?.toIntOrNull()

    companion object {
        private val BOOLEAN = setOf(
            "help", "quiet", "json", "lan", "check", "no-xray", "select-fastest", "verbose",
        )

        fun parse(tokens: List<String>): Args {
            val positional = mutableListOf<String>()
            val options = mutableMapOf<String, String>()
            val switches = mutableSetOf<String>()
            var index = 0
            while (index < tokens.size) {
                val token = tokens[index]
                when {
                    token.startsWith("--") && token.contains('=') -> {
                        val (key, value) = token.removePrefix("--").split('=', limit = 2)
                        options[key] = value
                    }

                    token.startsWith("--") -> {
                        val key = token.removePrefix("--")
                        val next = tokens.getOrNull(index + 1)
                        if (key in BOOLEAN || next == null || next.startsWith("--")) {
                            switches += key
                        } else {
                            options[key] = next
                            index++
                        }
                    }

                    else -> positional += token
                }
                index++
            }
            return Args(positional, options, switches)
        }
    }
}

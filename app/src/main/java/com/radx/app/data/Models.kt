package com.radx.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** How a profile was imported. */
@Serializable
enum class ProfileKind { LINKS, CLASH, SING_BOX }

/** Selectable proxy core. Only sing-box ships with this build. */
@Serializable
enum class CoreKind { SING_BOX, CLASH_META }

/** How the tunnel exposes itself to the device. */
@Serializable
enum class TunnelMode { VPN, PROXY }

/** Highlight colour of the interface. */
@Serializable
enum class AccentColor { CYAN, BLUE, VIOLET, EMERALD }

/** Verbosity of the core log, matching the names sing-box accepts. */
@Serializable
enum class LogLevel { ERROR, WARN, INFO, DEBUG }

/**
 * A single proxy outbound, already translated into sing-box outbound JSON so the
 * config builder only has to assemble them.
 *
 * [xray] is set for nodes whose transport sing-box cannot run (XHTTP). Those are
 * started on the bundled Xray core and reached through a local SOCKS bridge, so
 * [outbound] is replaced by a SOCKS outbound at config build time.
 */
@Serializable
data class ProxyNode(
    val tag: String,
    val name: String,
    val type: String,
    val server: String = "",
    val port: Int = 0,
    val outbound: JsonObject,
    val xray: JsonObject? = null,
) {
    val viaXray: Boolean get() = xray != null
}

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val kind: ProfileKind,
    /** Original imported payload (links, YAML or JSON) so it can be copied/shared. */
    val raw: String = "",
    /** Set for subscription profiles. */
    val subscriptionUrl: String? = null,
    val nodes: List<ProxyNode> = emptyList(),
    val selectedTag: String? = null,
    val updatedAt: Long = 0L,
) {
    val isSubscription: Boolean get() = subscriptionUrl != null
    val activeNode: ProxyNode? get() = nodes.firstOrNull { it.tag == selectedTag } ?: nodes.firstOrNull()
}

@Serializable
data class AppSettings(
    val core: CoreKind = CoreKind.SING_BOX,
    val tunnelMode: TunnelMode = TunnelMode.VPN,
    val allowLan: Boolean = false,
    val ipv6: Boolean = true,
    val localDns: String = "223.5.5.5",
    val remoteDns: String = "https://1.1.1.1/dns-query",
    val showNotification: Boolean = true,
    /** Frosted, translucent surfaces instead of flat cards. */
    val glassEffects: Boolean = true,
    val accent: AccentColor = AccentColor.CYAN,
    /** Bring the tunnel up as soon as the app opens, when a config exists. */
    val autoConnect: Boolean = false,
    /** Local SOCKS/HTTP port in proxy mode. */
    val proxyPort: Int = 7890,
    /** Measure every node's latency as soon as the node list opens. */
    val autoPing: Boolean = true,
    /** How long a latency probe waits before it counts as a timeout. */
    val pingTimeoutMs: Int = 3000,
    /** Update subscription configs that are older than `SUBSCRIPTION_MAX_AGE_MS`. */
    val autoRefresh: Boolean = false,
    /** MTU of the tunnel interface; a smaller value helps on links that drop big packets. */
    val tunMtu: Int = 9000,
    /** Dial private addresses (LAN, loopback ranges) directly instead of through the proxy. */
    val bypassLan: Boolean = true,
    /** Reject QUIC, so apps retry over TCP when UDP is blocked upstream. */
    val blockQuic: Boolean = false,
    /** How much the core writes to the log screen. */
    val logLevel: LogLevel = LogLevel.INFO,
) {
    companion object {
        /** Age after which an auto-refresh picks a subscription up again. */
        const val SUBSCRIPTION_MAX_AGE_MS = 12 * 60 * 60 * 1000L

        const val MIN_MTU = 1280
        const val MAX_MTU = 9000
    }
}

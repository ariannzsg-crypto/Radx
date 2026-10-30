package com.radx.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** How a profile was imported. */
@Serializable
enum class ProfileKind { LINKS, CLASH, SING_BOX }

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

    // ---------------------------------------------------------------- interface

    /** Put live up/down rates in the ongoing notification. */
    val speedDisplay: Boolean = false,

    /** Ask for confirmation before a config is deleted. */
    val deleteConfirm: Boolean = true,

    /** Show the node list in two columns instead of one. */
    val twoColumnLayout: Boolean = false,

    /** Follow the wallpaper palette on Android 12+. */
    val dynamicColor: Boolean = false,

    /** Interface language: `auto` follows the device, otherwise `en` or `fa`. */
    val language: String = "auto",

    // --------------------------------------------------------------------- VPN

    /** Ask the resolver for AAAA records first instead of preferring IPv4. */
    val preferIpv6: Boolean = false,

    /** Resolve through the core's DNS module instead of the plain VPN server. */
    val localDnsEnabled: Boolean = true,

    /** DNS handed to the tunnel interface when the core's own resolver is off. */
    val vpnDns: String = "1.1.1.1",

    /** Resolver for names that have to be looked up outside the tunnel. */
    val directDns: String = "223.5.5.5",

    /** `domain:address` pairs, comma separated; those names skip the resolver. */
    val dnsHosts: String = "",

    /** Address of the tunnel interface, without the prefix length. */
    val tunAddress: String = "10.10.14.1",

    // -------------------------------------------------------------------- core

    /** Sniff the destination out of the traffic so rules can match on the domain. */
    val sniffing: Boolean = true,

    /** Route by the sniffed domain but keep dialing the address the app asked for. */
    val routeOnly: Boolean = false,

    /** Keep a local SOCKS/HTTP port open even in VPN mode. */
    val localProxy: Boolean = true,

    /** Pick a free port for the local proxy on every start. */
    val dynamicProxyPort: Boolean = false,

    /** Credentials of the local proxy; an empty user means no authentication. */
    val proxyUser: String = "",
    val proxyPass: String = "",

    /** Carry UDP flows through the local SOCKS inbound. */
    val socksUdp: Boolean = true,

    /** Multiplex several streams over one upstream connection. */
    val muxEnabled: Boolean = false,

    /** Streams a multiplexed connection may carry. */
    val muxConcurrency: Int = 8,

    /** Split the TLS handshake across packets, which defeats naive DPI. */
    val fragmentEnabled: Boolean = false,

    /** Packet length range and delay range the handshake is split into. */
    val fragmentSize: String = "100-200",
    val fragmentInterval: String = "10-20",
) {
    companion object {
        /** Age after which an auto-refresh picks a subscription up again. */
        const val SUBSCRIPTION_MAX_AGE_MS = 12 * 60 * 60 * 1000L

        const val MIN_MTU = 1280
        const val MAX_MTU = 9000

        const val MIN_MUX_STREAMS = 1
        const val MAX_MUX_STREAMS = 128

        /** Values the language picker offers, in the order it shows them. */
        val LANGUAGES = listOf("auto", "en", "fa")
    }
}

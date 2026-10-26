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
)

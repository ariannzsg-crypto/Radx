package com.radx.app.core

import com.radx.app.data.TunnelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The tunnel structure that is actually running.
 *
 * The service fills this in right after the core starts, from the options the core
 * asked for, so the app can show the real interface (addresses, routes, DNS, apps)
 * instead of the values it wished for.
 */
data class TunnelInfo(
    val mode: TunnelMode,
    val core: String,
    /** 0 in proxy mode, where no tun interface exists. */
    val mtu: Int,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val ipv4Routes: List<String>,
    val ipv6Routes: List<String>,
    val dnsServers: List<String>,
    val autoRoute: Boolean,
    val strictRoute: Boolean,
    val includedApps: List<String>,
    val excludedApps: List<String>,
    /** Nodes handed over to the bundled Xray core and the loopback ports they use. */
    val xrayNodes: Int,
    val xrayPorts: List<Int>,
    /** Local SOCKS/HTTP port in proxy mode, 0 in VPN mode. */
    val proxyPort: Int,
) {
    val addressLines: List<String> get() = ipv4Addresses + ipv6Addresses

    val routeLines: List<String> get() = ipv4Routes + ipv6Routes

    val isVpn: Boolean get() = mode == TunnelMode.VPN
}

/**
 * Holds the latest snapshot so the UI can follow it.
 *
 * The service and the UI live in the same process, so a plain flow is enough; the
 * service clears it as soon as the tunnel is torn down.
 */
object TunnelInfoStore {

    private val _info = MutableStateFlow<TunnelInfo?>(null)
    val info: StateFlow<TunnelInfo?> = _info

    val value: TunnelInfo? get() = _info.value

    fun set(info: TunnelInfo) {
        _info.value = info
    }

    fun clear() {
        _info.value = null
    }
}

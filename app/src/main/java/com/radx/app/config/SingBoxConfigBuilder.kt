package com.radx.app.config

import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.ProxyNode
import com.radx.app.data.TunnelMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Assembles the sing-box configuration handed to the core.
 *
 * Layout:
 *  - one `tun` inbound (VPN mode) or `mixed` inbound (local proxy mode)
 *  - a `selector` outbound named "proxy" over every parsed node
 *  - a minimal route: sniff, DNS hijack, LAN bypass, everything else to "proxy"
 *
 * The default resolver (`dns.final`) runs through the proxy, so the hostnames the
 * core has to resolve *before* it can reach the proxy - the node servers themselves
 * and the remote DNS endpoint - are pinned to the direct resolver. Without that the
 * core tries to resolve the node through the proxy it is still dialing and fails
 * with `DNS query loopback`, which leaves the tunnel up but passing no traffic.
 */
object SingBoxConfigBuilder {

    /** Default local proxy port; the user can change it in the settings. */
    const val PROXY_PORT = 7890
    const val SELECTOR_TAG = "proxy"
    const val DIRECT_TAG = "direct"

    const val MIN_PROXY_PORT = 1024
    const val MAX_PROXY_PORT = 65535

    private const val TUN_IPV4 = "172.19.0.1/30"
    private const val TUN_IPV6 = "fdfe:dcba:9876::1/126"

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun build(
        profile: Profile,
        settings: AppSettings,
        xrayAvailable: Boolean = true,
        xraySkip: Set<Int> = emptySet(),
    ): String {
        require(profile.nodes.isNotEmpty()) { "This profile has no nodes" }

        val nodes = usableNodes(profile, xrayAvailable, xraySkip)
        require(nodes.isNotEmpty()) { "Every node of this profile needs the Xray core" }
        val activeTag = nodes[activeIndex(profile, nodes)].tag

        val config = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    put("level", settings.logLevel.name.lowercase())
                    put("timestamp", true)
                },
            )
            put("dns", dnsConfig(settings, bootstrapDomains(nodes)))
            put(
                "inbounds",
                buildJsonArray { add(inbound(settings)) },
            )
            put(
                "outbounds",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "selector")
                            put("tag", SELECTOR_TAG)
                            put("outbounds", buildJsonArray { nodes.forEach { add(it.tag) } })
                            put("default", activeTag)
                            put("interrupt_exist_connections", true)
                        },
                    )
                    nodes.forEach { add(it.outbound) }
                    add(
                        buildJsonObject {
                            put("type", "direct")
                            put("tag", DIRECT_TAG)
                        },
                    )
                },
            )
            put(
                "route",
                buildJsonObject {
                    put(
                        "rules",
                        buildJsonArray {
                            add(buildJsonObject { put("action", "sniff") })
                            add(
                                buildJsonObject {
                                    put("protocol", "dns")
                                    put("action", "hijack-dns")
                                },
                            )
                            if (settings.blockQuic) {
                                // QUIC is the first thing to break on a filtered link, and a
                                // blocked UDP flow stalls for seconds before the app retries
                                // over TCP; rejecting it here makes that switch immediate.
                                add(
                                    buildJsonObject {
                                        put("protocol", "quic")
                                        put("action", "reject")
                                    },
                                )
                            }
                            if (settings.bypassLan) {
                                add(
                                    buildJsonObject {
                                        put("ip_is_private", true)
                                        put("outbound", DIRECT_TAG)
                                    },
                                )
                            }
                        },
                    )
                    put("final", SELECTOR_TAG)
                    put("auto_detect_interface", true)
                },
            )
        }
        return prettyJson.encodeToString(JsonObject.serializer(), config)
    }

    /** The tag of the currently selected node, after collision-free tagging. */
    fun activeTag(profile: Profile, xrayAvailable: Boolean = true, xraySkip: Set<Int> = emptySet()): String? {
        if (profile.nodes.isEmpty()) return null
        val nodes = usableNodes(profile, xrayAvailable, xraySkip)
        if (nodes.isEmpty()) return null
        return nodes[activeIndex(profile, nodes)].tag
    }

    /** The node the tunnel will actually use, which may differ if Xray is missing. */
    fun activeNode(profile: Profile, xrayAvailable: Boolean = true, xraySkip: Set<Int> = emptySet()): ProxyNode? {
        val tag = activeTag(profile, xrayAvailable, xraySkip) ?: return null
        return usableNodes(profile, xrayAvailable, xraySkip).firstOrNull { it.tag == tag }
    }

    /**
     * The nodes this build can run. Nodes that need the Xray core are dropped when it
     * is missing or cannot load them, and otherwise point at the local Xray SOCKS port
     * that serves them; that port stays tied to the node's position in the profile.
     */
    private fun usableNodes(
        profile: Profile,
        xrayAvailable: Boolean,
        xraySkip: Set<Int>,
    ): List<ProxyNode> =
        assignTags(profile.nodes)
            .withIndex()
            .filter { (index, node) -> !node.viaXray || (xrayAvailable && index !in xraySkip) }
            .map { (index, node) ->
                if (node.viaXray) {
                    node.copy(outbound = bridgeOutbound(node.tag, XrayBridge.port(index)))
                } else {
                    node
                }
            }

    private fun activeIndex(profile: Profile, nodes: List<ProxyNode>): Int =
        nodes.indexOfFirst { it.tag == profile.selectedTag }.takeIf { it >= 0 } ?: 0

    /** The sing-box outbound that hands a node over to the Xray process. */
    private fun bridgeOutbound(tag: String, port: Int): JsonObject = buildJsonObject {
        put("type", "socks")
        put("tag", tag)
        put("server", "127.0.0.1")
        put("server_port", port)
        put("version", "5")
    }

    /**
     * Node servers that are host names. The core has to resolve them to be able to dial
     * the proxy at all, so the default (proxied) resolver would ask the very proxy it is
     * still dialing and fail with `DNS query loopback`.
     */
    private fun bootstrapDomains(nodes: List<ProxyNode>): List<String> {
        val domains = linkedSetOf<String>()
        nodes.forEach { node ->
            val server = (node.outbound["server"] as? JsonPrimitive)?.contentOrNull ?: node.server
            val host = server.trim().trim('[', ']')
            if (host.isNotEmpty() && !isIpLiteral(host)) domains += host
        }
        return domains.toList()
    }

    /** Bare host of a DNS address such as `https://dns.google/dns-query`. */
    private fun hostOf(address: String): String? {
        val withoutScheme = address.substringAfter("://", address)
        var host = withoutScheme.substringBefore('/').substringAfterLast('@')
        host = when {
            host.startsWith("[") -> host.substringAfter('[').substringBefore(']')
            host.count { it == ':' } == 1 -> host.substringBefore(':')
            else -> host
        }
        return host.trim().ifEmpty { null }
    }

    private fun isIpLiteral(value: String): Boolean =
        value.contains(':') || IPV4_HOST.matches(value)

    private val IPV4_HOST = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    private fun dnsConfig(settings: AppSettings, bootstrap: List<String>): JsonObject = buildJsonObject {
        put(
            "servers",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("tag", "dns-remote")
                        put("address", settings.remoteDns)
                        put("detour", SELECTOR_TAG)
                        // A DoH/DoT endpoint given by host name must be resolved before the
                        // proxy exists, so bootstrap it through the direct resolver.
                        hostOf(settings.remoteDns)
                            ?.takeIf { !isIpLiteral(it) }
                            ?.let { put("address_resolver", "dns-local") }
                    },
                )
                add(
                    buildJsonObject {
                        put("tag", "dns-local")
                        put("address", settings.localDns)
                        put("detour", DIRECT_TAG)
                    },
                )
            },
        )
        put("final", "dns-remote")
        put("strategy", if (settings.ipv6) "prefer_ipv4" else "ipv4_only")
        if (bootstrap.isNotEmpty()) {
            put(
                "rules",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("domain", buildJsonArray { bootstrap.forEach { add(it) } })
                            put("server", "dns-local")
                        },
                    )
                },
            )
        }
    }

    private fun inbound(settings: AppSettings): JsonObject =
        if (settings.tunnelMode == TunnelMode.VPN) {
            buildJsonObject {
                put("type", "tun")
                put("tag", "tun-in")
                put(
                    "address",
                    buildJsonArray {
                        add(TUN_IPV4)
                        if (settings.ipv6) add(TUN_IPV6)
                    },
                )
                put("mtu", settings.tunMtu.coerceIn(AppSettings.MIN_MTU, AppSettings.MAX_MTU))
                put("auto_route", true)
                put("strict_route", false)
                put("stack", "gvisor")
            }
        } else {
            buildJsonObject {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", if (settings.allowLan) "0.0.0.0" else "127.0.0.1")
                put("listen_port", settings.proxyPort.coerceIn(MIN_PROXY_PORT, MAX_PROXY_PORT))
            }
        }

    /**
     * Node tags must stay unique and must not shadow our own outbound tags, because
     * sing-box resolves them by name.
     */
    private fun assignTags(nodes: List<ProxyNode>): List<ProxyNode> {
        val used = mutableSetOf(SELECTOR_TAG, DIRECT_TAG, "tun-in", "mixed-in", "dns-remote", "dns-local")
        return nodes.map { node ->
            var tag = node.tag.trim().ifBlank { node.type }
            if (!used.add(tag)) {
                var index = 2
                while (!used.add("$tag $index")) index++
                tag = "$tag $index"
            }
            if (tag == node.tag) {
                node
            } else {
                val rebuilt = JsonObject(node.outbound.toMutableMap().apply { put("tag", JsonPrimitive(tag)) })
                node.copy(tag = tag, outbound = rebuilt)
            }
        }
    }
}

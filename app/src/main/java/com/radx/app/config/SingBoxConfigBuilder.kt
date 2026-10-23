package com.radx.app.config

import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.ProxyNode
import com.radx.app.data.TunnelMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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
 */
object SingBoxConfigBuilder {

    const val PROXY_PORT = 7890
    const val SELECTOR_TAG = "proxy"
    const val DIRECT_TAG = "direct"

    private const val TUN_IPV4 = "172.19.0.1/30"
    private const val TUN_IPV6 = "fdfe:dcba:9876::1/126"

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun build(profile: Profile, settings: AppSettings): String {
        require(profile.nodes.isNotEmpty()) { "This profile has no nodes" }

        val nodes = assignTags(profile.nodes)
        val activeIndex = profile.nodes
            .indexOfFirst { it.tag == profile.selectedTag }
            .takeIf { it >= 0 } ?: 0
        val activeTag = nodes[activeIndex].tag

        val config = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    put("level", "warn")
                    put("timestamp", true)
                },
            )
            put("dns", dnsConfig(settings))
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
                            add(
                                buildJsonObject {
                                    put("ip_is_private", true)
                                    put("outbound", DIRECT_TAG)
                                },
                            )
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
    fun activeTag(profile: Profile): String? {
        if (profile.nodes.isEmpty()) return null
        val nodes = assignTags(profile.nodes)
        val index = profile.nodes.indexOfFirst { it.tag == profile.selectedTag }.takeIf { it >= 0 } ?: 0
        return nodes[index].tag
    }

    private fun dnsConfig(settings: AppSettings): JsonObject = buildJsonObject {
        put(
            "servers",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("tag", "dns-remote")
                        put("address", settings.remoteDns)
                        put("detour", SELECTOR_TAG)
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
                put("mtu", 9000)
                put("auto_route", true)
                put("strict_route", false)
                put("stack", "gvisor")
            }
        } else {
            buildJsonObject {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", if (settings.allowLan) "0.0.0.0" else "127.0.0.1")
                put("listen_port", PROXY_PORT)
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

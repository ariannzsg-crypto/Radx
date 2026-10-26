package com.radx.app.config

import com.radx.app.data.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Local SOCKS ports that expose Xray-backed nodes to the sing-box core.
 *
 * A node keeps its position in the profile, so both cores agree on the mapping
 * without having to store the port on the node itself.
 */
object XrayBridge {

    const val BASE_PORT = 23300

    /** Keeps the port range well inside the ephemeral port space. */
    const val MAX_NODES = 500

    fun port(index: Int): Int = BASE_PORT + index
}

/**
 * Builds the configuration handed to the bundled Xray core.
 *
 * Xray runs as a separate process and only speaks SOCKS on the loopback interface;
 * sing-box is the one that owns the tun. Every node gets its own SOCKS inbound and
 * a routing rule that pins that inbound to its outbound, so switching nodes is just
 * a matter of which local port sing-box dials and needs no core restart.
 */
object XrayConfigBuilder {

    const val INBOUND_PREFIX = "radx-xray-in-"
    const val OUTBOUND_PREFIX = "radx-xray-out-"
    private const val DIRECT_TAG = "radx-xray-direct"

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun hasNodes(nodes: List<ProxyNode>): Boolean = nodes.any { it.viaXray }

    /**
     * The full Xray config, or null when the profile has nothing for this core.
     *
     * @throws IllegalArgumentException when the profile carries more Xray nodes than
     * the bridged port range allows.
     */
    fun build(nodes: List<ProxyNode>): String? {
        val bridged = nodes.withIndex().filter { it.value.viaXray }
        if (bridged.isEmpty()) return null
        require(bridged.size <= XrayBridge.MAX_NODES) {
            "Too many Xray nodes in one profile (${bridged.size} > ${XrayBridge.MAX_NODES})"
        }

        val config = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    // "info" is what makes the log screen show which node a
                    // connection used; the app keeps only the last few hundred lines.
                    put("loglevel", "info")
                },
            )
            put(
                "inbounds",
                buildJsonArray {
                    bridged.forEach { (index, _) ->
                        add(
                            buildJsonObject {
                                put("tag", inboundTag(index))
                                put("listen", "127.0.0.1")
                                put("port", XrayBridge.port(index))
                                put("protocol", "socks")
                                put(
                                    "settings",
                                    buildJsonObject {
                                        put("auth", "noauth")
                                        put("udp", true)
                                    },
                                )
                            },
                        )
                    }
                },
            )
            put(
                "outbounds",
                buildJsonArray {
                    bridged.forEach { (index, node) ->
                        add(
                            JsonObject(
                                node.xray!!.toMutableMap().apply {
                                    put("tag", JsonPrimitive(outboundTag(index)))
                                },
                            ),
                        )
                    }
                    add(
                        buildJsonObject {
                            put("tag", DIRECT_TAG)
                            put("protocol", "freedom")
                        },
                    )
                },
            )
            put(
                "routing",
                buildJsonObject {
                    put("domainStrategy", "AsIs")
                    put(
                        "rules",
                        buildJsonArray {
                            bridged.forEach { (index, _) ->
                                add(
                                    buildJsonObject {
                                        put("type", "field")
                                        put("inboundTag", buildJsonArray { add(inboundTag(index)) })
                                        put("outboundTag", outboundTag(index))
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        return prettyJson.encodeToString(JsonObject.serializer(), config)
    }

    fun inboundTag(index: Int): String = "$INBOUND_PREFIX$index"

    fun outboundTag(index: Int): String = "$OUTBOUND_PREFIX$index"
}

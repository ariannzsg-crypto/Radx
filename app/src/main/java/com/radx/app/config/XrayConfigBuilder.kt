package com.radx.app.config

import com.radx.app.data.AppSettings
import com.radx.app.data.LogLevel
import com.radx.app.data.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

    /**
     * Why the core refuses to load this node, or null when it loads fine.
     *
     * Xray 26.x rejects a VLESS or Trojan outbound that carries no transport security
     * ("vless without TLS or other encryption is prohibited unless the server address
     * is a private IP or domain"). A single such node aborts the whole config load, so
     * it has to be filtered out before the core is started instead of breaking every
     * other node in the profile.
     */
    fun rejectionReason(node: ProxyNode): String? {
        val outbound = node.xray ?: return null
        val protocol = outbound.string("protocol") ?: return null
        if (protocol != "vless" && protocol != "trojan") return null

        val security = outbound["streamSettings"]?.jsonObject?.string("security").orEmpty()
        if (security.isNotEmpty() && security != "none") return null
        if (protocol == "vless" && !outbound.vlessEncryption().isNullOrNone()) return null
        if (!requiresTransportSecurity(node.server)) return null

        return "$protocol without TLS cannot be loaded by this core"
    }

    /**
     * Index of the bridge outbound the core named when it refused to load a config,
     * parsed out of `failed to build outbound config with tag radx-xray-out-N`.
     */
    fun rejectedOutboundIndex(log: String): Int? =
        REJECTED_OUTBOUND.find(log)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * The full Xray config, or null when the profile has nothing left for this core.
     *
     * [skip] holds node positions the core cannot load; their ports stay reserved so
     * the sing-box bridge keeps dialing the same port for every other node.
     *
     * @throws IllegalArgumentException when the profile carries more Xray nodes than
     * the bridged port range allows.
     */
    fun build(
        nodes: List<ProxyNode>,
        skip: Set<Int> = emptySet(),
        settings: AppSettings? = null,
    ): String? {
        val bridged = nodes.withIndex().filter { it.value.viaXray && it.index !in skip }
        if (bridged.isEmpty()) return null
        require(bridged.size <= XrayBridge.MAX_NODES) {
            "Too many Xray nodes in one profile (${bridged.size} > ${XrayBridge.MAX_NODES})"
        }

        val config = buildJsonObject {
            put(
                "log",
                buildJsonObject {
                    // The log screen shows which node a connection used, so it follows
                    // the level picked in the settings; the app keeps the last few
                    // hundred lines either way.
                    put("loglevel", xrayLogLevel(settings?.logLevel))
                },
            )
            put(
                "inbounds",
                buildJsonArray {
                    bridged.forEach { (index, _) ->
                        add(
                            buildJsonObject {
                                put("tag", inboundTag(index))
                                // The bridge is loopback-only unless the user opened the
                                // local proxy to the LAN, which is also when the
                                // credentials in the settings start to matter.
                                put("listen", if (settings?.allowLan == true) "0.0.0.0" else "127.0.0.1")
                                put("port", XrayBridge.port(index))
                                put("protocol", "socks")
                                put("settings", socksSettings(settings))
                            },
                        )
                    }
                },
            )
            put(
                "outbounds",
                buildJsonArray {
                    bridged.forEach { (index, node) ->
                        add(outbound(node.xray!!, outboundTag(index), settings))
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

    /** The level names the core itself accepts. */
    private fun xrayLogLevel(level: LogLevel?): String = when (level) {
        LogLevel.ERROR -> "error"
        LogLevel.WARN -> "warning"
        LogLevel.DEBUG -> "debug"
        else -> "info"
    }

    private fun socksSettings(settings: AppSettings?): JsonObject = buildJsonObject {
        val user = settings?.proxyUser.orEmpty().trim()
        if (user.isEmpty()) {
            put("auth", "noauth")
        } else {
            put("auth", "password")
            put(
                "accounts",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("user", user)
                            put("pass", settings?.proxyPass.orEmpty())
                        },
                    )
                },
            )
        }
        put("udp", settings?.socksUdp ?: true)
    }

    /**
     * The outbound with the app's transport tweaks applied.
     *
     * Both options live in the node's own stream settings, so they are added here
     * rather than in the parser: switching them on must not need a re-import.
     */
    private fun outbound(source: JsonObject, tag: String, settings: AppSettings?): JsonObject {
        val fields = source.toMutableMap()
        fields["tag"] = JsonPrimitive(tag)
        if (settings == null) return JsonObject(fields)

        if (settings.muxEnabled) {
            fields["mux"] = buildJsonObject {
                put("enabled", true)
                put("concurrency", settings.muxConcurrency.coerceIn(AppSettings.MIN_MUX_STREAMS, AppSettings.MAX_MUX_STREAMS))
            }
        }
        if (settings.fragmentEnabled) {
            val stream = (source["streamSettings"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            stream["fragment"] = buildJsonObject {
                put("packets", "tlshello")
                put("length", settings.fragmentSize.ifBlank { "100-200" })
                put("interval", settings.fragmentInterval.ifBlank { "10-20" })
            }
            fields["streamSettings"] = JsonObject(stream)
        }
        return JsonObject(fields)
    }

    private val REJECTED_OUTBOUND = Regex("failed to build outbound config with tag " + Regex.escape(OUTBOUND_PREFIX) + "(\\d+)")

    /**
     * True when an unencrypted VLESS/Trojan outbound is refused: the core only exempts
     * servers on a private address, mirroring its own built-in private IP/domain lists.
     */
    private fun requiresTransportSecurity(server: String): Boolean {
        val host = server.trim().trim('[', ']').lowercase().removeSuffix(".")
        if (host.isEmpty()) return false
        if (PRIVATE_DOMAIN_SUFFIXES.any { host == it || host.endsWith(".$it") }) return false
        // A bare host name (`localhost`, a container name) counts as private; a dotted
        // name or a literal address is treated as a real upstream that must be encrypted.
        if (!host.contains(':') && !IPV4.matches(host)) return host.contains('.')
        return !isPrivateAddress(host)
    }

    private fun isPrivateAddress(host: String): Boolean {
        if (host.contains(':')) {
            // Loopback, link-local and unique-local addresses are never public.
            return host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")
        }
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) return false
        val (a, b) = octets
        return a == 10 || a == 127 || a == 0 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }

    private fun JsonObject.vlessEncryption(): String? {
        val vnext = this["settings"]?.jsonObject?.get("vnext")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val user = vnext["users"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        return user.string("encryption")
    }

    private fun String?.isNullOrNone(): Boolean = this.isNullOrEmpty() || this == "none"

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private val IPV4 = Regex("\\d{1,3}(\\.\\d{1,3}){3}")

    /** Domains the core treats as private, so an unencrypted outbound is still allowed. */
    private val PRIVATE_DOMAIN_SUFFIXES =
        setOf("localhost", "local", "localdomain", "lan", "internal", "home", "home.arpa")
}

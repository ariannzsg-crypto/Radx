package com.radx.app.config

import com.radx.app.data.ProxyNode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Reads the `proxies:` section of a Clash Meta / mihomo profile and converts every
 * entry into a sing-box outbound.
 *
 * Routing rules and proxy groups are intentionally not imported in this first
 * version: Radx builds its own `selector` over all parsed nodes, so a profile works
 * immediately no matter which client produced it.
 */
object ClashConfigParser {

    private val proxiesHeader = Regex("(?m)^\\s*proxies\\s*:")

    fun isClash(text: String): Boolean = proxiesHeader.containsMatchIn(text)

    data class Result(val nodes: List<ProxyNode>, val warnings: List<String>)

    fun parse(text: String): Result {
        val loaderOptions = LoaderOptions().apply {
            maxAliasesForCollections = 200
            isAllowDuplicateKeys = true
        }
        val root = runCatching {
            Yaml(SafeConstructor(loaderOptions)).load<Any>(text)
        }.getOrNull() as? Map<*, *>
            ?: return Result(emptyList(), listOf("Invalid Clash YAML"))

        val proxies = root["proxies"] as? List<*>
            ?: return Result(emptyList(), listOf("No proxies found in this YAML"))

        val warnings = mutableListOf<String>()
        if (root.containsKey("rules")) {
            warnings += "Clash routing rules are not imported; Radx uses a single proxy selector"
        }

        val nodes = proxies.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            convert(map, warnings)
        }
        return Result(ensureUniqueTags(nodes), warnings)
    }

    // ------------------------------------------------------------- converters

    private fun convert(proxy: Map<*, *>, warnings: MutableList<String>): ProxyNode? {
        val type = proxy.str("type")?.lowercase() ?: return null
        val name = proxy.str("name") ?: return null
        val server = proxy.str("server") ?: return null
        val port = proxy.int("port") ?: 443

        val outbound: JsonObject = when (type) {
            "ss" -> shadowsocks(proxy, name, server, port)
            "vmess" -> vmess(proxy, name, server, port)
            "vless" -> vless(proxy, name, server, port)
            "trojan" -> trojan(proxy, name, server, port)
            "hysteria2", "hy2" -> hysteria2(proxy, name, server, port)
            "tuic" -> tuic(proxy, name, server, port)
            "socks5", "socks" -> socks(proxy, name, server, port)
            "http" -> http(proxy, name, server, port)
            else -> {
                warnings += "Unsupported proxy type \"$type\" ($name)"
                return null
            }
        }
        return ProxyNode(tag = name, name = name, type = type, server = server, port = port, outbound = outbound)
    }

    private fun shadowsocks(proxy: Map<*, *>, name: String, server: String, port: Int): JsonObject =
        buildJsonObject {
            put("type", "shadowsocks")
            put("tag", name)
            put("server", server)
            put("server_port", port)
            put("method", proxy.str("cipher") ?: "aes-256-gcm")
            put("password", proxy.str("password") ?: "")
            when (val plugin = proxy.str("plugin")?.lowercase()) {
                null -> Unit
                "obfs" -> {
                    put("plugin", "obfs-local")
                    val opts = proxy.map("plugin-opts")
                    val mode = opts?.str("mode") ?: "http"
                    val host = opts?.str("host") ?: ""
                    put("plugin_opts", "obfs=$mode" + if (host.isNotBlank()) ";obfs-host=$host" else "")
                }

                "v2ray-plugin" -> {
                    put("plugin", "v2ray-plugin")
                    val opts = proxy.map("plugin-opts")
                    val parts = mutableListOf<String>()
                    opts?.str("mode")?.let { parts += "mode=$it" }
                    opts?.str("host")?.let { parts += "host=$it" }
                    opts?.str("path")?.let { parts += "path=$it" }
                    if (opts?.bool("tls") == true) parts += "tls"
                    put("plugin_opts", parts.joinToString(";"))
                }

                else -> put("plugin", plugin)
            }
        }

    private fun vmess(proxy: Map<*, *>, name: String, server: String, port: Int): JsonObject =
        buildJsonObject {
            put("type", "vmess")
            put("tag", name)
            put("server", server)
            put("server_port", port)
            put("uuid", proxy.str("uuid") ?: "")
            put("security", proxy.str("cipher") ?: "auto")
            put("alter_id", proxy.int("alterId") ?: proxy.int("alter-id") ?: 0)
            proxy.tlsObject()?.let { put("tls", it) }
            transportFor(proxy)?.let { put("transport", it) }
        }

    private fun vless(proxy: Map<*, *>, name: String, server: String, port: Int): JsonObject =
        buildJsonObject {
            put("type", "vless")
            put("tag", name)
            put("server", server)
            put("server_port", port)
            put("uuid", proxy.str("uuid") ?: "")
            proxy.str("flow")?.let { put("flow", it) }
            proxy.tlsObject()?.let { put("tls", it) }
            transportFor(proxy)?.let { put("transport", it) }
        }

    private fun trojan(proxy: Map<*, *>, name: String, server: String, port: Int): JsonObject =
        buildJsonObject {
            put("type", "trojan")
            put("tag", name)
            put("server", server)
            put("server_port", port)
            put("password", proxy.str("password") ?: "")
            put("tls", proxy.tlsObject() ?: defaultTls(proxy))
            transportFor(proxy)?.let { put("transport", it) }
        }

    private fun hysteria2(proxy: Map<*, *>, name: String, server: String, port: Int): JsonObject =
        buildJsonObject {
            put("type", "hysteria2")
            put("tag", name)
            put("server", server)
            put("server_port", port)
            put("password", proxy.str("password") ?: proxy.str("auth") ?: "")
            put("tls", proxy.tlsObject() ?: defaultTls(proxy))
            proxy.str("obfs")?.takeIf { it != "none" }?.let { obfs ->
                put(
                    "obfs",
                    buildJsonObject {
                        put("type", obfs)
                        put("password", proxy.str("obfs-password") ?: "")
                    },
                )
            }
            proxy.int("up")?.let { put("up_mbps", it) }
            proxy.int("down")?.let { put("down_mbps", it) }
        }

    private fun tuic(proxy: Map<*, *>, name: String, server: String, port: Int): JsonObject =
        buildJsonObject {
            put("type", "tuic")
            put("tag", name)
            put("server", server)
            put("server_port", port)
            put("uuid", proxy.str("uuid") ?: "")
            put("password", proxy.str("password") ?: "")
            proxy.str("congestion-controller")?.let { put("congestion_control", it) }
            proxy.str("udp-relay-mode")?.let { put("udp_relay_mode", it) }
            put("tls", proxy.tlsObject() ?: defaultTls(proxy))
        }

    private fun socks(proxy: Map<*, *>, name: String, server: String, port: Int): JsonObject =
        buildJsonObject {
            put("type", "socks")
            put("tag", name)
            put("server", server)
            put("server_port", port)
            put("version", "5")
            proxy.str("username")?.let { put("username", it) }
            proxy.str("password")?.let { put("password", it) }
        }

    private fun http(proxy: Map<*, *>, name: String, server: String, port: Int): JsonObject =
        buildJsonObject {
            put("type", "http")
            put("tag", name)
            put("server", server)
            put("server_port", port)
            proxy.str("username")?.let { put("username", it) }
            proxy.str("password")?.let { put("password", it) }
            if (proxy.bool("tls") == true) put("tls", proxy.tlsObject() ?: defaultTls(proxy))
        }

    // ---------------------------------------------------------------- helpers

    private fun Map<*, *>.tlsObject(): JsonObject? {
        val enabled = bool("tls") == true
        val security = str("security")?.lowercase()
        val reality = map("reality-opts")
        if (!enabled && reality == null && security != "reality") return null
        return buildJsonObject {
            put("enabled", true)
            (str("sni") ?: str("servername") ?: str("server-name"))?.let { put("server_name", it) }
            put("insecure", bool("skip-cert-verify") == true)
            val alpn = list("alpn")?.mapNotNull { it?.toString() }.orEmpty()
            if (alpn.isNotEmpty()) put("alpn", ConfigUtils.jsonStringList(alpn))
            val fingerprint = str("client-fingerprint")
                ?: if (reality != null) "chrome" else null
            fingerprint?.let {
                put(
                    "utls",
                    buildJsonObject {
                        put("enabled", true)
                        put("fingerprint", it)
                    },
                )
            }
            if (reality != null) {
                put(
                    "reality",
                    buildJsonObject {
                        put("enabled", true)
                        put("public_key", reality.str("public-key") ?: "")
                        put("short_id", reality.str("short-id") ?: "")
                    },
                )
            }
        }
    }

    private fun defaultTls(proxy: Map<*, *>): JsonObject = buildJsonObject {
        put("enabled", true)
        (proxy.str("sni") ?: proxy.str("servername"))?.let { put("server_name", it) }
        put("insecure", proxy.bool("skip-cert-verify") == true)
    }

    private fun transportFor(proxy: Map<*, *>): JsonObject? = when (val network = proxy.str("network")?.lowercase()) {
        "ws" -> {
            val opts = proxy.map("ws-opts")
            buildJsonObject {
                put("type", "ws")
                put("path", opts?.str("path") ?: "/")
                val host = opts?.map("headers")?.str("Host") ?: opts?.map("headers")?.str("host")
                if (!host.isNullOrBlank()) {
                    put("headers", buildJsonObject { put("Host", host) })
                }
            }
        }

        "grpc" -> {
            val opts = proxy.map("grpc-opts")
            buildJsonObject {
                put("type", "grpc")
                opts?.str("grpc-service-name")?.let { put("service_name", it) }
            }
        }

        "h2", "http" -> {
            val opts = proxy.map("h2-opts")
            buildJsonObject {
                put("type", "http")
                put("path", opts?.str("path") ?: "/")
                val hosts = opts?.list("host")?.mapNotNull { it?.toString() }
                if (!hosts.isNullOrEmpty()) put("host", buildJsonArray { hosts.forEach { add(it) } })
            }
        }

        "tcp", null -> null
        else -> {
            @Suppress("UNUSED_VARIABLE") val ignored = network
            null
        }
    }

    private fun Map<*, *>.str(key: String): String? {
        val value = this[key] ?: return null
        val text = value.toString().trim()
        return text.ifBlank { null }
    }

    private fun Map<*, *>.int(key: String): Int? = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }

    private fun Map<*, *>.bool(key: String): Boolean? = when (val value = this[key]) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }

        else -> null
    }

    private fun Map<*, *>.map(key: String): Map<*, *>? = this[key] as? Map<*, *>

    private fun Map<*, *>.list(key: String): List<*>? = this[key] as? List<*>
}

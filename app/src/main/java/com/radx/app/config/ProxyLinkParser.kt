package com.radx.app.config

import com.radx.app.data.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Converts proxy share links (vmess/vless/trojan/ss/hysteria2/tuic/socks) into
 * sing-box outbound objects.
 */
object ProxyLinkParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(link: String): ProxyNode? {
        val trimmed = link.trim()
        if (trimmed.isEmpty() || !ConfigUtils.looksLikeLink(trimmed)) return null
        return when (ConfigUtils.schemeOf(trimmed)) {
            "vmess" -> parseVmess(trimmed)
            "vless" -> parseVless(trimmed)
            "trojan" -> parseTrojan(trimmed)
            "ss" -> parseShadowsocks(trimmed)
            "hysteria2", "hy2" -> parseHysteria2(trimmed)
            "tuic" -> parseTuic(trimmed)
            "socks" -> parseSocks(trimmed)
            else -> null
        }
    }

    // ---------------------------------------------------------------- vmess

    private fun parseVmess(link: String): ProxyNode? {
        val decoded = ConfigUtils.decodeBase64(link.substringAfter("://")) ?: return null
        val source = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null
        val host = source.string("add") ?: return null
        val port = source.int("port") ?: 443
        val uuid = source.string("id") ?: return null
        val name = cleanName(source.string("ps") ?: "$host:$port")
        val network = source.string("net")?.lowercase() ?: "tcp"
        val tlsEnabled = source.string("tls")?.lowercase() == "tls"
        val serverName = source.string("sni") ?: source.string("host") ?: ""
        val path = source.string("path")
        val hostHeader = source.string("host")
        // A transport neither core can build would turn into a node that never connects.
        if (!supportsTransport(network)) return null
        if (isXrayTransport(network)) {
            val options = buildMap {
                put("security", source.string("tls")?.lowercase() ?: "none")
                source.string("sni")?.let { put("sni", it) }
                source.string("host")?.let { put("host", it) }
                source.string("path")?.let { put("path", it) }
                source.string("alpn")?.let { put("alpn", it) }
                source.string("fp")?.let { put("fp", it) }
                source.string("pbk")?.let { put("pbk", it) }
                source.string("sid")?.let { put("sid", it) }
                source.string("spx")?.let { put("spx", it) }
                source.string("mode")?.let { put("mode", it) }
                source.string("extra")?.let { put("extra", it) }
                source.string("type")?.takeIf { network != "xhttp" }?.let { put("headertype", it) }
            }
            val user = buildJsonObject {
                put("id", uuid)
                put("alterId", source.int("aid") ?: 0)
                put("security", source.string("scy") ?: "auto")
            }
            return ProxyNode(
                tag = name,
                name = name,
                type = "vmess",
                server = host,
                port = port,
                outbound = bridgePlaceholder(name),
                xray = xrayVnext("vmess", host, port, user, network, options),
            )
        }

        val outbound = buildJsonObject {
            put("type", "vmess")
            put("tag", name)
            put("server", host)
            put("server_port", port)
            put("uuid", uuid)
            put("security", source.string("scy") ?: "auto")
            put("alter_id", source.int("aid") ?: 0)
            if (tlsEnabled) put("tls", tlsObject(serverName, false, null, null, null))
            transport(network, path, hostHeader)?.let { put("transport", it) }
        }
        return ProxyNode(tag = name, name = name, type = "vmess", server = host, port = port, outbound = outbound)
    }

    // ---------------------------------------------------------------- vless

    private fun parseVless(link: String): ProxyNode? {
        val ep = endpoint(link, 443) ?: return null
        val uuid = ep.userInfo?.substringBefore(':')?.takeIf { it.isNotBlank() } ?: return null
        val query = ep.query
        val name = cleanName(ep.fragment ?: "${ep.host}:${ep.port}")
        val security = query["security"]?.lowercase() ?: "none"
        val network = query["type"]?.lowercase() ?: "tcp"
        if (!supportsTransport(network)) return null
        // XHTTP only exists in Xray, so such nodes run there and are bridged into
        // sing-box through a local SOCKS inbound.
        if (isXrayTransport(network)) {
            return ProxyNode(
                tag = name,
                name = name,
                type = "vless",
                server = ep.host,
                port = ep.port,
                outbound = bridgePlaceholder(name),
                xray = xrayVless(ep.host, ep.port, uuid, network, query),
            )
        }

        val outbound = buildJsonObject {
            put("type", "vless")
            put("tag", name)
            put("server", ep.host)
            put("server_port", ep.port)
            put("uuid", uuid)
            query["flow"]?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
            query["packetencoding"]?.takeIf { it.isNotBlank() }?.let { put("packet_encoding", it) }
            if (security == "tls" || security == "reality" || security == "xtls") {
                put(
                    "tls",
                    tlsObject(
                        serverName = query["sni"] ?: query["peer"] ?: query["host"] ?: "",
                        insecure = query["allowinsecure"].asBool() || query["insecure"].asBool(),
                        fingerprint = query["fp"],
                        realityPublicKey = query["pbk"].takeIf { security == "reality" },
                        realityShortId = query["sid"].takeIf { security == "reality" },
                        alpn = ConfigUtils.alpnList(query["alpn"]),
                    ),
                )
            }
            transport(network, query["path"], query["host"], query["servicename"])?.let { put("transport", it) }
        }
        return ProxyNode(tag = name, name = name, type = "vless", server = ep.host, port = ep.port, outbound = outbound)
    }

    // --------------------------------------------------------------- trojan

    private fun parseTrojan(link: String): ProxyNode? {
        val ep = endpoint(link, 443) ?: return null
        val password = ep.userInfo?.takeIf { it.isNotBlank() } ?: return null
        val query = ep.query
        val name = cleanName(ep.fragment ?: "${ep.host}:${ep.port}")
        val network = query["type"]?.lowercase() ?: "tcp"
        if (!supportsTransport(network)) return null
        if (isXrayTransport(network)) {
            return ProxyNode(
                tag = name,
                name = name,
                type = "trojan",
                server = ep.host,
                port = ep.port,
                outbound = bridgePlaceholder(name),
                xray = xrayTrojan(ep.host, ep.port, password, network, query),
            )
        }

        val outbound = buildJsonObject {
            put("type", "trojan")
            put("tag", name)
            put("server", ep.host)
            put("server_port", ep.port)
            put("password", password)
            put(
                "tls",
                tlsObject(
                    serverName = query["sni"] ?: query["peer"] ?: query["host"] ?: "",
                    insecure = query["allowinsecure"].asBool() || query["insecure"].asBool(),
                    fingerprint = query["fp"],
                    realityPublicKey = null,
                    realityShortId = null,
                    alpn = ConfigUtils.alpnList(query["alpn"]),
                ),
            )
            transport(network, query["path"], query["host"], query["servicename"])?.let { put("transport", it) }
        }
        return ProxyNode(tag = name, name = name, type = "trojan", server = ep.host, port = ep.port, outbound = outbound)
    }

    // ----------------------------------------------------------- shadowsocks

    private fun parseShadowsocks(link: String): ProxyNode? {
        val withoutScheme = link.substringAfter("://")
        val hashIndex = withoutScheme.indexOf('#')
        val fragment = if (hashIndex >= 0) ConfigUtils.decodeComponent(withoutScheme.substring(hashIndex + 1)) else null
        var rest = if (hashIndex >= 0) withoutScheme.substring(0, hashIndex) else withoutScheme

        val queryIndex = rest.indexOf('?')
        val query = mutableMapOf<String, String>()
        if (queryIndex >= 0) {
            rest.substring(queryIndex + 1).split('&').forEach { pair ->
                if (pair.isBlank()) return@forEach
                val key = ConfigUtils.decodeComponent(pair.substringBefore('=')).lowercase()
                query[key] = ConfigUtils.decodeComponent(pair.substringAfter('=', ""))
            }
            rest = rest.substring(0, queryIndex)
        }

        val atIndex = rest.lastIndexOf('@')
        val userInfo: String
        val hostPort: String
        if (atIndex >= 0) {
            userInfo = rest.substring(0, atIndex)
            hostPort = rest.substring(atIndex + 1)
        } else {
            val decoded = ConfigUtils.decodeBase64(rest) ?: return null
            val decodedAt = decoded.lastIndexOf('@')
            if (decodedAt < 0) return null
            userInfo = decoded.substring(0, decodedAt)
            hostPort = decoded.substring(decodedAt + 1)
        }

        val credentials = if (userInfo.contains(':')) {
            userInfo
        } else {
            ConfigUtils.decodeBase64(userInfo) ?: return null
        }
        val method = ConfigUtils.decodeComponent(credentials.substringBefore(':'))
        val password = ConfigUtils.decodeComponent(credentials.substringAfter(':', ""))
        if (method.isBlank() || password.isBlank()) return null

        val (host, port) = splitHostPort(hostPort, 8388) ?: return null
        val name = cleanName(fragment ?: "$host:$port")

        val outbound = buildJsonObject {
            put("type", "shadowsocks")
            put("tag", name)
            put("server", host)
            put("server_port", port)
            put("method", method)
            put("password", password)
            val rawPlugin = query["plugin"]?.takeIf { it.isNotBlank() }
            if (rawPlugin != null) {
                val pluginName = rawPlugin.substringBefore(';').let {
                    if (it == "simple-obfs") "obfs-local" else it
                }
                val pluginOpts = rawPlugin.substringAfter(';', "")
                put("plugin", pluginName)
                if (pluginOpts.isNotBlank()) put("plugin_opts", pluginOpts)
            }
        }
        return ProxyNode(tag = name, name = name, type = "shadowsocks", server = host, port = port, outbound = outbound)
    }

    // ------------------------------------------------------------ hysteria2

    private fun parseHysteria2(link: String): ProxyNode? {
        val ep = endpoint(link, 443) ?: return null
        val password = ep.userInfo?.takeIf { it.isNotBlank() } ?: return null
        val query = ep.query
        val name = cleanName(ep.fragment ?: "${ep.host}:${ep.port}")

        val outbound = buildJsonObject {
            put("type", "hysteria2")
            put("tag", name)
            put("server", ep.host)
            put("server_port", ep.port)
            put("password", password)
            put(
                "tls",
                tlsObject(
                    serverName = query["sni"] ?: query["peer"] ?: "",
                    insecure = query["insecure"].asBool() || query["allowinsecure"].asBool(),
                    fingerprint = query["fp"],
                    realityPublicKey = null,
                    realityShortId = null,
                    alpn = ConfigUtils.alpnList(query["alpn"]),
                ),
            )
            val obfs = query["obfs"]?.takeIf { it.isNotBlank() && it != "none" }
            if (obfs != null) {
                put(
                    "obfs",
                    buildJsonObject {
                        put("type", obfs)
                        put("password", query["obfs-password"] ?: query["obfs_password"] ?: "")
                    },
                )
            }
            query["up"]?.toIntOrNull()?.let { put("up_mbps", it) }
            query["down"]?.toIntOrNull()?.let { put("down_mbps", it) }
        }
        return ProxyNode(tag = name, name = name, type = "hysteria2", server = ep.host, port = ep.port, outbound = outbound)
    }

    // ----------------------------------------------------------------- tuic

    private fun parseTuic(link: String): ProxyNode? {
        val ep = endpoint(link, 443) ?: return null
        val userInfo = ep.userInfo ?: return null
        val uuid = userInfo.substringBefore(':')
        val password = userInfo.substringAfter(':', "")
        if (uuid.isBlank()) return null
        val query = ep.query
        val name = cleanName(ep.fragment ?: "${ep.host}:${ep.port}")

        val outbound = buildJsonObject {
            put("type", "tuic")
            put("tag", name)
            put("server", ep.host)
            put("server_port", ep.port)
            put("uuid", uuid)
            put("password", password)
            query["congestion_control"]?.takeIf { it.isNotBlank() }?.let { put("congestion_control", it) }
            query["udp_relay_mode"]?.takeIf { it.isNotBlank() }?.let { put("udp_relay_mode", it) }
            put(
                "tls",
                tlsObject(
                    serverName = query["sni"] ?: "",
                    insecure = query["insecure"].asBool() || query["allow_insecure"].asBool(),
                    fingerprint = null,
                    realityPublicKey = null,
                    realityShortId = null,
                    alpn = ConfigUtils.alpnList(query["alpn"]).ifEmpty { listOf("h3") },
                ),
            )
        }
        return ProxyNode(tag = name, name = name, type = "tuic", server = ep.host, port = ep.port, outbound = outbound)
    }

    // ---------------------------------------------------------------- socks

    private fun parseSocks(link: String): ProxyNode? {
        val ep = endpoint(link, 1080) ?: return null
        val userInfo = ep.userInfo ?: ""
        val credentials = when {
            userInfo.isBlank() -> null
            userInfo.contains(':') -> userInfo
            else -> ConfigUtils.decodeBase64(userInfo)
        }
        val name = cleanName(ep.fragment ?: "${ep.host}:${ep.port}")
        val outbound = buildJsonObject {
            put("type", "socks")
            put("tag", name)
            put("server", ep.host)
            put("server_port", ep.port)
            put("version", "5")
            if (credentials != null) {
                put("username", credentials.substringBefore(':'))
                put("password", credentials.substringAfter(':', ""))
            }
        }
        return ProxyNode(tag = name, name = name, type = "socks", server = ep.host, port = ep.port, outbound = outbound)
    }

    // ------------------------------------------------------------- xray core

    /**
     * Stand-in outbound for a node that runs on the Xray core. The config builder
     * replaces it with a SOCKS outbound pointing at the node's Xray port, so the
     * placeholder never reaches a running core.
     */
    private fun bridgePlaceholder(tag: String): JsonObject = buildJsonObject {
        put("type", "socks")
        put("tag", tag)
        put("server", "127.0.0.1")
        put("server_port", 0)
        put("version", "5")
    }

    private fun xrayVnext(
        protocol: String,
        host: String,
        port: Int,
        user: JsonObject,
        network: String,
        options: Map<String, String>,
    ): JsonObject = buildJsonObject {
        put("protocol", protocol)
        put(
            "settings",
            buildJsonObject {
                put(
                    "vnext",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("address", host)
                                put("port", port)
                                put("users", buildJsonArray { add(user) })
                            },
                        )
                    },
                )
            },
        )
        put("streamSettings", xrayStream(network, options))
    }

    private fun xrayVless(
        host: String,
        port: Int,
        uuid: String,
        network: String,
        query: Map<String, String>,
    ): JsonObject {
        val user = buildJsonObject {
            put("id", uuid)
            put("encryption", query["encryption"]?.takeIf { it.isNotBlank() } ?: "none")
            query["flow"]?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
        }
        return xrayVnext("vless", host, port, user, network, query)
    }

    private fun xrayTrojan(
        host: String,
        port: Int,
        password: String,
        network: String,
        query: Map<String, String>,
    ): JsonObject =
        buildJsonObject {
            put("protocol", "trojan")
            put(
                "settings",
                buildJsonObject {
                    put(
                        "servers",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("address", host)
                                    put("port", port)
                                    put("password", password)
                                },
                            )
                        },
                    )
                },
            )
            put("streamSettings", xrayStream(network, query))
        }

    /** Xray `streamSettings` for the transports sing-box cannot run. */
    private fun xrayStream(network: String, options: Map<String, String>): JsonObject = buildJsonObject {
        val transport = when (network.trim().lowercase()) {
            "splithttp" -> "xhttp"
            "mkcp" -> "kcp"
            else -> network
        }
        put("network", transport)

        val serverName = options["sni"]?.takeIf { it.isNotBlank() }
            ?: options["peer"]?.takeIf { it.isNotBlank() }
            ?: options["host"]?.substringBefore(',')?.substringBefore(';')?.takeIf { it.isNotBlank() }
            ?: ""
        val fingerprint = options["fp"]?.takeIf { it.isNotBlank() }
        val alpn = ConfigUtils.alpnList(options["alpn"])

        when (options["security"]?.trim()?.lowercase() ?: "none") {
            "tls", "xtls" -> {
                put("security", "tls")
                put(
                    "tlsSettings",
                    buildJsonObject {
                        put("serverName", serverName)
                        if (fingerprint != null) put("fingerprint", fingerprint)
                        if (alpn.isNotEmpty()) put("alpn", ConfigUtils.jsonStringList(alpn))
                        // Current Xray dropped `allowInsecure` and refuses to load a
                        // config that still carries it. The share-link equivalents are
                        // a pinned certificate hash (`pcs`) or a pinned name (`vcn`).
                        options["pcs"]?.takeIf { it.isNotBlank() }?.let { put("pinnedPeerCertSha256", it) }
                        options["vcn"]?.takeIf { it.isNotBlank() }?.let { put("verifyPeerCertByName", it) }
                    },
                )
            }

            "reality" -> {
                put("security", "reality")
                put(
                    "realitySettings",
                    buildJsonObject {
                        put("serverName", serverName)
                        put("fingerprint", fingerprint ?: "chrome")
                        put("publicKey", options["pbk"] ?: "")
                        put("shortId", options["sid"] ?: "")
                        options["spx"]?.takeIf { it.isNotBlank() }?.let { put("spiderX", it) }
                    },
                )
            }

            else -> put("security", "none")
        }

        when (transport) {
            "xhttp" -> put(
                "xhttpSettings",
                buildJsonObject {
                    put("path", options["path"]?.takeIf { it.isNotBlank() } ?: "/")
                    options["host"]?.takeIf { it.isNotBlank() }?.let { put("host", it) }
                    put("mode", options["mode"]?.takeIf { it.isNotBlank() } ?: "auto")
                    options["extra"]?.let { raw ->
                        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                            ?.let { put("extra", it) }
                    }
                },
            )

            "kcp" -> put(
                "kcpSettings",
                buildJsonObject {
                    put("mtu", 1350)
                    put(
                        "header",
                        buildJsonObject {
                            put("type", options["headertype"]?.takeIf { it.isNotBlank() } ?: "none")
                        },
                    )
                    options["seed"]?.takeIf { it.isNotBlank() }?.let { put("seed", it) }
                },
            )
        }
    }

    // -------------------------------------------------------------- helpers

    private data class Endpoint(
        val userInfo: String?,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val fragment: String?,
    )

    private fun endpoint(link: String, defaultPort: Int): Endpoint? {
        val withoutScheme = link.substringAfter("://")
        val hashIndex = withoutScheme.indexOf('#')
        val fragment = if (hashIndex >= 0) ConfigUtils.decodeComponent(withoutScheme.substring(hashIndex + 1)) else null
        var rest = if (hashIndex >= 0) withoutScheme.substring(0, hashIndex) else withoutScheme

        val queryIndex = rest.indexOf('?')
        val query = mutableMapOf<String, String>()
        if (queryIndex >= 0) {
            rest.substring(queryIndex + 1).split('&').forEach { pair ->
                if (pair.isBlank()) return@forEach
                val key = ConfigUtils.decodeComponent(pair.substringBefore('=')).lowercase()
                query[key] = ConfigUtils.decodeComponent(pair.substringAfter('=', ""))
            }
            rest = rest.substring(0, queryIndex)
        }

        val atIndex = rest.lastIndexOf('@')
        val userInfo = if (atIndex >= 0) ConfigUtils.decodeComponent(rest.substring(0, atIndex)) else null
        val hostPort = if (atIndex >= 0) rest.substring(atIndex + 1) else rest
        val parsed = splitHostPort(hostPort, defaultPort) ?: return null
        return Endpoint(userInfo, parsed.first, parsed.second, query, fragment)
    }

    private fun splitHostPort(value: String, defaultPort: Int): Pair<String, Int>? {
        val hostPort = value.trim().trimEnd('/')
        if (hostPort.isEmpty()) return null
        return if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end < 0) return null
            val host = hostPort.substring(1, end)
            val port = hostPort.substring(end + 1).removePrefix(":").toIntOrNull() ?: defaultPort
            host to port
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0) {
                val host = hostPort.substring(0, colon)
                val port = hostPort.substring(colon + 1).toIntOrNull() ?: defaultPort
                host.ifBlank { return null } to port
            } else {
                hostPort to defaultPort
            }
        }
    }

    private fun tlsObject(
        serverName: String,
        insecure: Boolean,
        fingerprint: String?,
        realityPublicKey: String?,
        realityShortId: String?,
        alpn: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("enabled", true)
        if (serverName.isNotBlank()) put("server_name", serverName)
        put("insecure", insecure)
        if (alpn.isNotEmpty()) put("alpn", ConfigUtils.jsonStringList(alpn))
        val effectiveFingerprint = fingerprint?.takeIf { it.isNotBlank() }
            ?: "chrome".takeIf { !realityPublicKey.isNullOrBlank() }
        if (effectiveFingerprint != null) {
            put(
                "utls",
                buildJsonObject {
                    put("enabled", true)
                    put("fingerprint", effectiveFingerprint)
                },
            )
        }
        if (!realityPublicKey.isNullOrBlank()) {
            put(
                "reality",
                buildJsonObject {
                    put("enabled", true)
                    put("public_key", realityPublicKey)
                    put("short_id", realityShortId ?: "")
                },
            )
        }
    }

    private fun transport(
        network: String,
        path: String?,
        host: String?,
        serviceName: String? = null,
    ): JsonObject? = when (network.lowercase()) {
        "ws", "websocket" -> buildJsonObject {
            put("type", "ws")
            put("path", path?.takeIf { it.isNotBlank() } ?: "/")
            if (!host.isNullOrBlank()) {
                put("headers", buildJsonObject { put("Host", host) })
            }
        }

        "grpc" -> buildJsonObject {
            put("type", "grpc")
            val name = serviceName?.takeIf { it.isNotBlank() } ?: path
            if (!name.isNullOrBlank()) put("service_name", name.trimStart('/'))
        }

        "h2", "http" -> buildJsonObject {
            put("type", "http")
            put("path", path?.takeIf { it.isNotBlank() } ?: "/")
            if (!host.isNullOrBlank()) put("host", buildJsonArray { add(host) })
        }

        "quic" -> buildJsonObject {
            put("type", "quic")
            if (!host.isNullOrBlank()) put("host", host)
        }

        "httpupgrade" -> buildJsonObject {
            put("type", "httpupgrade")
            put("host", host?.takeIf { it.isNotBlank() } ?: "")
            put("path", path?.takeIf { it.isNotBlank() } ?: "/")
        }

        else -> null
    }

    /**
     * Transports at least one bundled core can run. Anything else is refused instead
     * of becoming a node that never connects.
     */
    private fun supportsTransport(network: String): Boolean {
        val name = network.trim().lowercase()
        return name in PLAIN_TRANSPORTS || name in SUPPORTED_TRANSPORTS || name in XRAY_TRANSPORTS
    }

    /** Transports only the Xray core understands; those nodes run there. */
    private fun isXrayTransport(network: String): Boolean =
        network.trim().lowercase() in XRAY_TRANSPORTS

    /**
     * The transport of a share link when the core has no support for it, else null.
     * Lets the importer explain what it skipped instead of dropping nodes silently.
     */
    fun unsupportedTransport(link: String): String? {
        val network = transportParam(link) ?: return null
        val name = network.trim().lowercase()
        return name.takeIf { it.isNotEmpty() && !supportsTransport(it) }
    }

    private fun transportParam(link: String): String? = when (ConfigUtils.schemeOf(link)) {
        "vmess" -> ConfigUtils.decodeBase64(link.substringAfter("://"))
            ?.let { decoded ->
                runCatching { json.parseToJsonElement(decoded).jsonObject.string("net") }.getOrNull()
            }

        else -> link.substringAfter("://", "")
            .substringAfter('?', "")
            .substringBefore('#')
            .takeIf { it.isNotEmpty() }
            ?.split('&')
            ?.firstOrNull { pair -> pair.substringBefore('=').equals("type", ignoreCase = true) }
            ?.let { pair -> ConfigUtils.decodeComponent(pair.substringAfter('=', "")) }
    }

    private fun cleanName(raw: String): String =
        raw.trim().replace("\n", " ").replace("\r", " ").take(120).ifBlank { "Radx node" }

    private val PLAIN_TRANSPORTS = setOf("", "tcp", "raw", "none")
    private val SUPPORTED_TRANSPORTS = setOf("ws", "websocket", "grpc", "h2", "http", "quic", "httpupgrade")
    private val XRAY_TRANSPORTS = setOf("xhttp", "splithttp", "kcp", "mkcp")
}

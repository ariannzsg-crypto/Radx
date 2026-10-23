package com.radx.app.config

import com.radx.app.data.ProxyNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Pulls the proxy outbounds out of a sing-box JSON config. Radx rebuilds the
 * surrounding config (inbound, DNS, routing) itself, so imported profiles behave
 * the same as link-based ones.
 */
object SingBoxConfigParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val proxyTypes = setOf(
        "vmess", "vless", "trojan", "shadowsocks", "hysteria2", "tuic", "socks", "http",
    )

    data class Result(val nodes: List<ProxyNode>, val warnings: List<String>)

    fun looksLikeSingBox(text: String): Boolean = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        root["outbounds"] != null
    }.getOrDefault(false)

    fun parse(text: String): Result {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return Result(emptyList(), listOf("Invalid sing-box JSON"))
        val outbounds = runCatching { root["outbounds"]!!.jsonArray }.getOrNull()
            ?: return Result(emptyList(), listOf("No outbounds found"))

        val warnings = mutableListOf<String>()
        val nodes = outbounds.mapNotNull { element ->
            val outbound = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val type = outbound.string("type")?.lowercase() ?: return@mapNotNull null
            if (type !in proxyTypes) return@mapNotNull null
            val server = outbound.string("server") ?: return@mapNotNull null
            val port = outbound.int("server_port") ?: return@mapNotNull null
            val name = outbound.string("tag") ?: "$server:$port"
            val tagged = if (outbound["tag"] == null) {
                JsonObject(outbound + ("tag" to JsonPrimitive(name)))
            } else {
                outbound
            }
            ProxyNode(tag = name, name = name, type = type, server = server, port = port, outbound = tagged)
        }

        if (root.containsKey("route") || root.containsKey("rules")) {
            warnings += "Original sing-box routing rules are not imported"
        }
        return Result(ensureUniqueTags(nodes), warnings)
    }
}

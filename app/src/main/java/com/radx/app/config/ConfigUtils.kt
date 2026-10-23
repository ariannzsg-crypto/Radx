package com.radx.app.config

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import java.net.URLDecoder
import java.util.Base64

/** Small shared helpers for URL / base64 handling used by the parsers. */
internal object ConfigUtils {

    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    val SUPPORTED_SCHEMES = setOf(
        "vmess", "vless", "trojan", "ss", "hysteria2", "hy2", "tuic", "socks", "ssr",
    )

    fun looksLikeLink(text: String): Boolean {
        val trimmed = text.trim()
        if (!schemeRegex.containsMatchIn(trimmed)) return false
        return schemeOf(trimmed) in SUPPORTED_SCHEMES
    }

    fun schemeOf(link: String): String = link.substringBefore("://").lowercase()

    fun decodeComponent(value: String): String {
        // Keep literal '+' intact; fragments and names commonly contain it.
        val safe = value.replace("+", "%2B")
        return runCatching { URLDecoder.decode(safe, "UTF-8") }.getOrDefault(value)
    }

    fun decodeBase64(input: String): String? {
        val cleaned = input.trim()
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace("-", "+")
            .replace("_", "/")
        if (cleaned.isEmpty()) return null
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        val decoders = listOf(Base64.getDecoder(), Base64.getMimeDecoder())
        for (decoder in decoders) {
            val decoded = runCatching { String(decoder.decode(padded), Charsets.UTF_8) }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) return decoded
        }
        return null
    }

    /** Decodes a whole base64 subscription body when it contains proxy links. */
    fun decodeSubscriptionBody(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.contains("://")) return null
        if (!trimmed.matches(Regex("^[A-Za-z0-9+/=_\\-\\s]+$"))) return null
        val decoded = decodeBase64(trimmed) ?: return null
        return if (decoded.contains("://")) decoded else null
    }

    fun alpnList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun jsonStringList(values: List<String>) = buildJsonArray { values.forEach { add(it) } }
}

internal fun JsonObject.string(key: String): String? =
    runCatching { (get(key) as? JsonPrimitive)?.content }.getOrNull()?.takeIf { it.isNotBlank() }

internal fun JsonObject.int(key: String): Int? =
    runCatching { (get(key) as? JsonPrimitive)?.content?.trim()?.toIntOrNull() }.getOrNull()

internal fun JsonObject.bool(key: String): Boolean? = runCatching {
    val content = (get(key) as? JsonPrimitive)?.content ?: return@runCatching null
    when (content.trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }
}.getOrNull()

internal fun String?.asBool(): Boolean {
    val value = this?.trim()?.lowercase() ?: return false
    return value == "1" || value == "true" || value == "yes"
}

/** Ensures every node has a unique tag, rewriting it inside the outbound JSON too. */
internal fun ensureUniqueTags(nodes: List<com.radx.app.data.ProxyNode>): List<com.radx.app.data.ProxyNode> {
    val used = mutableSetOf<String>()
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
            val rebuilt = JsonObject(
                node.outbound.toMutableMap().apply {
                    put("tag", JsonPrimitive(tag))
                },
            )
            node.copy(tag = tag, outbound = rebuilt)
        }
    }
}

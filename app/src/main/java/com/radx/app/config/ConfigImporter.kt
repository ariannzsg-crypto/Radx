package com.radx.app.config

import com.radx.app.data.ProfileKind
import com.radx.app.data.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Single entry point for every import path: pasted links, Clash YAML, sing-box
 * JSON, local files and subscription URLs.
 */
class ConfigImporter(
    private val client: OkHttpClient = defaultClient(),
) {

    data class Imported(
        val kind: ProfileKind,
        val nodes: List<ProxyNode>,
        val warnings: List<String>,
        val raw: String,
        val subscriptionUrl: String?,
        val suggestedName: String,
    )

    suspend fun importText(text: String, name: String?): Imported = withContext(Dispatchers.IO) {
        parsePayload(text, name, null, depth = 0)
    }

    suspend fun importSubscription(url: String, name: String?): Imported = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw IllegalArgumentException("Subscription URL must start with http:// or https://")
        }
        val body = download(normalized)
        parsePayload(body, name, normalized, depth = 1)
    }

    suspend fun refresh(url: String): Imported = importSubscription(url, null)

    // ------------------------------------------------------------- internals

    private fun parsePayload(
        payload: String,
        name: String?,
        subscriptionUrl: String?,
        depth: Int,
    ): Imported {
        val text = payload.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) throw IllegalArgumentException("Nothing to import")

        // A pasted subscription URL in the text field.
        if (subscriptionUrl == null && depth == 0 && text.matches(Regex("^https?://\\S+$"))) {
            val body = download(text)
            return parsePayload(body, name, text, depth + 1)
        }

        if (ClashConfigParser.isClash(text)) {
            val result = ClashConfigParser.parse(text)
            if (result.nodes.isEmpty()) {
                throw IllegalArgumentException("No supported proxies found in this Clash config")
            }
            return Imported(
                kind = ProfileKind.CLASH,
                nodes = result.nodes,
                warnings = result.warnings,
                raw = text,
                subscriptionUrl = subscriptionUrl,
                suggestedName = name ?: "Clash config",
            )
        }

        if (SingBoxConfigParser.looksLikeSingBox(text)) {
            val result = SingBoxConfigParser.parse(text)
            if (result.nodes.isEmpty()) {
                throw IllegalArgumentException("No supported outbounds found in this sing-box config")
            }
            return Imported(
                kind = ProfileKind.SING_BOX,
                nodes = result.nodes,
                warnings = result.warnings,
                raw = text,
                subscriptionUrl = subscriptionUrl,
                suggestedName = name ?: "sing-box config",
            )
        }

        val decoded = ConfigUtils.decodeSubscriptionBody(text) ?: text
        val nodes = collectLinks(decoded)
        if (nodes.isEmpty()) {
            throw IllegalArgumentException("No supported proxy link found")
        }
        return Imported(
            kind = ProfileKind.LINKS,
            nodes = ensureUniqueTags(nodes),
            warnings = emptyList(),
            raw = text,
            subscriptionUrl = subscriptionUrl,
            suggestedName = name ?: defaultName(nodes.first(), subscriptionUrl),
        )
    }

    private fun collectLinks(text: String): List<ProxyNode> {
        val nodes = mutableListOf<ProxyNode>()
        val candidates = mutableListOf<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) continue
            if (ConfigUtils.looksLikeLink(trimmed)) {
                candidates += trimmed
            } else if (trimmed.contains("://")) {
                Regex("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s]+")
                    .findAll(trimmed)
                    .map { it.value }
                    .filter { ConfigUtils.looksLikeLink(it) }
                    .forEach { candidates += it }
            }
        }
        for (candidate in candidates) {
            val node = runCatching { ProxyLinkParser.parse(candidate) }.getOrNull()
            if (node != null) nodes += node
        }
        return nodes
    }

    private fun defaultName(node: ProxyNode, subscriptionUrl: String?): String {
        if (subscriptionUrl != null) {
            val host = runCatching { java.net.URI(subscriptionUrl).host }.getOrNull()
            if (!host.isNullOrBlank()) return host
        }
        return node.name
    }

    private fun download(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Radx/1.0 (Android)")
            .header("Accept", "*/*")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Subscription request failed: HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty subscription response")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}

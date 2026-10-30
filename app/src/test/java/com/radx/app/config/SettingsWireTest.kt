package com.radx.app.config

import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.ProfileKind
import com.radx.app.data.ProxyNode
import com.radx.app.data.TunnelMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every setting that reaches a config has to land in it: a switch that only repaints the
 * settings screen would be a lie, and a field the core rejects would take the tunnel down.
 */
class SettingsWireTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun nodes(): List<ProxyNode> = runBlocking {
        ConfigImporter().importText(
            """
            vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls&type=ws&path=%2Fws&host=example.com#ws
            trojan://secret@example.org:443?security=tls&type=xhttp&path=%2Fx&host=example.org#xhttp
            """.trimIndent(),
            "settings wire test",
        ).nodes
    }

    private fun profile(): Profile = Profile(
        id = "wire",
        name = "wire",
        kind = ProfileKind.LINKS,
        nodes = nodes(),
    )

    @Test
    fun `mux and fragment reach the xray outbound`() {
        val settings = AppSettings(muxEnabled = true, muxConcurrency = 16, fragmentEnabled = true)
        val config = json.parseToJsonElement(
            XrayConfigBuilder.build(nodes(), emptySet(), settings)!!,
        ).jsonObject

        val outbound = config["outbounds"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["tag"]!!.jsonPrimitive.content == XrayConfigBuilder.outboundTag(1) }

        val mux = outbound["mux"]!!.jsonObject
        assertTrue(mux["enabled"]!!.jsonPrimitive.boolean)
        assertEquals(16, mux["concurrency"]!!.jsonPrimitive.int)

        val fragment = outbound["streamSettings"]!!.jsonObject["fragment"]!!.jsonObject
        assertEquals("tlshello", fragment["packets"]!!.jsonPrimitive.content)
        assertEquals(settings.fragmentSize, fragment["length"]!!.jsonPrimitive.content)
    }

    @Test
    fun `socks bridge takes the proxy credentials, udp flag and log level`() {
        val settings = AppSettings(
            proxyUser = "alice",
            proxyPass = "s3cret",
            socksUdp = false,
            allowLan = true,
            logLevel = com.radx.app.data.LogLevel.DEBUG,
        )
        val config = json.parseToJsonElement(
            XrayConfigBuilder.build(nodes(), emptySet(), settings)!!,
        ).jsonObject

        assertEquals("debug", config["log"]!!.jsonObject["loglevel"]!!.jsonPrimitive.content)
        val inbound = config["inbounds"]!!.jsonArray.first().jsonObject
        assertEquals("0.0.0.0", inbound["listen"]!!.jsonPrimitive.content)
        val socks = inbound["settings"]!!.jsonObject
        assertEquals("password", socks["auth"]!!.jsonPrimitive.content)
        assertEquals("alice", socks["accounts"]!!.jsonArray.first().jsonObject["user"]!!.jsonPrimitive.content)
        assertFalse(socks["udp"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `sniffing, routeOnly and host mappings shape the route`() {
        val settings = AppSettings(
            sniffing = true,
            routeOnly = true,
            dnsHosts = "ads.example.com:10.0.0.9",
        )
        val config = json.parseToJsonElement(SingBoxConfigBuilder.build(profile(), settings)).jsonObject
        val rules = config["route"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }

        // routeOnly lives on the inbound: the rules still see the sniffed domain, but the
        // dial keeps the address the app asked for.
        assertNotNull(rules.firstOrNull { it["action"]?.jsonPrimitive?.content == "sniff" })
        val tun = config["inbounds"]!!.jsonArray.map { it.jsonObject }
            .first { it["type"]!!.jsonPrimitive.content == "tun" }
        assertFalse(tun["sniff_override_destination"]!!.jsonPrimitive.boolean)

        val pinned = rules.first { it["override_address"] != null }
        assertEquals("ads.example.com", pinned["domain"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals("10.0.0.9", pinned["override_address"]!!.jsonPrimitive.content)
    }

    @Test
    fun `vpn mode keeps the local proxy on the port the service resolved`() {
        val settings = AppSettings(tunnelMode = TunnelMode.VPN, localProxy = true, preferIpv6 = true)
        val config = json.parseToJsonElement(
            SingBoxConfigBuilder.build(profile(), settings, proxyPort = 18999),
        ).jsonObject

        val inbounds = config["inbounds"]!!.jsonArray.map { it.jsonObject }
        assertEquals(
            setOf("tun", "mixed"),
            inbounds.map { it["type"]!!.jsonPrimitive.content }.toSet(),
        )
        val mixed = inbounds.first { it["type"]!!.jsonPrimitive.content == "mixed" }
        assertEquals(18999, mixed["listen_port"]!!.jsonPrimitive.int)
        assertEquals("prefer_ipv6", config["dns"]!!.jsonObject["strategy"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tunnel address and mux reach the sing-box config`() {
        val settings = AppSettings(tunAddress = "10.20.30.1", muxEnabled = true, muxConcurrency = 4)
        val config = json.parseToJsonElement(SingBoxConfigBuilder.build(profile(), settings)).jsonObject

        val tun = config["inbounds"]!!.jsonArray.map { it.jsonObject }
            .first { it["type"]!!.jsonPrimitive.content == "tun" }
        assertEquals("10.20.30.1/30", tun["address"]!!.jsonArray.first().jsonPrimitive.content)

        val outbounds = config["outbounds"]!!.jsonArray.map { it.jsonObject }
        val node = outbounds.first { it["tag"]!!.jsonPrimitive.content == "ws" }
        assertEquals(4, node["multiplex"]!!.jsonObject["max_streams"]!!.jsonPrimitive.int)
    }
}

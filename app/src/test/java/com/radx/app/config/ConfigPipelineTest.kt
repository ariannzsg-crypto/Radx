package com.radx.app.config

import com.radx.app.data.AppSettings
import com.radx.app.data.Profile
import com.radx.app.data.ProfileKind
import com.radx.app.data.TunnelMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ConfigPipelineTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun b64(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())

    private val clashYaml = """
        proxies:
          - name: "HY2"
            type: hysteria2
            server: hy.example.com
            port: 443
            password: secret
            sni: hy.example.com
            skip-cert-verify: true
          - name: "VM"
            type: vmess
            server: 1.2.3.4
            port: 8443
            uuid: u-u-i-d
            alterId: 0
            cipher: auto
            tls: true
            network: ws
            ws-opts:
              path: /tunnel
              headers:
                Host: cdn.example.com
        rules:
          - MATCH,PROXY
    """.trimIndent()

    // ------------------------------------------------------------- links

    @Test
    fun `vless reality link becomes a sing-box outbound`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?security=reality&sni=cdn.example.com&pbk=PUBKEY&sid=ab12&fp=chrome" +
            "&type=grpc&serviceName=svc&flow=xtls-rprx-vision#My%20Node"

        val parsed = ProxyLinkParser.parse(link)
        assertNotNull(parsed)
        val node = requireNotNull(parsed) { "vless link did not parse" }
        assertEquals("My Node", node.name)
        assertEquals("vless", node.type)
        assertEquals("example.com", node.server)
        assertEquals(443, node.port)

        val outbound = node.outbound
        assertEquals("vless", outbound.string("type"))
        assertEquals("xtls-rprx-vision", outbound.string("flow"))
        assertEquals("svc", outbound["transport"]!!.jsonObject.string("service_name"))

        val tls = outbound["tls"]!!.jsonObject
        assertEquals(true, tls.bool("enabled"))
        assertEquals("cdn.example.com", tls.string("server_name"))
        assertEquals("chrome", tls["utls"]!!.jsonObject.string("fingerprint"))
        assertEquals("PUBKEY", tls["reality"]!!.jsonObject.string("public_key"))
        assertEquals("ab12", tls["reality"]!!.jsonObject.string("short_id"))
    }

    @Test
    fun `vmess json link keeps transport and tls`() {
        val payload = """
            {"v":"2","ps":"VM Node","add":"1.2.3.4","port":"8443","id":"uuid-1",
             "aid":"0","scy":"auto","net":"ws","host":"cdn.example.com",
             "path":"/ws","tls":"tls","sni":"cdn.example.com"}
        """.trimIndent()

        val node = ProxyLinkParser.parse("vmess://" + b64(payload))!!
        assertEquals("VM Node", node.name)
        assertEquals(8443, node.port)

        val transport = node.outbound["transport"]!!.jsonObject
        assertEquals("ws", transport.string("type"))
        assertEquals("/ws", transport.string("path"))
        assertEquals("cdn.example.com", transport["headers"]!!.jsonObject.string("Host"))
        assertEquals(true, node.outbound["tls"]!!.jsonObject.bool("enabled"))
    }

    @Test
    fun `shadowsocks base64 body is decoded`() {
        val link = "ss://" + b64("aes-256-gcm:pass123@5.6.7.8:8388") + "#SS%20Node"

        val node = ProxyLinkParser.parse(link)!!
        assertEquals("SS Node", node.name)
        assertEquals("aes-256-gcm", node.outbound.string("method"))
        assertEquals("pass123", node.outbound.string("password"))
        assertEquals(8388, node.port)
    }

    @Test
    fun `unsupported or malformed links are rejected`() {
        assertNull(ProxyLinkParser.parse("https://example.com/sub"))
        assertNull(ProxyLinkParser.parse("vmess://not-base64-json"))
        assertNull(ProxyLinkParser.parse(""))
    }

    @Test
    fun `xhttp links are refused instead of becoming dead nodes`() {
        val xhttp = "vless://uuid@cdn.example.com:443?security=tls&sni=cdn.example.com" +
            "&type=xhttp&path=%2Fapi&mode=auto#XHTTP"

        assertNull(ProxyLinkParser.parse(xhttp))
        assertEquals("xhttp", ProxyLinkParser.unsupportedTransport(xhttp))
        assertNull(ProxyLinkParser.unsupportedTransport("vless://uuid@example.com:443?security=tls#Plain"))
    }

    @Test
    fun `importer keeps the reality nodes and warns about xhttp ones`() = runBlocking {
        val links = listOf(
            "vless://uuid@cdn.example.com:443?security=tls&sni=cdn.example.com&type=xhttp&path=%2Fapi#X1",
            "vless://uuid@45.82.13.140:443?security=reality&sni=play.google.com&pbk=PUBKEY&sid=ab12" +
                "&fp=chrome&type=tcp&flow=xtls-rprx-vision#REAL",
        ).joinToString("\n")

        val imported = ConfigImporter().importText(links, null)
        assertEquals(1, imported.nodes.size)
        assertEquals("REAL", imported.nodes[0].name)
        assertEquals(1, imported.warnings.size)
        assertTrue(imported.warnings[0].contains("xhttp"))
    }

    @Test
    fun `a subscription of only unsupported transports explains itself`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                ConfigImporter().importText("vless://uuid@cdn.example.com:443?type=xhttp#X1", null)
            }
        }
        assertTrue(error.message!!.contains("xhttp"))
    }

    // ------------------------------------------------------------- clash

    @Test
    fun `clash yaml proxies are converted`() {
        val result = ClashConfigParser.parse(clashYaml)
        assertEquals(2, result.nodes.size)
        assertEquals(1, result.warnings.size)

        val hysteria = result.nodes[0]
        assertEquals("hysteria2", hysteria.outbound.string("type"))
        assertEquals("hy.example.com", hysteria.server)
        assertEquals("secret", hysteria.outbound.string("password"))
        assertEquals(true, hysteria.outbound["tls"]!!.jsonObject.bool("insecure"))

        val vmess = result.nodes[1]
        assertEquals("vmess", vmess.outbound.string("type"))
        assertEquals(8443, vmess.port)
        assertEquals(true, vmess.outbound["tls"]!!.jsonObject.bool("enabled"))
        assertEquals("ws", vmess.outbound["transport"]!!.jsonObject.string("type"))
        assertEquals("/tunnel", vmess.outbound["transport"]!!.jsonObject.string("path"))
    }

    @Test
    fun `sing-box json outbounds are extracted`() {
        val config = """
            {"outbounds":[
              {"type":"vless","tag":"X","server":"example.com","server_port":443,"uuid":"u"},
              {"type":"direct","tag":"direct"}
            ],
            "route":{"final":"direct"}}
        """.trimIndent()

        val result = SingBoxConfigParser.parse(config)
        assertEquals(1, result.nodes.size)
        assertEquals("X", result.nodes[0].tag)
        assertEquals("example.com", result.nodes[0].server)
        assertEquals(1, result.warnings.size)
    }

    // ------------------------------------------------------------ builder

    @Test
    fun `builder creates a tun config with selector default`() {
        val nodes = ClashConfigParser.parse(clashYaml).nodes
        val profile = Profile(
            id = "p1",
            name = "Clash",
            kind = ProfileKind.CLASH,
            nodes = nodes,
            selectedTag = "VM",
        )

        val config = json.parseToJsonElement(SingBoxConfigBuilder.build(profile, AppSettings())).jsonObject

        val inbound = config["inbounds"]!!.jsonArray[0].jsonObject
        assertEquals("tun", inbound.string("type"))
        assertEquals(true, inbound.bool("auto_route"))

        val outbounds = config["outbounds"]!!.jsonArray
        val selector = outbounds[0].jsonObject
        assertEquals("selector", selector.string("type"))
        assertEquals("VM", selector.string("default"))
        assertEquals(2, selector["outbounds"]!!.jsonArray.size)
        assertEquals("direct", outbounds.last().jsonObject.string("type"))

        val route = config["route"]!!.jsonObject
        assertEquals("proxy", route.string("final"))
        assertEquals(true, route.bool("auto_detect_interface"))
    }

    @Test
    fun `builder switches to a mixed inbound in proxy mode`() {
        val nodes = ClashConfigParser.parse(clashYaml).nodes
        val profile = Profile(id = "p1", name = "Clash", kind = ProfileKind.CLASH, nodes = nodes)

        val settings = AppSettings(tunnelMode = TunnelMode.PROXY, allowLan = true, ipv6 = false)
        val config = json.parseToJsonElement(SingBoxConfigBuilder.build(profile, settings)).jsonObject

        val inbound = config["inbounds"]!!.jsonArray[0].jsonObject
        assertEquals("mixed", inbound.string("type"))
        assertEquals("0.0.0.0", inbound.string("listen"))
        assertEquals(7890, inbound["listen_port"]!!.jsonPrimitive.int)

        val dns = config["dns"]!!.jsonObject
        assertEquals("ipv4_only", dns.string("strategy"))
    }

    @Test
    fun `builder rejects an empty profile`() {
        val profile = Profile(id = "empty", name = "Empty", kind = ProfileKind.LINKS)
        assertThrows(IllegalArgumentException::class.java) {
            SingBoxConfigBuilder.build(profile, AppSettings())
        }
    }

    @Test
    fun `duplicate node tags are made unique inside their outbound`() {
        val link = "vless://uuid@example.com:443?security=tls#Same"
        val nodes = listOf(ProxyLinkParser.parse(link)!!, ProxyLinkParser.parse(link)!!)

        val unique = ensureUniqueTags(nodes)
        assertEquals(listOf("Same", "Same 2"), unique.map { it.tag })
        assertEquals("Same 2", unique[1].outbound.string("tag"))
    }

    // ----------------------------------------------------------- importer

    @Test
    fun `importer accepts multiple links in one paste`() = runBlocking {
        val links = listOf(
            "vless://uuid@a.example.com:443?security=tls#A",
            "ss://" + b64("aes-128-gcm:pw@b.example.com:8388") + "#B",
        ).joinToString("\n")

        val imported = ConfigImporter().importText(links, "Mixed")
        assertEquals(ProfileKind.LINKS, imported.kind)
        assertEquals(2, imported.nodes.size)
        assertEquals("Mixed", imported.suggestedName)
        assertEquals(links, imported.raw)
    }

    @Test
    fun `importer decodes a base64 subscription body`() = runBlocking {
        val body = b64(
            listOf(
                "trojan://pw@trojan.example.com:443?sni=trojan.example.com#T1",
                "vless://uuid@v.example.com:8443?security=tls#V1",
            ).joinToString("\n"),
        )

        val imported = ConfigImporter().importText(body, null)
        assertEquals(2, imported.nodes.size)
        assertEquals("T1", imported.nodes[0].name)
        assertEquals(ProfileKind.LINKS, imported.kind)
        assertNull(imported.subscriptionUrl)
    }

    @Test
    fun `importer keeps clash payload intact for sharing`() = runBlocking {
        val imported = ConfigImporter().importText(clashYaml, null)
        assertEquals(ProfileKind.CLASH, imported.kind)
        assertEquals(2, imported.nodes.size)
        assertEquals(clashYaml, imported.raw)
        assertTrue(imported.suggestedName.isNotBlank())
    }

    @Test
    fun `importer rejects payloads without proxies`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ConfigImporter().importText("hello world", null) }
        }
    }

}

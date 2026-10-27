package com.radx.app.config

import com.radx.app.data.AppSettings
import com.radx.app.data.LogLevel
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

    private val xhttpLink = "vless://11111111-2222-3333-4444-555555555555@cdn.example.com:443" +
        "?security=tls&sni=front.example.com&fp=chrome&alpn=h2%2Chttp%2F1.1" +
        "&type=xhttp&path=%2Fapi&host=cdn.example.com&mode=auto" +
        "&extra=%7B%22xPaddingBytes%22%3A%22100-1000%22%7D#XHTTP"

    @Test
    fun `xhttp links run on the xray core`() {
        val node = ProxyLinkParser.parse(xhttpLink)!!

        assertTrue(node.viaXray)
        // The sing-box side is only a placeholder until the config is built.
        assertEquals("socks", node.outbound.string("type"))
        assertEquals(0, node.outbound.int("server_port"))

        val xray = node.xray!!
        assertEquals("vless", xray.string("protocol"))
        val user = xray["settings"]!!.jsonObject["vnext"]!!.jsonArray[0].jsonObject["users"]!!.jsonArray[0].jsonObject
        assertEquals("11111111-2222-3333-4444-555555555555", user.string("id"))
        assertEquals("none", user.string("encryption"))

        val stream = xray["streamSettings"]!!.jsonObject
        assertEquals("xhttp", stream.string("network"))
        assertEquals("tls", stream.string("security"))
        val tls = stream["tlsSettings"]!!.jsonObject
        assertEquals("front.example.com", tls.string("serverName"))
        assertEquals("chrome", tls.string("fingerprint"))
        assertEquals(2, tls["alpn"]!!.jsonArray.size)

        val xhttp = stream["xhttpSettings"]!!.jsonObject
        assertEquals("/api", xhttp.string("path"))
        assertEquals("cdn.example.com", xhttp.string("host"))
        assertEquals("auto", xhttp.string("mode"))
        assertEquals("100-1000", xhttp["extra"]!!.jsonObject.string("xPaddingBytes"))
    }

    @Test
    fun `xray nodes are bridged into sing-box through their local socks port`() {
        val reality = ProxyLinkParser.parse(
            "vless://uuid@45.82.13.140:443?security=reality&sni=play.google.com&pbk=PUBKEY&sid=ab12" +
                "&fp=chrome&type=tcp&flow=xtls-rprx-vision#REAL",
        )!!
        val profile = Profile(
            id = "p",
            name = "Mixed",
            kind = ProfileKind.LINKS,
            nodes = listOf(ProxyLinkParser.parse(xhttpLink)!!, reality),
        )

        val config = json.parseToJsonElement(SingBoxConfigBuilder.build(profile, AppSettings())).jsonObject
        val outbounds = config["outbounds"]!!.jsonArray.map { it.jsonObject }

        val bridged = outbounds.first { it.string("tag") == "XHTTP" }
        assertEquals("socks", bridged.string("type"))
        assertEquals("127.0.0.1", bridged.string("server"))
        assertEquals(XrayBridge.port(0), bridged.int("server_port"))

        // The reality node keeps its own sing-box outbound.
        val direct = outbounds.first { it.string("tag") == "REAL" }
        assertEquals("vless", direct.string("type"))
        assertEquals("45.82.13.140", direct.string("server"))

        // No placeholder port may survive into a config the core has to parse.
        assertTrue(outbounds.none { it.string("type") == "socks" && it.int("server_port") == 0 })
    }

    @Test
    fun `xray config gives every bridged node its own socks inbound`() {
        val nodes = listOf(ProxyLinkParser.parse(xhttpLink)!!, ProxyLinkParser.parse(xhttpLink)!!)
        val config = json.parseToJsonElement(XrayConfigBuilder.build(nodes)!!).jsonObject

        val inbounds = config["inbounds"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, inbounds.size)
        assertEquals(XrayBridge.port(0), inbounds[0].int("port"))
        assertEquals(XrayBridge.port(1), inbounds[1].int("port"))
        assertEquals("socks", inbounds[0].string("protocol"))
        assertEquals("127.0.0.1", inbounds[0].string("listen"))

        val rules = config["routing"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, rules.size)
        assertEquals(
            XrayConfigBuilder.inboundTag(1),
            rules[1]["inboundTag"]!!.jsonArray[0].jsonPrimitive.content,
        )
        assertEquals(XrayConfigBuilder.outboundTag(1), rules[1].string("outboundTag"))

        val outbounds = config["outbounds"]!!.jsonArray.map { it.jsonObject }
        assertEquals(XrayConfigBuilder.outboundTag(0), outbounds[0].string("tag"))
        assertEquals("freedom", outbounds.last().string("protocol"))
    }

    @Test
    fun `xray tls settings never carry the removed allowInsecure flag`() {
        // Current Xray refuses to load a config that still has allowInsecure, which
        // would take every Xray-backed node down at once.
        val insecure = ProxyLinkParser.parse(
            "vless://uuid@front.example.com:443?security=tls&sni=front.example.com" +
                "&allowInsecure=1&type=xhttp&path=%2F#Insecure",
        )!!
        val tls = insecure.xray!!["streamSettings"]!!.jsonObject["tlsSettings"]!!.jsonObject
        assertNull(tls["allowInsecure"])
        assertEquals("front.example.com", tls.string("serverName"))

        val pinned = ProxyLinkParser.parse(
            "vless://uuid@front.example.com:443?security=tls&sni=a.example.com&type=xhttp" +
                "&pcs=ABCDEF&vcn=b.example.com#Pinned",
        )!!
        val pinnedTls = pinned.xray!!["streamSettings"]!!.jsonObject["tlsSettings"]!!.jsonObject
        assertEquals("ABCDEF", pinnedTls.string("pinnedPeerCertSha256"))
        assertEquals("b.example.com", pinnedTls.string("verifyPeerCertByName"))
    }

    @Test
    fun `xray config is only built when a node needs it`() {
        val node = ProxyLinkParser.parse("vless://uuid@example.com:443?security=tls#Plain")!!
        assertNull(XrayConfigBuilder.build(listOf(node)))
    }

    @Test
    fun `without the xray core only the sing-box nodes are used`() {
        val reality = ProxyLinkParser.parse("vless://uuid@example.com:443?security=tls#Plain")!!
        val profile = Profile(
            id = "p",
            name = "Mixed",
            kind = ProfileKind.LINKS,
            nodes = listOf(ProxyLinkParser.parse(xhttpLink)!!, reality),
        )

        val config = json.parseToJsonElement(
            SingBoxConfigBuilder.build(profile, AppSettings(), xrayAvailable = false),
        ).jsonObject
        val selector = config["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals(1, selector["outbounds"]!!.jsonArray.size)
        assertEquals("Plain", selector.string("default"))

        // A profile that only has Xray nodes cannot run at all.
        val onlyXray = profile.copy(nodes = listOf(ProxyLinkParser.parse(xhttpLink)!!))
        assertThrows(IllegalArgumentException::class.java) {
            SingBoxConfigBuilder.build(onlyXray, AppSettings(), xrayAvailable = false)
        }
    }

    @Test
    fun `importer keeps xhttp nodes next to reality ones`() = runBlocking {
        val links = listOf(
            xhttpLink,
            "vless://uuid@45.82.13.140:443?security=reality&sni=play.google.com&pbk=PUBKEY&sid=ab12" +
                "&fp=chrome&type=tcp&flow=xtls-rprx-vision#REAL",
        ).joinToString("\n")

        val imported = ConfigImporter().importText(links, null)
        assertEquals(2, imported.nodes.size)
        assertEquals(listOf("XHTTP", "REAL"), imported.nodes.map { it.name })
        assertEquals(1, imported.nodes.count { it.viaXray })
        assertTrue(imported.warnings.isEmpty())
    }

    @Test
    fun `a transport neither core can run explains itself`() {
        val link = "vless://uuid@cdn.example.com:443?security=tls&type=meek#Odd"
        assertNull(ProxyLinkParser.parse(link))
        assertEquals("meek", ProxyLinkParser.unsupportedTransport(link))
        assertNull(ProxyLinkParser.unsupportedTransport(xhttpLink))

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ConfigImporter().importText(link, null) }
        }
        assertTrue(error.message!!.contains("meek"))
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
    fun `advanced settings reach the generated config`() {
        val nodes = ClashConfigParser.parse(clashYaml).nodes
        val profile = Profile(id = "p1", name = "Clash", kind = ProfileKind.CLASH, nodes = nodes)

        val config = json.parseToJsonElement(
            SingBoxConfigBuilder.build(
                profile,
                AppSettings(
                    tunnelMode = TunnelMode.PROXY,
                    bypassLan = false,
                    blockQuic = true,
                    logLevel = LogLevel.DEBUG,
                ),
            ),
        ).jsonObject

        assertEquals("debug", config["log"]!!.jsonObject.string("level"))
        val rules = config["route"]!!.jsonObject["rules"]!!.jsonArray.map { it.jsonObject }
        assertTrue(rules.any { it.string("protocol") == "quic" && it.string("action") == "reject" })
        assertTrue(rules.none { it["ip_is_private"] != null })
    }

    @Test
    fun `the tun mtu follows the setting`() {
        val nodes = ClashConfigParser.parse(clashYaml).nodes
        val profile = Profile(id = "p1", name = "Clash", kind = ProfileKind.CLASH, nodes = nodes)

        val inbound = json.parseToJsonElement(
            SingBoxConfigBuilder.build(profile, AppSettings(tunMtu = 1400)),
        ).jsonObject["inbounds"]!!.jsonArray[0].jsonObject

        assertEquals(1400, inbound["mtu"]!!.jsonPrimitive.int)
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

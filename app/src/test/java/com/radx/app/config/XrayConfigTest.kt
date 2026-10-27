package com.radx.app.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** A node from a real subscription: XHTTP on port 80 with no TLS at all. */
    private val unencrypted =
        "vless://9f29da38-aff5-460c-b100-a300b56ef657@www.speedtest.net:80" +
            "?encryption=none&type=xhttp&path=%2Fapi%2Fpattern-to-decode&mode=auto&security=none#NoTLS"

    private val tlsNode =
        "vless://9f29da38-aff5-460c-b100-a300b56ef657@www.speedtest.net:443" +
            "?encryption=none&type=xhttp&path=%2Fapi&mode=auto&security=tls&sni=api.example.com&fp=chrome#TLS"

    private val lanNode =
        "vless://9f29da38-aff5-460c-b100-a300b56ef657@192.168.1.10:80" +
            "?encryption=none&type=xhttp&path=%2F&mode=auto&security=none#LAN"

    /**
     * The core aborts the whole config when one outbound is invalid, so these nodes have
     * to be recognised before the core is started.
     */
    @Test
    fun `unencrypted public node is refused`() {
        val node = ProxyLinkParser.parse(unencrypted)
        assertNotNull(node)
        assertNotNull(XrayConfigBuilder.rejectionReason(node!!))
    }

    @Test
    fun `transport security and private addresses are accepted`() {
        val tls = ProxyLinkParser.parse(tlsNode)
        assertNotNull(tls)
        assertNull(XrayConfigBuilder.rejectionReason(tls!!))

        val lan = ProxyLinkParser.parse(lanNode)
        assertNotNull(lan)
        assertNull(XrayConfigBuilder.rejectionReason(lan!!))
    }

    @Test
    fun `sing-box nodes are never judged by the xray rules`() {
        val clash = ProxyLinkParser.parse("vless://abc@1.2.3.4:443?security=reality&type=tcp&pbk=k&sid=1#REAL")
        assertNotNull(clash)
        assertNull(XrayConfigBuilder.rejectionReason(clash!!))
    }

    @Test
    fun `a skipped node keeps its port reserved for the rest`() {
        val first = ProxyLinkParser.parse(unencrypted)!!
        val second = ProxyLinkParser.parse(tlsNode)!!
        val nodes = listOf(first, second)

        val full = XrayConfigBuilder.build(nodes)
        assertNotNull(full)
        assertEquals(listOf(XrayBridge.port(0), XrayBridge.port(1)), full!!.inboundPorts())

        val without = XrayConfigBuilder.build(nodes, setOf(0))
        assertNotNull(without)
        assertEquals(listOf(XrayBridge.port(1)), without!!.inboundPorts())
        val tags = without.jsonObject()["outbounds"]!!.jsonArray.map { it.jsonObject["tag"]!!.jsonPrimitive.content }
        assertTrue(tags.contains(XrayConfigBuilder.outboundTag(1)))
        assertFalse(tags.contains(XrayConfigBuilder.outboundTag(0)))
    }

    @Test
    fun `a profile with nothing left for the core builds no config`() {
        val node = ProxyLinkParser.parse(unencrypted)!!
        assertNull(XrayConfigBuilder.build(listOf(node), setOf(0)))
    }

    @Test
    fun `the refused node is read back from the core log`() {
        val line = "Failed to start: main: failed to load config files: " +
            "[/data/user/0/com.radx.app/files/xray/config.json] > infra/conf: " +
            "failed to build outbound config with tag radx-xray-out-0 > infra/conf: " +
            "vless without TLS or other encryption is prohibited unless the server address " +
            "is a private IP or domain"
        assertEquals(0, XrayConfigBuilder.rejectedOutboundIndex(line))
        assertEquals(
            12,
            XrayConfigBuilder.rejectedOutboundIndex(
                "infra/conf: failed to build outbound config with tag radx-xray-out-12 > x",
            ),
        )
        assertNull(XrayConfigBuilder.rejectedOutboundIndex("Failed to start: something else"))
    }

    private fun String.jsonObject() = json.parseToJsonElement(this).jsonObject

    private fun String.inboundPorts(): List<Int> = jsonObject()["inbounds"]!!.jsonArray
        .map { it.jsonObject["port"]!!.jsonPrimitive.int }
}

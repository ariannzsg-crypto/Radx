package com.radx.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The probe has to tell three states apart: a node that carries data, a node whose
 * handshake is answered but which never returns a body (the "ping works, no traffic"
 * case), and a node that is simply not there.
 */
class TrafficProbeTest {

    /** A loopback server that either answers with a fixed body or hangs up at once. */
    private class FakeServer(private val bodyBytes: Int, private val answer: Boolean) : Closeable {
        private val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        private val running = AtomicBoolean(true)
        private val worker = thread(isDaemon = true) {
            while (running.get()) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching { handle(socket) }
            }
        }

        val port: Int get() = server.localPort

        private fun handle(socket: Socket) {
            socket.use {
                if (!answer) return
                val reader = it.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val out = it.getOutputStream()
                out.write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Length: $bodyBytes\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(),
                )
                out.write(ByteArray(bodyBytes) { 0x41 })
                out.flush()
            }
        }

        override fun close() {
            running.set(false)
            runCatching { server.close() }
            worker.join(1_000)
        }
    }

    @Test
    fun `a proxied download that returns a body is healthy`() {
        // The URL cannot resolve, so a healthy result can only come through the proxy.
        FakeServer(bodyBytes = 64 * 1024, answer = true).use { proxy ->
            val result = TrafficProbe.measure(
                proxyPort = proxy.port,
                url = "http://radx.invalid/payload",
                timeoutMs = 5_000,
            )
            assertTrue("expected healthy, got $result", result.healthy)
            assertEquals(64L * 1024, result.bytes)
            assertTrue(result.mbps > 0)
        }
    }

    @Test
    fun `a node that only accepts the connection is not healthy`() {
        // This is the reported failure: the handshake succeeds, nothing ever arrives.
        FakeServer(bodyBytes = 0, answer = false).use { pingOnly ->
            val result = TrafficProbe.measure(
                proxyPort = null,
                url = "http://127.0.0.1:${pingOnly.port}/payload",
                timeoutMs = 5_000,
            )
            assertFalse("a handshake alone must not count as healthy", result.healthy)
            assertEquals(0L, result.bytes)
            assertNotNull("the reason has to be reported", result.error)
        }
    }

    @Test
    fun `a body below the healthy threshold does not count`() {
        FakeServer(bodyBytes = 1024, answer = true).use { tiny ->
            val result = TrafficProbe.measure(
                proxyPort = null,
                url = "http://127.0.0.1:${tiny.port}/payload",
                timeoutMs = 5_000,
            )
            assertEquals(1024L, result.bytes)
            assertFalse("1 KB proves nothing about a node", result.healthy)
        }
    }

    @Test
    fun `a closed port is not healthy`() {
        val port = ServerSocket(0).use { it.localPort }
        val result = TrafficProbe.measure(
            proxyPort = null,
            url = "http://127.0.0.1:$port/payload",
            timeoutMs = 3_000,
        )
        assertFalse(result.healthy)
        assertNotNull(result.error)
    }

    @Test
    fun `throughput is computed over the transfer window only`() {
        // 800 kB in 1 s is 6.4 Mbit/s, whatever the connection setup cost was.
        val result = TrafficProbe.Result(bytes = 800_000, millis = 4_000, transferMillis = 1_000)
        assertEquals(6.4, result.mbps, 0.001)
        assertTrue(result.healthy)
    }

    @Test
    fun `a failed result has no throughput`() {
        val result = TrafficProbe.Result(bytes = 0, millis = 900, transferMillis = 0, error = "reset")
        assertEquals(0.0, result.mbps, 0.0)
        assertFalse(result.healthy)
    }
}

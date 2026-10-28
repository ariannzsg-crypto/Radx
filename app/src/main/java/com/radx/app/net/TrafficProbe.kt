package com.radx.app.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Measures whether a node carries traffic, not just whether it answers.
 *
 * A filtered link can complete the TCP handshake locally, and so can another VPN or
 * adapter on the same machine, which is why a latency probe can report an excellent
 * number for a node that moves no data at all. A real transfer is the only evidence,
 * and this is the single implementation both clients share: the desktop passes its
 * local proxy port, while Android passes its proxy port in proxy mode and null in VPN
 * mode, where the app's own sockets already leave through the tunnel.
 */
object TrafficProbe {

    private const val DESKTOP_BYTES = 4_000_000
    private const val MOBILE_BYTES = 1_000_000

    /** Below this a "successful" request proves nothing about throughput. */
    const val MIN_HEALTHY_BYTES = 32 * 1024

    /** A fixed payload keeps results comparable between runs and devices. */
    fun url(bytes: Int): String = "https://speed.cloudflare.com/__down?bytes=$bytes"

    val desktopUrl: String get() = url(DESKTOP_BYTES)

    val mobileUrl: String get() = url(MOBILE_BYTES)

    data class Result(
        val bytes: Long,
        val millis: Long,
        val transferMillis: Long,
        val error: String? = null,
    ) {
        /** Throughput over the transfer itself; setup time is reported separately. */
        val mbps: Double
            get() = if (transferMillis > 0 && bytes > 0) bytes * 8.0 / transferMillis / 1000.0 else 0.0

        /** True only when a real payload arrived, not merely when the handshake did. */
        val healthy: Boolean get() = error == null && bytes >= MIN_HEALTHY_BYTES
    }

    /**
     * Downloads [url] once, or [attempts] times when a transient failure should not
     * mark a working node as dead. A failed attempt that transferred nothing is not
     * retried by callers that care about latency, so [attempts] stays explicit.
     */
    fun measure(
        proxyPort: Int? = null,
        url: String = desktopUrl,
        timeoutMs: Int = 30_000,
        attempts: Int = 1,
    ): Result {
        var result = Result(0, 0, 0, "not attempted")
        repeat(attempts.coerceAtLeast(1)) {
            result = download(proxyPort, url, timeoutMs)
            if (result.healthy) return result
        }
        return result
    }

    private fun download(proxyPort: Int?, url: String, timeoutMs: Int): Result {
        val client = OkHttpClient.Builder()
            .apply {
                proxyPort?.let { port ->
                    proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
                }
            }
            .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder().url(url).header("Cache-Control", "no-store").build()
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000
        return runCatching {
            client.newCall(request).execute().use { response ->
                val firstByte = if (response.receivedResponseAtMillis > 0) {
                    response.receivedResponseAtMillis
                } else {
                    System.currentTimeMillis()
                }
                val body = response.body?.bytes() ?: ByteArray(0)
                val transfer = (System.currentTimeMillis() - firstByte).coerceAtLeast(1)
                val failure = if (response.isSuccessful) null else "HTTP ${response.code}"
                Result(body.size.toLong(), elapsed(), transfer, failure)
            }
        }.getOrElse { error ->
            Result(0, elapsed(), 0, error.message ?: error.javaClass.simpleName)
        }
    }
}

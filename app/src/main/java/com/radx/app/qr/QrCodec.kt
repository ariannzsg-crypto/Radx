package com.radx.app.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Turns a config link or subscription URL into a QR matrix.
 *
 * The encoder lives outside the UI so the payload rules (trimming, size limit, UTF-8)
 * are the same everywhere a code is shown, and can be checked without a camera: the
 * test decodes the matrix back and compares it with the original text.
 */
object QrCodec {

    /**
     * A QR code above this size stops being reliable to scan from a phone screen, and a
     * whole multi-line subscription would blow past it; callers show the copy/share
     * actions instead.
     */
    const val MAX_CHARS = 1200

    /** Encodes [text], or returns null when it is empty or too large to scan. */
    fun encode(text: String, size: Int = 720): BitMatrix? {
        val payload = text.trim()
        if (payload.isEmpty() || payload.length > MAX_CHARS) return null
        return runCatching {
            QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                    EncodeHintType.MARGIN to 1,
                ),
            )
        }.getOrNull()
    }

    /** The shareable text of a profile, preferring the short subscription URL. */
    fun payload(subscriptionUrl: String?, raw: String): String =
        subscriptionUrl?.takeIf { it.isNotBlank() } ?: raw.trim()
}

package com.radx.app.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The matrix is only useful if a scanner can read it back, so every test decodes the
 * generated code and compares the text, including the unicode names real links carry.
 */
class QrCodecTest {

    private class MatrixLuminance(private val matrix: BitMatrix) : LuminanceSource(matrix.width, matrix.height) {

        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = if (row == null) ByteArray(width) else row
            for (x in 0 until width) out[x] = if (matrix.get(x, y)) 0 else -1
            return out
        }

        override fun getMatrix(): ByteArray = ByteArray(width * height) { index ->
            if (matrix.get(index % width, index / width)) 0 else -1
        }
    }

    private fun decode(matrix: BitMatrix): String =
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(MatrixLuminance(matrix)))).text

    @Test
    fun `a vless link survives the round trip`() {
        val link = "vless://9f29da38-aff5-460c-b100-a300b56ef657@45.82.13.140:443" +
            "?type=tcp&security=reality&sni=play.google.com&fp=chrome" +
            "&pbk=-3FYNptvHNKGKULt4jOkSqSfBbpl9ef_ijzo_8I7OXk&sid=8f2798f9&flow=xtls-rprx-vision#REAL"
        val matrix = QrCodec.encode(link)
        assertNotNull(matrix)
        assertEquals(link, decode(matrix!!))
    }

    @Test
    fun `a persian node name survives the round trip`() {
        val link = "vless://uuid@example.com:443?type=ws&security=tls#سرور آلمان ۱"
        val matrix = QrCodec.encode(link)
        assertNotNull(matrix)
        assertEquals(link, decode(matrix!!))
    }

    @Test
    fun `text is trimmed before encoding`() {
        val matrix = QrCodec.encode("  https://example.com/sub  ")
        assertNotNull(matrix)
        assertEquals("https://example.com/sub", decode(matrix!!))
    }

    @Test
    fun `a payload above the limit is refused`() {
        assertNull(QrCodec.encode("x".repeat(QrCodec.MAX_CHARS + 1)))
    }

    @Test
    fun `blank text is refused`() {
        assertNull(QrCodec.encode("   "))
    }

    @Test
    fun `a subscription url wins over the raw payload`() {
        assertEquals("https://example.com/sub", QrCodec.payload("https://example.com/sub", "line1\nline2"))
        assertEquals("line1\nline2", QrCodec.payload(null, "line1\nline2"))
        assertEquals("line1", QrCodec.payload("  ", "line1"))
    }
}

package com.radx.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.common.BitMatrix
import com.radx.app.R
import com.radx.app.qr.QrCodec

/**
 * Shows a config as a QR code, so it can be moved to another device without a clipboard.
 *
 * The white plate is part of the code: a translucent background would cost the quiet
 * zone a scanner needs. When the payload is too big for a scannable code the dialog
 * says so and points at copy/share instead of drawing an unreadable square.
 */
@Composable
fun QrImageDialog(payload: String, title: String, onDismiss: () -> Unit) {
    val matrix = remember(payload) { QrCodec.encode(payload) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (matrix == null) {
                Text(stringResource(R.string.qr_too_large))
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = remember(matrix) { matrix.toImageBitmap() },
                        contentDescription = stringResource(R.string.qr_title),
                        modifier = Modifier
                            .size(260.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height) { index ->
        if (get(index % width, index / width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}

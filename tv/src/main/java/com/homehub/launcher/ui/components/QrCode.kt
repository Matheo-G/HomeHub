package com.homehub.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR code vectoriel : modules sombres sur carte blanche (contraste maximal
 * pour les appareils photo), net à toutes les tailles.
 * La matrice est calculée une seule fois par contenu.
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { encode(content) }
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = minOf(size.width / matrix.width, size.height / matrix.height)
                val origin = Offset(
                    (size.width - cell * matrix.width) / 2f,
                    (size.height - cell * matrix.height) / 2f,
                )
                // Légère surépaisseur : évite les fines lignes entre modules adjacents.
                val module = Size(cell + 0.5f, cell + 0.5f)
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        if (matrix[x, y]) {
                            drawRect(
                                color = QrDark,
                                topLeft = Offset(origin.x + x * cell, origin.y + y * cell),
                                size = module,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val QrDark = Color(0xFF0B0F1A)

private fun encode(content: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
        ),
    )
}.getOrNull()

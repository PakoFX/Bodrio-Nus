package com.pako.eminus4.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object BarcodeGenerator {

    /**
     * Genera un Bitmap con código QR a partir de un texto, con color personalizado
     * y soporte opcional para logo centrado.
     */
    fun generateQrBitmap(
        content: String,
        size: Int = 512,
        qrColor: Int = Color.parseColor("#00348A"),
        centerLogo: Bitmap? = null
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            }
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) qrColor else Color.WHITE)
                }
            }

            if (centerLogo != null) {
                val canvas = Canvas(bitmap)
                val logoW = (width * 0.22f).toInt()
                val logoH = (logoW * (centerLogo.height.toFloat() / centerLogo.width.toFloat())).toInt()
                val logoLeft = (width - logoW) / 2
                val logoTop = (height - logoH) / 2
                val logoRect = Rect(logoLeft, logoTop, logoLeft + logoW, logoTop + logoH)

                val whitePaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                // Fondo blanco alrededor del logo para separación limpia de los módulos
                canvas.drawRect(
                    RectF(
                        (logoLeft - 6).toFloat(),
                        (logoTop - 6).toFloat(),
                        (logoLeft + logoW + 6).toFloat(),
                        (logoTop + logoH + 6).toFloat()
                    ),
                    whitePaint
                )

                // Dibujar logo centrado
                canvas.drawBitmap(centerLogo, null, logoRect, null)
            }

            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Genera un código de barras lineal Code 128 (matrícula institucional).
     */
    fun generateBarcodeBitmap(content: String, width: Int = 600, height: Int = 180): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.MARGIN, 10)
            }
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.CODE_128,
                width,
                height,
                hints
            )
            val w = bitMatrix.width
            val h = bitMatrix.height
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}


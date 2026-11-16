package com.example.transfer

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeHelper {

  fun generateQrBitmap(content: String, sizePx: Int = 560): Bitmap? {
    if (content.isBlank() || sizePx <= 0) return null
    return runCatching {
      val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
      )
      val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val width = bitMatrix.width
      val height = bitMatrix.height
      val pixels = IntArray(width * height)
      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
        }
      }
      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
      }
    }.getOrNull()
  }

  fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return runCatching {
      val width = bitmap.width
      val height = bitmap.height
      val pixels = IntArray(width * height)
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
      val source = RGBLuminanceSource(width, height, pixels)
      decodeLuminanceSource(source)
    }.getOrNull()
  }

  fun decodeQrFromYuvFrame(
    yPlane: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int,
  ): String? {
    if (width <= 0 || height <= 0) return null
    return runCatching {
      val normalized = if (rowStride == width) {
        yPlane
      } else {
        ByteArray(width * height).also { out ->
          for (row in 0 until height) {
            val srcPos = row * rowStride
            val len = minOf(width, yPlane.size - srcPos)
            if (len > 0) {
              System.arraycopy(yPlane, srcPos, out, row * width, len)
            }
          }
        }
      }
      val source = PlanarYUVLuminanceSource(
        normalized,
        width,
        height,
        0,
        0,
        width,
        height,
        false,
      )
      decodeLuminanceSource(source)
    }.getOrNull()
  }

  private fun decodeLuminanceSource(source: com.google.zxing.LuminanceSource): String? {
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    val reader = MultiFormatReader().apply {
      setHints(
        mapOf(
          DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
          DecodeHintType.TRY_HARDER to true,
          DecodeHintType.CHARACTER_SET to "UTF-8",
        )
      )
    }
    return reader.decode(binaryBitmap).text
  }
}

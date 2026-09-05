package com.rebelroot.docscannerpro.core.ocr
import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.rebelroot.docscannerpro.core.model.BarcodeItem
import com.rebelroot.docscannerpro.core.model.BarcodeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class BarcodeEngine {
    suspend fun scanBarcodes(bitmap: Bitmap): List<BarcodeItem> = withContext(Dispatchers.Default) {
        try {
            val sourceBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val pixels = IntArray(sourceBitmap.width * sourceBitmap.height)
            sourceBitmap.getPixels(pixels, 0, sourceBitmap.width, 0, 0, sourceBitmap.width, sourceBitmap.height)
            val source = RGBLuminanceSource(sourceBitmap.width, sourceBitmap.height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val hints = mapOf(
                com.google.zxing.DecodeHintType.TRY_HARDER to true,
                com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    com.google.zxing.BarcodeFormat.EAN_13,
                    com.google.zxing.BarcodeFormat.EAN_8,
                    com.google.zxing.BarcodeFormat.UPC_A,
                    com.google.zxing.BarcodeFormat.UPC_E,
                    com.google.zxing.BarcodeFormat.CODE_128,
                    com.google.zxing.BarcodeFormat.CODE_39,
                    com.google.zxing.BarcodeFormat.ITF,
                    com.google.zxing.BarcodeFormat.DATA_MATRIX,
                    com.google.zxing.BarcodeFormat.PDF_417,
                    com.google.zxing.BarcodeFormat.AZTEC
                )
            )
            val result = reader.decode(binary, hints)
            listOf(result.toBarcodeItem())
        } catch (_: NotFoundException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    private fun Result.toBarcodeItem(): BarcodeItem {
        val raw = text.orEmpty()
        val type = when {
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> BarcodeType.URL
            raw.startsWith("WIFI:", true) -> BarcodeType.WIFI
            raw.startsWith("mailto:", true) -> BarcodeType.EMAIL
            raw.startsWith("tel:", true) -> BarcodeType.PHONE
            else -> BarcodeType.TEXT
        }
        return BarcodeItem(
            rawValue = raw,
            displayValue = raw,
            formatName = formatName,
            type = type
        )
    }
    private val Result.formatName: String
        get() = when (barcodeFormat) {
            com.google.zxing.BarcodeFormat.QR_CODE -> "QR Code"
            com.google.zxing.BarcodeFormat.EAN_13 -> "EAN-13"
            com.google.zxing.BarcodeFormat.EAN_8 -> "EAN-8"
            com.google.zxing.BarcodeFormat.UPC_A -> "UPC-A"
            com.google.zxing.BarcodeFormat.UPC_E -> "UPC-E"
            com.google.zxing.BarcodeFormat.CODE_128 -> "Code 128"
            com.google.zxing.BarcodeFormat.CODE_39 -> "Code 39"
            com.google.zxing.BarcodeFormat.ITF -> "ITF"
            com.google.zxing.BarcodeFormat.DATA_MATRIX -> "Data Matrix"
            com.google.zxing.BarcodeFormat.PDF_417 -> "PDF-417"
            com.google.zxing.BarcodeFormat.AZTEC -> "Aztec"
            else -> "Barcode"
        }
}

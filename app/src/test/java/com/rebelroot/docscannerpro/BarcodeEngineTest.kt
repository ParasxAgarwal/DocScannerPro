package com.rebelroot.docscannerpro

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.rebelroot.docscannerpro.core.ocr.BarcodeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BarcodeEngineTest {

    private fun encodeToBitmap(text: String, size: Int = 400): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    @Test
    fun `decodes a plain qr code`() = runBlocking {
        val engine = BarcodeEngine()
        val bitmap = encodeToBitmap("DOC-SCANNER-PRO-TEST")
        val results = engine.scanBarcodes(bitmap)
        assertTrue("Expected one result, got ${results.size}", results.isNotEmpty())
        assertEquals("DOC-SCANNER-PRO-TEST", results.first().rawValue)
    }

    @Test
    fun `decodes wifi qr payload`() = runBlocking {
        val engine = BarcodeEngine()
        val bitmap = encodeToBitmap("WIFI:T:WPA;S:TestNet;P:pw12345;;")
        val results = engine.scanBarcodes(bitmap)
        assertTrue(results.isNotEmpty())
        assertEquals("WIFI:T:WPA;S:TestNet;P:pw12345;;", results.first().rawValue)
    }

    @Test
    fun `returns empty when no code is present`() = runBlocking {
        val engine = BarcodeEngine()
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.RGB_565)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val results = engine.scanBarcodes(bitmap)
        assertTrue(results.isEmpty())
    }
}

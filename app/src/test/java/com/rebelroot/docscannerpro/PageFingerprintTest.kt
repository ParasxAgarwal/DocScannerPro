package com.rebelroot.docscannerpro

import com.rebelroot.docscannerpro.core.cv.PageFingerprint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFingerprintTest {

    private fun flatGray(value: Int): IntArray = IntArray(PageFingerprint.GRID * PageFingerprint.GRID) { value }

    @Test
    fun `identical content hashes are the same page`() {
        val a = PageFingerprint.computeHash(flatGray(120))
        val b = PageFingerprint.computeHash(flatGray(124))
        assertTrue(PageFingerprint.areSamePage(a, b))
    }

    @Test
    fun `half dark half light differs from inverted half`() {
        val grid = PageFingerprint.GRID
        fun half(topBright: Boolean): IntArray {
            val pixels = IntArray(grid * grid)
            for (row in 0 until grid) {
                val value = if ((row < grid / 2) == topBright) 200 else 20
                for (col in 0 until grid) pixels[row * grid + col] = value
            }
            return pixels
        }
        val a = PageFingerprint.computeHash(half(topBright = true))
        val b = PageFingerprint.computeHash(half(topBright = false))
        assertFalse(PageFingerprint.areSamePage(a, b))
    }

    @Test
    fun `checkerboard vs flat page are different`() {
        val grid = PageFingerprint.GRID
        val checker = IntArray(grid * grid) { i ->
            val row = i / grid
            val col = i % grid
            if ((row + col) % 2 == 0) 220 else 10
        }
        val a = PageFingerprint.computeHash(checker)
        val b = PageFingerprint.computeHash(flatGray(115))
        assertFalse(PageFingerprint.areSamePage(a, b))
    }

    @Test
    fun `difference is bounded between zero and one`() {
        val a = PageFingerprint.computeHash(flatGray(10))
        val b = PageFingerprint.computeHash(flatGray(240))
        val diff = PageFingerprint.difference(a, b)
        assertTrue(diff in 0f..1f)
    }
}

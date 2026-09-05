package com.rebelroot.docscannerpro.core.cv

/**
 * Lightweight perceptual fingerprint used to detect duplicate page captures
 * and to notice page turns during auto capture. Pure Kotlin so it can be
 * unit tested without an Android device.
 */
object PageFingerprint {

    const val GRID = 16

    /**
     * Computes a 16x16 average hash from grayscale pixel values.
     * [gray] is row-major, length >= grid * grid; values may be any scale.
     */
    fun computeHash(gray: IntArray, sampleStride: Int = 1): LongArray {
        val cells = IntArray(GRID * GRID)
        val counts = IntArray(GRID * GRID)
        var i = 0
        while (i < gray.size) {
            val lum = gray[i]
            val col = (i % GRID)
            val row = (i / GRID)
            if (row < GRID) {
                cells[row * GRID + col] += lum
                counts[row * GRID + col]++
            }
            i += sampleStride
        }
        val means = LongArray(GRID * GRID)
        for (c in cells.indices) {
            val n = counts[c].coerceAtLeast(1)
            means[c] = (cells[c].toLong() / n)
        }
        val overall = means.average().toLong()
        return LongArray(GRID * GRID) { if (means[it] > overall) 1L else 0L }
    }

    fun hammingDistance(a: LongArray, b: LongArray): Int {
        val n = minOf(a.size, b.size)
        var distance = 0
        for (i in 0 until n) if (a[i] != b[i]) distance++
        return distance + (maxOf(a.size, b.size) - n)
    }

    /** Fraction of differing cells; 0 = identical, 1 = opposite. */
    fun difference(a: LongArray, b: LongArray): Float =
        hammingDistance(a, b).toFloat() / GRID * GRID

    /**
     * True when two fingerprints represent the same page content.
     * Threshold tuned with hysteresis in mind: camera noise, small exposure
     * shifts and minor background motion must read as the SAME page, while a
     * genuinely turned page differs by far more.
     */
    fun areSamePage(a: LongArray, b: LongArray): Boolean =
        hammingDistance(a, b) <= (GRID * GRID * 0.10f).toInt()
}

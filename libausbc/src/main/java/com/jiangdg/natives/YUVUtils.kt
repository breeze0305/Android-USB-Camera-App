package com.jiangdg.natives

/**
 * Small in-place YUV frame conversions used by the H.264 encoder.
 *
 * The old implementation lived in a separate JNI library. Keeping this code in
 * Kotlin removes that native dependency while preserving the existing API used
 * by the library.
 */
object YUVUtils {
    fun yuv420spToNv21(data: ByteArray, width: Int, height: Int) {
        swapSemiPlanarChroma(data, width, height)
    }

    fun nv21ToYuv420sp(data: ByteArray, width: Int, height: Int) {
        swapSemiPlanarChroma(data, width, height)
    }

    fun nv21ToYuv420spWithMirror(data: ByteArray, width: Int, height: Int) {
        if (!hasValidFrame(data, width, height)) return
        val src = data.copyOf()
        val ySize = width * height

        for (row in 0 until height) {
            val rowOffset = row * width
            for (col in 0 until width) {
                data[rowOffset + col] = src[rowOffset + width - 1 - col]
            }
        }

        var dest = ySize
        for (row in 0 until height / 2) {
            val rowOffset = ySize + row * width
            for (col in width - 2 downTo 0 step 2) {
                data[dest++] = src[rowOffset + col + 1]
                data[dest++] = src[rowOffset + col]
            }
        }
    }

    fun nv21ToYuv420p(data: ByteArray, width: Int, height: Int) {
        if (!hasValidFrame(data, width, height)) return
        val src = data.copyOf()
        val ySize = width * height
        val quarterSize = ySize / 4
        src.copyInto(data, endIndex = ySize)

        for (index in 0 until quarterSize) {
            data[ySize + index] = src[ySize + index * 2 + 1]
            data[ySize + quarterSize + index] = src[ySize + index * 2]
        }
    }

    fun nv21ToYuv420pWithMirror(data: ByteArray, width: Int, height: Int) {
        if (!hasValidFrame(data, width, height)) return
        val src = data.copyOf()
        val ySize = width * height
        val quarterSize = ySize / 4

        for (row in 0 until height) {
            val rowOffset = row * width
            for (col in 0 until width) {
                data[rowOffset + col] = src[rowOffset + width - 1 - col]
            }
        }

        var uDest = ySize
        var vDest = ySize + quarterSize
        for (row in 0 until height / 2) {
            val rowOffset = ySize + row * width
            for (col in width - 2 downTo 0 step 2) {
                data[uDest++] = src[rowOffset + col + 1]
                data[vDest++] = src[rowOffset + col]
            }
        }
    }

    fun nativeRotateNV21(data: ByteArray, width: Int, height: Int, degree: Int) {
        if (!hasValidFrame(data, width, height)) return
        when (normalizeDegree(degree)) {
            0 -> return
            90 -> rotate90(data, width, height)
            180 -> rotate180(data, width, height)
            270 -> rotate270(data, width, height)
        }
    }

    private fun swapSemiPlanarChroma(data: ByteArray, width: Int, height: Int) {
        if (!hasValidFrame(data, width, height)) return
        val ySize = width * height
        val frameSize = ySize * 3 / 2
        var offset = ySize
        while (offset + 1 < frameSize) {
            val first = data[offset]
            data[offset] = data[offset + 1]
            data[offset + 1] = first
            offset += 2
        }
    }

    private fun rotate90(data: ByteArray, width: Int, height: Int) {
        val src = data.copyOf()
        val ySize = width * height
        var dest = 0

        for (col in 0 until width) {
            for (row in height - 1 downTo 0) {
                data[dest++] = src[row * width + col]
            }
        }

        for (col in 0 until width step 2) {
            for (row in height / 2 - 1 downTo 0) {
                val srcOffset = ySize + row * width + col
                data[dest++] = src[srcOffset]
                data[dest++] = src[srcOffset + 1]
            }
        }
    }

    private fun rotate180(data: ByteArray, width: Int, height: Int) {
        val src = data.copyOf()
        val ySize = width * height
        var dest = 0

        for (srcOffset in ySize - 1 downTo 0) {
            data[dest++] = src[srcOffset]
        }

        for (srcOffset in src.size - 1 downTo ySize + 1 step 2) {
            data[dest++] = src[srcOffset - 1]
            data[dest++] = src[srcOffset]
        }
    }

    private fun rotate270(data: ByteArray, width: Int, height: Int) {
        val src = data.copyOf()
        val ySize = width * height
        var dest = 0

        for (col in width - 1 downTo 0) {
            for (row in height - 1 downTo 0) {
                data[dest++] = src[row * width + col]
            }
        }

        for (col in width - 1 downTo 1 step 2) {
            for (row in height / 2 - 1 downTo 0) {
                val srcOffset = ySize + row * width + col
                data[dest++] = src[srcOffset - 1]
                data[dest++] = src[srcOffset]
            }
        }
    }

    private fun hasValidFrame(data: ByteArray, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) return false
        return data.size >= width * height * 3 / 2
    }

    private fun normalizeDegree(degree: Int): Int {
        val normalized = degree % 360
        return if (normalized < 0) normalized + 360 else normalized
    }
}

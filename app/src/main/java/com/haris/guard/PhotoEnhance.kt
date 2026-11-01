package com.haris.guard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ln
import kotlin.math.pow

// Feature: clearer photos in the dark.
// Fixes rotation, then brightens dark photos with auto-levels + gamma.
object PhotoEnhance {

    fun process(raw: ByteArray, tmp: File): ByteArray {
        return try {
            tmp.writeBytes(raw)
            val orient = try {
                ExifInterface(tmp.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
            tmp.delete()

            var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
            val deg = when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg != 0f) {
                val m = Matrix()
                m.postRotate(deg)
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r != bmp) bmp.recycle()
                bmp = r
            }

            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)

            val hist = IntArray(256)
            var sum = 0L
            var n = 0
            var i = 0
            while (i < px.size) {
                val c = px[i]
                val y = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
                hist[y]++
                sum += y
                n++
                i += 7
            }
            val mean = sum.toDouble() / n

            if (mean < 115) {
                var low = 0
                var acc = 0
                while (low < 255 && acc + hist[low] < n * 0.01) { acc += hist[low]; low++ }
                var high = 255
                acc = 0
                while (high > 0 && acc + hist[high] < n * 0.005) { acc += hist[high]; high-- }
                if (high - low < 24) high = (low + 24).coerceAtMost(255)

                val stretchedMean = ((mean - low) * 255.0 / (high - low)).coerceIn(1.0, 254.0) / 255.0
                val gamma = (ln(0.45) / ln(stretchedMean)).coerceIn(0.35, 1.0)

                val lut = IntArray(256)
                for (v in 0..255) {
                    val s = ((v - low).toDouble() / (high - low)).coerceIn(0.0, 1.0)
                    lut[v] = (255.0 * s.pow(gamma)).toInt().coerceIn(0, 255)
                }
                for (k in px.indices) {
                    val c = px[k]
                    px[k] = (0xFF shl 24) or
                        (lut[(c shr 16) and 255] shl 16) or
                        (lut[(c shr 8) and 255] shl 8) or
                        lut[c and 255]
                }
            }

            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            val bos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            out.recycle()
            bos.toByteArray()
        } catch (_: Throwable) {
            raw
        }
    }
}

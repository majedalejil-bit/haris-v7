package com.haris.guard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Feature: clearer photos in the dark.
// 1) averages several frames (removes most of the noise)
// 2) corrects the colour cast (no more purple)
// 3) smooths colour noise, brightens only the light level
object PhotoEnhance {

    private const val MAX_SIDE = 1280

    fun process(frames: List<ByteArray>, tmp: File): ByteArray {
        val first = frames.firstOrNull() ?: return ByteArray(0)
        return try {
            val deg = rotation(first, tmp)

            // ---- 1. average frames ----
            var w = 0
            var h = 0
            var sr = IntArray(0)
            var sg = IntArray(0)
            var sb = IntArray(0)
            var used = 0
            for (f in frames) {
                val bmp = decode(f, deg) ?: continue
                if (used == 0) {
                    w = bmp.width; h = bmp.height
                    sr = IntArray(w * h); sg = IntArray(w * h); sb = IntArray(w * h)
                }
                if (bmp.width != w || bmp.height != h) { bmp.recycle(); continue }
                val px = IntArray(w * h)
                bmp.getPixels(px, 0, w, 0, 0, w, h)
                bmp.recycle()
                for (i in px.indices) {
                    val c = px[i]
                    sr[i] += (c shr 16) and 255
                    sg[i] += (c shr 8) and 255
                    sb[i] += c and 255
                }
                used++
            }
            if (used == 0) return first
            val n = w * h

            // ---- 2. luma / chroma split ----
            val y = IntArray(n)
            val cb = IntArray(n)
            val cr = IntArray(n)
            var mr = 0L; var mg = 0L; var mb = 0L
            for (i in 0 until n) {
                mr += sr[i]; mg += sg[i]; mb += sb[i]
            }
            // gray-world white balance gains (x1000)
            val avg = (mr + mg + mb) / 3.0
            val dark = avg / (n.toDouble() * used) < 115
            val gr = if (dark) clampD(avg / max(mr, 1), 0.6, 1.7) else 1.0
            val gg = if (dark) clampD(avg / max(mg, 1), 0.6, 1.7) else 1.0
            val gb = if (dark) clampD(avg / max(mb, 1), 0.6, 1.7) else 1.0
            val hist = IntArray(256)
            var ysum = 0L
            for (i in 0 until n) {
                val r = sr[i] * gr / used
                val g = sg[i] * gg / used
                val b = sb[i] * gb / used
                val yy = (0.299 * r + 0.587 * g + 0.114 * b)
                val yi = yy.toInt().coerceIn(0, 255)
                y[i] = yi
                cb[i] = ((b - yy) * 0.564).toInt()
                cr[i] = ((r - yy) * 0.713).toInt()
                hist[yi]++
                ysum += yi
            }
            sr = IntArray(0); sg = IntArray(0); sb = IntArray(0)
            val mean = ysum.toDouble() / n

            // ---- 3. smooth colour noise strongly, light level gently ----
            val cr2 = if (dark) blur(cr, w, h, max(2, w / 110)) else cr
            val cb2 = if (dark) blur(cb, w, h, max(2, w / 110)) else cb
            val y2 = if (mean < 70) blur(y, w, h, 1) else y

            // ---- 4. brighten light level only ----
            val lut = IntArray(256) { it }
            var sat = 1.0
            if (mean < 115) {
                var low = 0
                var acc = 0
                while (low < 255 && acc + hist[low] < n * 0.01) { acc += hist[low]; low++ }
                var high = 255
                acc = 0
                while (high > 0 && acc + hist[high] < n * 0.003) { acc += hist[high]; high-- }
                if (high - low < 30) high = min(low + 30, 255)
                val sm = clampD((mean - low) / (high - low), 0.01, 0.99)
                val gamma = clampD(ln(0.42) / ln(sm), 0.5, 1.0)
                for (v in 0..255) {
                    val s = clampD((v - low).toDouble() / (high - low), 0.0, 1.0)
                    lut[v] = (255.0 * s.pow(gamma)).toInt().coerceIn(0, 255)
                }
                sat = if (mean < 40) 0.7 else 0.85
            }

            val out = IntArray(n)
            for (i in 0 until n) {
                val yy = lut[y2[i]].toDouble()
                val b = cb2[i] * sat
                val r = cr2[i] * sat
                val rr = (yy + 1.403 * r).toInt().coerceIn(0, 255)
                val gg2 = (yy - 0.344 * b - 0.714 * r).toInt().coerceIn(0, 255)
                val bb = (yy + 1.773 * b).toInt().coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (rr shl 16) or (gg2 shl 8) or bb
            }

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(out, 0, w, 0, 0, w, h)
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            bmp.recycle()
            bos.toByteArray()
        } catch (_: Throwable) {
            first
        }
    }

    private fun clampD(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v

    private fun rotation(raw: ByteArray, tmp: File): Float {
        return try {
            tmp.writeBytes(raw)
            val o = ExifInterface(tmp.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            when (o) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        } finally {
            tmp.delete()
        }
    }

    private fun decode(raw: ByteArray, deg: Float): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val o = BitmapFactory.Options()
        o.inSampleSize = sample
        var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, o) ?: return null
        val side = max(bmp.width, bmp.height)
        if (side > MAX_SIDE) {
            val k = MAX_SIDE.toFloat() / side
            val s = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
            if (s != bmp) bmp.recycle()
            bmp = s
        }
        if (deg != 0f) {
            val m = Matrix()
            m.postRotate(deg)
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (r != bmp) bmp.recycle()
            bmp = r
        }
        return bmp
    }

    // separable box blur, applied twice (close to gaussian)
    private fun blur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        var a = src
        repeat(2) {
            a = boxV(boxH(a, w, h, r), w, h, r)
        }
        return a
    }

    private fun boxH(s: IntArray, w: Int, h: Int, r: Int): IntArray {
        val o = IntArray(s.size)
        for (row in 0 until h) {
            val base = row * w
            var sum = 0
            var cnt = 0
            for (x in 0..min(r, w - 1)) { sum += s[base + x]; cnt++ }
            for (x in 0 until w) {
                o[base + x] = sum / cnt
                val add = x + r + 1
                val rem = x - r
                if (add < w) { sum += s[base + add]; cnt++ }
                if (rem >= 0) { sum -= s[base + rem]; cnt-- }
            }
        }
        return o
    }

    private fun boxV(s: IntArray, w: Int, h: Int, r: Int): IntArray {
        val o = IntArray(s.size)
        for (col in 0 until w) {
            var sum = 0
            var cnt = 0
            for (yy in 0..min(r, h - 1)) { sum += s[yy * w + col]; cnt++ }
            for (yy in 0 until h) {
                o[yy * w + col] = sum / cnt
                val add = yy + r + 1
                val rem = yy - r
                if (add < h) { sum += s[add * w + col]; cnt++ }
                if (rem >= 0) { sum -= s[rem * w + col]; cnt-- }
            }
        }
        return o
    }
}

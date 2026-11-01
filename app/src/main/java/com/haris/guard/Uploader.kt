package com.haris.guard

import android.content.Context
import java.io.File

object Uploader {
    private val lock = Any()
    @Volatile private var lastMsg = ""

    private fun logOnce(c: Context, m: String) {
        if (m != lastMsg) {
            lastMsg = m
            PhotoStore.log(c, m)
        }
    }

    fun pending(c: Context): Int =
        PhotoStore.dir(c).listFiles()?.count { (it.name.startsWith("img_") || it.name.startsWith("wait_")) && it.name.endsWith(".jpg") } ?: 0

    fun flush(c: Context) {
        Thread {
            synchronized(lock) {
                if (!Auth.loggedIn(c)) return@synchronized
                PhotoStore.dir(c).listFiles()
                    ?.filter { it.name.startsWith("wait_") && it.name.endsWith(".jpg") && System.currentTimeMillis() - it.lastModified() > 60000 }
                    ?.forEach { PhotoStore.ready(it) }
                val files = PhotoStore.dir(c).listFiles()
                    ?.filter { it.name.startsWith("img_") && it.name.endsWith(".jpg") }
                    ?.sortedBy { it.lastModified() }
                    ?: return@synchronized
                if (files.isEmpty()) return@synchronized
                val t = Auth.token(c)
                if (t.isBlank()) {
                    logOnce(c, "تعذّر تجديد الدخول، سجّل الدخول من جديد")
                    return@synchronized
                }
                for (f in files) {
                    var loc = PhotoStore.readLoc(f)
                    if (loc == null && System.currentTimeMillis() - f.lastModified() < 10 * 60 * 1000L) {
                        val k = LocationHelper.lastKnown(c)
                        if (k != null && Math.abs(f.lastModified() - k.time) < 30 * 60 * 1000L) {
                            loc = doubleArrayOf(k.latitude, k.longitude, k.accuracy.toDouble())
                        }
                    }
                    val r = CloudStore.upload(c, f, t, loc)
                    if (r == "ok") {
                        val m = Mailer.send(c, t, f, loc)
                        PhotoStore.markSent(f)
                        lastMsg = ""
                        PhotoStore.log(c, "رُفعت صورة إلى حسابك" + (if (loc != null) " مع الموقع" else ""))
                        if (m == "ok") PhotoStore.log(c, "أُرسل تنبيه إلى بريدك")
                        else if (m != "off") PhotoStore.log(c, "تعذّر إرسال البريد: $m")
                    } else {
                        logOnce(c, "فشل الرفع: $r")
                        break
                    }
                }
            }
        }.start()
    }
}

package com.haris.guard

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoStore {
    fun dir(c: Context): File = File(c.getExternalFilesDir(null), "intruders").also { it.mkdirs() }

    private fun locFile(f: File) = File(f.path + ".loc")

    fun saveLoc(f: File, lat: Double, lng: Double, acc: Double) {
        try { locFile(f).writeText("$lat,$lng,$acc") } catch (_: Exception) {}
    }

    fun readLoc(f: File): DoubleArray? = try {
        val p = locFile(f).readText().split(",").map { it.toDouble() }
        doubleArrayOf(p[0], p[1], p[2])
    } catch (_: Exception) { null }

    // wait_x.jpg -> img_x.jpg (ready to upload), keeps its location file
    fun ready(f: File): File {
        val to = File(f.parentFile, f.name.replaceFirst("wait_", "img_"))
        val lf = locFile(f)
        if (lf.exists()) lf.renameTo(locFile(to))
        f.renameTo(to)
        return to
    }

    fun markSent(f: File) {
        val to = File(f.parentFile, "sent_" + f.name)
        val lf = locFile(f)
        if (lf.exists()) lf.renameTo(locFile(to))
        f.renameTo(to)
    }

    fun photos(c: Context): List<File> =
        dir(c).listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun log(c: Context, m: String) {
        try {
            val f = File(c.filesDir, "log.txt")
            if (f.exists() && f.length() > 20000) {
                f.writeText(f.readLines().takeLast(30).joinToString("\n") + "\n")
            }
            val t = SimpleDateFormat("HH:mm:ss dd/MM", Locale.US).format(Date())
            f.appendText("$t  $m\n")
        } catch (_: Exception) {}
    }

    fun lastLog(c: Context): String {
        val f = File(c.filesDir, "log.txt")
        return if (f.exists()) f.readLines().takeLast(8).joinToString("\n") else "(فارغ)"
    }
}

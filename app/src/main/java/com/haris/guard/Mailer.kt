package com.haris.guard

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Feature: instant email alert with the photo and the location.
// Sends to the email of the signed-in account only (checked on the server side).
object Mailer {

    const val ENDPOINT = "__MAIL_URL__"

    private fun prefs(c: Context) = c.getSharedPreferences("mail", Context.MODE_PRIVATE)

    fun available(): Boolean = ENDPOINT.startsWith("https://")

    fun enabled(c: Context): Boolean = prefs(c).getBoolean("on", true)

    fun setEnabled(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("on", v).apply()
    }

    fun send(c: Context, token: String, photo: File, loc: DoubleArray?): String {
        if (!available() || !enabled(c)) return "off"
        return try {
            val when_ = SimpleDateFormat("HH:mm:ss  dd/MM/yyyy", Locale.US).format(Date(photo.lastModified()))
            val body = JSONObject()
                .put("idToken", token)
                .put("img", Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP))
                .put("when", when_)
            if (loc != null) body.put("lat", loc[0]).put("lng", loc[1]).put("acc", loc[2])

            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 40000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code in 200..399) "ok" else "خطأ $code"
        } catch (e: Exception) {
            "لا يوجد اتصال (" + e.javaClass.simpleName + ")"
        }
    }
}

package com.haris.guard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object CloudStore {
    private fun post(url: String, token: String, body: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val s = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = s?.bufferedReader()?.use { it.readText() } ?: ""
        return Pair(code, text)
    }

    private fun small(f: File): ByteArray {
        val bmp = BitmapFactory.decodeFile(f.path) ?: return f.readBytes()
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bmp.recycle()
        return out.toByteArray()
    }

    class Shot(val time: Long, val bytes: ByteArray, val lat: Double?, val lng: Double?)

    fun upload(c: Context, f: File, token: String, loc: DoubleArray? = null): String {
        return try {
            val uid = Auth.uid(c)
            val b64 = Base64.encodeToString(small(f), Base64.NO_WRAP)
            val fields = JSONObject()
                .put("img", JSONObject().put("stringValue", b64))
                .put("time", JSONObject().put("integerValue", f.lastModified().toString()))
            if (loc != null) {
                fields.put("lat", JSONObject().put("doubleValue", loc[0]))
                fields.put("lng", JSONObject().put("doubleValue", loc[1]))
                fields.put("acc", JSONObject().put("doubleValue", loc[2]))
            }
            val body = JSONObject().put("fields", fields).toString()
            val r = post(FirebaseCfg.DOCS + "/users/" + uid + "/photos", token, body)
            when (r.first) {
                200 -> "ok"
                401, 403 -> "لا توجد صلاحية، انشر قواعد Firestore أو أعد تسجيل الدخول"
                else -> "خطأ " + r.first
            }
        } catch (e: Exception) {
            "لا يوجد اتصال (" + e.javaClass.simpleName + ")"
        }
    }

    fun list(c: Context, token: String): List<Shot>? {
        return try {
            val uid = Auth.uid(c)
            val order = JSONObject()
                .put("field", JSONObject().put("fieldPath", "time"))
                .put("direction", "DESCENDING")
            val sq = JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", "photos")))
                .put("orderBy", JSONArray().put(order))
                .put("limit", 20)
            val body = JSONObject().put("structuredQuery", sq).toString()
            val r = post(FirebaseCfg.DOCS + "/users/" + uid + ":runQuery", token, body)
            if (r.first != 200) return null
            val arr = JSONArray(r.second)
            val out = ArrayList<Shot>()
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i).optJSONObject("document") ?: continue
                val fl = d.getJSONObject("fields")
                val img = fl.getJSONObject("img").getString("stringValue")
                val t = fl.getJSONObject("time").getString("integerValue").toLong()
                val lat = if (fl.has("lat")) fl.getJSONObject("lat").optDouble("doubleValue") else null
                val lng = if (fl.has("lng")) fl.getJSONObject("lng").optDouble("doubleValue") else null
                out.add(Shot(t, Base64.decode(img, Base64.DEFAULT), lat, lng))
            }
            out
        } catch (e: Exception) {
            null
        }
    }
}

package com.haris.guard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Invite {
    const val PROJECT = "hars-983d4"
    const val API_KEY = ""
    const val LINK = "https://github.com/majedalejil-bit/haris-v7/releases/latest/download/haris.apk"
    private const val BASE = "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents"

    private fun prefs(c: Context) = c.getSharedPreferences("invite", Context.MODE_PRIVATE)

    fun myCode(c: Context): String {
        val p = prefs(c)
        var code = p.getString("code", "") ?: ""
        if (code.isBlank()) {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            code = (1..6).map { chars.random() }.joinToString("")
            p.edit().putString("code", code).apply()
        }
        return code
    }

    fun invitedBy(c: Context): String = prefs(c).getString("by", "") ?: ""

    private fun url(path: String, query: String = ""): URL {
        val key = if (API_KEY.isBlank()) "" else "key=$API_KEY"
        val q = listOf(query, key).filter { it.isNotBlank() }.joinToString("&")
        return URL(BASE + path + (if (q.isEmpty()) "" else "?$q"))
    }

    private fun call(u: URL, method: String, body: String?): Pair<Int, String> {
        val conn = u.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        if (method == "PATCH") {
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            conn.requestMethod = method
        }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return Pair(status, text)
    }

    private fun inviterBody(value: String): String {
        val fields = JSONObject().put("inviter", JSONObject().put("stringValue", value))
        return JSONObject().put("fields", fields).toString()
    }

    fun register(c: Context): String {
        return try {
            val p = prefs(c)
            if (p.getBoolean("reg", false)) return "ok"
            val fields = JSONObject()
                .put("inviter", JSONObject().put("stringValue", ""))
                .put("created", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
            val body = JSONObject().put("fields", fields).toString()
            val r = call(url("/members", "documentId=" + myCode(c)), "POST", body)
            if (r.first == 200 || r.first == 409) {
                p.edit().putBoolean("reg", true).apply()
                "ok"
            } else {
                "تعذّر التسجيل (" + r.first + ")"
            }
        } catch (e: Exception) {
            "لا يوجد اتصال"
        }
    }

    fun claim(c: Context, raw: String): String {
        return try {
            val code = raw.trim().uppercase()
            if (code.length != 6) return "الرمز يجب أن يكون 6 خانات"
            if (code == myCode(c)) return "لا يمكنك استخدام رمزك أنت"
            if (invitedBy(c).isNotBlank()) return "سجّلت رمز دعوة من قبل"
            val reg = register(c)
            if (reg != "ok") return reg
            val g = call(url("/members/" + code), "GET", null)
            if (g.first == 404) return "هذا الرمز غير موجود"
            if (g.first != 200) return "تعذّر التحقق (" + g.first + ")"
            val r = call(url("/members/" + myCode(c), "updateMask.fieldPaths=inviter"), "PATCH", inviterBody(code))
            if (r.first == 200) {
                prefs(c).edit().putString("by", code).apply()
                "تم تسجيل رمز الدعوة"
            } else {
                "تعذّر التسجيل (" + r.first + ")"
            }
        } catch (e: Exception) {
            "لا يوجد اتصال"
        }
    }

    fun count(c: Context): String {
        return try {
            register(c)
            val value = JSONObject().put("stringValue", myCode(c))
            val filter = JSONObject()
                .put("field", JSONObject().put("fieldPath", "inviter"))
                .put("op", "EQUAL")
                .put("value", value)
            val sq = JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", "members")))
                .put("where", JSONObject().put("fieldFilter", filter))
            val agg = JSONArray().put(JSONObject().put("alias", "n").put("count", JSONObject()))
            val q = JSONObject().put(
                "structuredAggregationQuery",
                JSONObject().put("structuredQuery", sq).put("aggregations", agg)
            )
            val r = call(url(":runAggregationQuery"), "POST", q.toString())
            if (r.first != 200) return "تعذّر (" + r.first + ")"
            JSONArray(r.second).getJSONObject(0).getJSONObject("result")
                .getJSONObject("aggregateFields").getJSONObject("n").getString("integerValue")
        } catch (e: Exception) {
            "لا يوجد اتصال"
        }
    }
}

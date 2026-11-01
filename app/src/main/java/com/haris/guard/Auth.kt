package com.haris.guard

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Auth {
    private fun prefs(c: Context) = c.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun loggedIn(c: Context): Boolean = (prefs(c).getString("refresh", "") ?: "").isNotBlank()

    fun email(c: Context): String = prefs(c).getString("email", "") ?: ""

    fun uid(c: Context): String = prefs(c).getString("uid", "") ?: ""

    fun logout(c: Context) {
        prefs(c).edit().clear().apply()
    }

    private fun post(url: String, body: String, type: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", type)
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val s = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = s?.bufferedReader()?.use { it.readText() } ?: ""
        return Pair(code, text)
    }

    private fun errorText(raw: String): String {
        val m = try {
            JSONObject(raw).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            ""
        }
        return when {
            m.startsWith("EMAIL_EXISTS") -> "هذا البريد مسجّل من قبل، اضغط تسجيل الدخول"
            m.startsWith("WEAK_PASSWORD") -> "كلمة المرور ضعيفة، اكتب 6 أحرف أو أكثر"
            m.startsWith("INVALID_EMAIL") -> "صيغة البريد غير صحيحة"
            m.startsWith("EMAIL_NOT_FOUND") -> "هذا البريد غير مسجّل، اضغط إنشاء حساب جديد"
            m.startsWith("INVALID_PASSWORD") || m.startsWith("INVALID_LOGIN_CREDENTIALS") -> "البريد أو كلمة المرور غير صحيحة"
            m.startsWith("TOO_MANY_ATTEMPTS") -> "محاولات كثيرة، انتظر قليلًا ثم أعد المحاولة"
            m.startsWith("OPERATION_NOT_ALLOWED") -> "تسجيل البريد وكلمة المرور غير مفعّل في Firebase"
            m.contains("API key") -> "مفتاح Firebase (apiKey) غير صحيح"
            m.isBlank() -> "تعذّر الاتصال"
            else -> "خطأ: $m"
        }
    }

    private fun sign(c: Context, endpoint: String, email: String, pass: String): String {
        return try {
            val body = JSONObject()
                .put("email", email)
                .put("password", pass)
                .put("returnSecureToken", true)
                .toString()
            val r = post(
                "https://identitytoolkit.googleapis.com/v1/accounts:" + endpoint + "?key=" + FirebaseCfg.API_KEY,
                body,
                "application/json"
            )
            if (r.first != 200) return errorText(r.second)
            val j = JSONObject(r.second)
            val exp = System.currentTimeMillis() + j.getString("expiresIn").toLong() * 1000
            prefs(c).edit()
                .putString("id", j.getString("idToken"))
                .putString("refresh", j.getString("refreshToken"))
                .putString("uid", j.getString("localId"))
                .putString("email", email)
                .putLong("exp", exp)
                .apply()
            "ok"
        } catch (e: Exception) {
            "لا يوجد اتصال بالإنترنت"
        }
    }

    fun signIn(c: Context, email: String, pass: String): String = sign(c, "signInWithPassword", email, pass)

    fun signUp(c: Context, email: String, pass: String): String = sign(c, "signUp", email, pass)

    @Synchronized
    fun token(c: Context): String {
        val p = prefs(c)
        val refresh = p.getString("refresh", "") ?: ""
        if (refresh.isBlank()) return ""
        if (System.currentTimeMillis() < p.getLong("exp", 0L) - 60000L) {
            return p.getString("id", "") ?: ""
        }
        return try {
            val body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8")
            val r = post(
                "https://securetoken.googleapis.com/v1/token?key=" + FirebaseCfg.API_KEY,
                body,
                "application/x-www-form-urlencoded"
            )
            if (r.first != 200) return ""
            val j = JSONObject(r.second)
            val exp = System.currentTimeMillis() + j.getString("expires_in").toLong() * 1000
            p.edit()
                .putString("id", j.getString("id_token"))
                .putString("refresh", j.getString("refresh_token"))
                .putLong("exp", exp)
                .apply()
            j.getString("id_token")
        } catch (e: Exception) {
            ""
        }
    }
}

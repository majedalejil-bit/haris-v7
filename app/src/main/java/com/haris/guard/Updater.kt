package com.haris.guard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val VERSION = 9
    private const val API = "https://api.github.com/repos/majedalejil-bit/haris-v7/releases/latest"
    @Volatile private var lastCheck = 0L

    // checks at most every 20 minutes (the app process stays alive because of protection)
    fun check(a: Activity) {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 20 * 60 * 1000L) return
        lastCheck = now
        Thread {
            try {
                val c = URL(API).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "haris")
                c.connectTimeout = 15000
                c.readTimeout = 15000
                if (c.responseCode != 200) return@Thread
                val tag = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optString("tag_name")
                val latest = tag.filter { it.isDigit() }.toIntOrNull() ?: return@Thread
                if (latest <= VERSION) return@Thread
                a.runOnUiThread {
                    if (a.isFinishing) return@runOnUiThread
                    AlertDialog.Builder(a)
                        .setTitle("تحديث جديد متاح")
                        .setMessage("صدر إصدار أحدث من حارس الهاتف. حمّله وثبّته فوق النسخة الحالية، صورك وحسابك يبقون كما هم.")
                        .setPositiveButton("تحميل التحديث") { _, _ ->
                            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Invite.LINK)))
                        }
                        .setNegativeButton("لاحقًا", null)
                        .show()
                }
            } catch (_: Exception) {}
        }.start()
    }
}

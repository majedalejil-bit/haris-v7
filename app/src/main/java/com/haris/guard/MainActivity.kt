package com.haris.guard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var inviterBox: EditText
    private lateinit var inviteInfo: TextView

    private fun comp() = ComponentName(this, AdminReceiver::class.java)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (!Auth.loggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        val root = Ui.page(this)
        root.addView(Ui.title(this, "حارس الهاتف"))
        root.addView(Ui.text(this, "الحساب: " + Auth.email(this), Ui.MUTED, 14f, 6))
        status = Ui.text(this, "", 0xFFCBD5E1.toInt(), 14f, 16)
        root.addView(status)

        root.addView(Ui.button(this, "1. تفعيل صلاحية المراقبة", true) { askAdmin() })
        root.addView(Ui.button(this, "2. تشغيل الحماية", true) { start() })
        root.addView(Ui.button(this, "تحديث", false) { refresh() })

        root.addView(Ui.section(this, "ادعُ أصدقاءك"))
        inviteInfo = Ui.text(this, "رمز دعوتك: " + Invite.myCode(this), 0xFFCBD5E1.toInt(), 14f, 6)
        root.addView(inviteInfo)
        root.addView(Ui.button(this, "مشاركة رابط التطبيق", false) { shareInvite() })
        root.addView(Ui.button(this, "تحديث عدد المسجّلين", false) { showCount() })
        inviterBox = Ui.input(this, "رمز دعوة من صديق (اختياري)", false, false)
        root.addView(inviterBox)
        root.addView(Ui.button(this, "تسجيل رمز الدعوة", false) { claimCode() })

        root.addView(Ui.section(this, "تنبيهات ومتابعة"))
        root.addView(Ui.button(this, "إعدادات الموقع", false) { fixLocation() })
        if (Mailer.available()) {
            mailBtn = Ui.button(this, mailLabel(), false) { toggleMail() }
            root.addView(mailBtn)
        }
        if (Web.available()) {
            root.addView(Ui.button(this, "عرض صوري من أي متصفح", false) { openUrl(Web.URL) })
        }

        root.addView(Ui.button(this, "المصمم والمنفذ", false) { showAbout() })
        root.addView(Ui.button(this, "تسجيل الخروج", false) { logout() })

        root.addView(Ui.section(this, "الصور"))
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        root.addView(list)

        val sv = ScrollView(this)
        sv.setBackgroundColor(Ui.BG)
        sv.addView(root)
        setContentView(sv)
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("تسجيل الخروج")
            .setMessage("ستتوقف الحماية على هذا الهاتف حتى تسجّل الدخول من جديد.")
            .setPositiveButton("خروج") { _, _ ->
                Watchdog.setEnabled(this, false)
                stopService(Intent(this, GuardService::class.java))
                Auth.logout(this)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun shareInvite() {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(
            Intent.EXTRA_TEXT,
            "جرّب تطبيق حارس الهاتف، يصوّر من يحاول فتح هاتفك برمز خاطئ ويرفع الصورة إلى حسابك:\n" +
                Invite.LINK + "\nرمز دعوتي: " + Invite.myCode(this) + "\nثبّت التطبيق ثم أنشئ حسابًا ببريدك وكلمة مرور."
        )
        startActivity(Intent.createChooser(i, "مشاركة"))
    }

    private fun showCount() {
        val ctx = applicationContext
        Thread {
            val n = Invite.count(ctx)
            runOnUiThread {
                inviteInfo.text = "رمز دعوتك: " + Invite.myCode(ctx) + "\nعدد من سجّلوا برمزك: " + n
            }
        }.start()
    }

    private fun claimCode() {
        val ctx = applicationContext
        val code = inviterBox.text.toString()
        Thread {
            val r = Invite.claim(ctx, code)
            runOnUiThread {
                Toast.makeText(this, r, Toast.LENGTH_LONG).show()
                if (r.startsWith("تم")) inviterBox.setText("")
            }
        }.start()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(About.TITLE)
            .setMessage(About.TEXT)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun askAdmin() {
        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp())
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "لالتقاط صورة عند إدخال رمز قفل خاطئ")
        startActivity(i)
    }

    private fun start() {
        val need = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 23 && need.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(need.toTypedArray(), 1)
        } else {
            launch()
        }
    }

    private fun launch() {
        Watchdog.setEnabled(this, true)
        Watchdog.schedule(applicationContext)
        val si = Intent(this, GuardService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(si) else startService(si)
        keepAlive()
        refresh()
    }

    private fun keepAlive() {
        try {
            if (Build.VERSION.SDK_INT < 23) return
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) return
            val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            i.data = android.net.Uri.parse("package:" + packageName)
            startActivity(i)
        } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (rc == 2) {
            if (Watchdog.enabled(this)) launch() else refresh()
            return
        }
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launch()
    }

    private fun refresh() {
        if (!::status.isInitialized) return
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = dpm.isAdminActive(comp())
        val on = GuardService.instance != null
        status.text = "صلاحية المراقبة: " + (if (admin) "مفعّلة" else "غير مفعّلة") +
            "\nالحماية: " + (if (on) "تعمل" else "متوقفة") +
            "\nالموقع: " + LocationHelper.status(this) +
            "\nصور بانتظار الرفع: " + Uploader.pending(this) +
            "\n\nالسجل:\n" + PhotoStore.lastLog(this)
        val ctx = applicationContext
        Thread {
            val t = Auth.token(ctx)
            val cloud = if (t.isBlank()) null else CloudStore.list(ctx, t)
            runOnUiThread { showPhotos(cloud) }
        }.start()
    }

    private var mailBtn: android.widget.Button? = null

    private fun mailLabel() = "تنبيه البريد: " + (if (Mailer.enabled(this)) "مفعّل" else "متوقف")

    private fun toggleMail() {
        Mailer.setEnabled(this, !Mailer.enabled(this))
        mailBtn?.text = mailLabel()
    }

    private fun fixLocation() {
        if (!LocationHelper.granted(this)) {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2)
            }
            return
        }
        if (!LocationHelper.deviceOn(this)) {
            try { startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) {}
            return
        }
        Toast.makeText(this, "الموقع يعمل", Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(u: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u))) } catch (_: Exception) {}
    }

    private fun addPhoto(bytes: ByteArray?, path: String?, time: Long, lat: Double? = null, lng: Double? = null) {
        val o = BitmapFactory.Options()
        o.inSampleSize = 2
        val bmp = if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o) else BitmapFactory.decodeFile(path, o)
        if (bmp == null) return
        val label = Ui.text(this, SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.US).format(Date(time)), Ui.MUTED, 13f, 20)
        list.addView(label)
        val iv = ImageView(this)
        iv.setImageBitmap(bmp)
        iv.adjustViewBounds = true
        iv.setPadding(0, 8, 0, 0)
        list.addView(iv)
        if (lat != null && lng != null) {
            val m = Ui.text(this, "عرض موقع الهاتف على الخريطة", Ui.ACCENT, 14f, 8)
            m.setOnClickListener { openUrl(LocationHelper.mapLink(lat, lng)) }
            list.addView(m)
        }
    }

    private fun showPhotos(cloud: List<CloudStore.Shot>?) {
        list.removeAllViews()
        if (cloud != null) {
            if (cloud.isEmpty()) list.addView(Ui.text(this, "لا توجد صور في حسابك بعد", Ui.MUTED, 14f, 8))
            for (p in cloud) addPhoto(p.bytes, null, p.time, p.lat, p.lng)
        } else {
            list.addView(Ui.text(this, "تعذّر الاتصال بالحساب، هذه الصور المحفوظة في الهاتف:", Ui.MUTED, 14f, 8))
            PhotoStore.photos(this).take(10).forEach {
                val l = PhotoStore.readLoc(it)
                addPhoto(null, it.path, it.lastModified(), l?.get(0), l?.get(1))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Updater.check(this)
        if (!::status.isInitialized) return
        Watchdog.ensureRunning(applicationContext)
        refresh()
        Uploader.flush(applicationContext)
        showCount()
    }
}

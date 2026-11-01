package com.haris.guard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView

class LoginActivity : Activity() {
    private lateinit var email: EditText
    private lateinit var pass: EditText
    private lateinit var msg: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = Ui.page(this)
        root.addView(Ui.title(this, "حارس الهاتف"))
        root.addView(Ui.text(this, "سجّل الدخول لتصلك صورة من يحاول فتح هاتفك برمز خاطئ، وتراها من أي هاتف.", Ui.MUTED, 14f, 12))
        email = Ui.input(this, "البريد الإلكتروني", false, true)
        pass = Ui.input(this, "كلمة المرور (6 أحرف أو أكثر)", true, false)
        root.addView(email)
        root.addView(pass)
        root.addView(Ui.button(this, "تسجيل الدخول", true) { go(false) })
        root.addView(Ui.button(this, "إنشاء حساب جديد", false) { go(true) })
        msg = Ui.text(this, "", Ui.MUTED, 14f, 16)
        root.addView(msg)
        val sv = ScrollView(this)
        sv.setBackgroundColor(Ui.BG)
        sv.isFillViewport = true
        sv.addView(root)
        setContentView(sv)
    }

    private fun go(create: Boolean) {
        val e = email.text.toString().trim()
        val p = pass.text.toString()
        if (e.isBlank() || p.isBlank()) {
            msg.text = "اكتب البريد وكلمة المرور"
            return
        }
        msg.text = "جارٍ الاتصال..."
        val ctx = applicationContext
        Thread {
            val r = if (create) Auth.signUp(ctx, e, p) else Auth.signIn(ctx, e, p)
            runOnUiThread {
                if (r == "ok") {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    msg.text = r
                }
            }
        }.start()
    }
}

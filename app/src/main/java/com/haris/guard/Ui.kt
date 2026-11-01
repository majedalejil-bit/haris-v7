package com.haris.guard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    val BG = 0xFF0B1220.toInt()
    val CARD = 0xFF182235.toInt()
    val ACCENT = 0xFF3B82F6.toInt()
    val TEXT = 0xFFFFFFFF.toInt()
    val MUTED = 0xFF94A3B8.toInt()

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Float): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radius
        return g
    }

    private fun lp(c: Context, top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(c, top)
        return p
    }

    fun page(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.layoutDirection = View.LAYOUT_DIRECTION_RTL
        l.setPadding(dp(c, 20), dp(c, 48), dp(c, 20), dp(c, 32))
        l.setBackgroundColor(BG)
        return l
    }

    fun text(c: Context, t: String, color: Int, size: Float, top: Int = 8): TextView {
        val v = TextView(c)
        v.text = t
        v.setTextColor(color)
        v.textSize = size
        v.layoutParams = lp(c, top)
        return v
    }

    fun title(c: Context, t: String): TextView {
        val v = text(c, t, TEXT, 26f, 0)
        v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    fun section(c: Context, t: String): TextView {
        val v = text(c, t, TEXT, 18f, 28)
        v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    fun button(c: Context, t: String, primary: Boolean, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = t
        b.isAllCaps = false
        b.setTextColor(TEXT)
        b.textSize = 15f
        b.background = shape(if (primary) ACCENT else CARD, dp(c, 14).toFloat())
        b.layoutParams = lp(c, 12)
        b.setOnClickListener { onClick() }
        return b
    }

    fun input(c: Context, hint: String, password: Boolean, email: Boolean): EditText {
        val e = EditText(c)
        e.hint = hint
        e.setTextColor(TEXT)
        e.setHintTextColor(MUTED)
        e.setSingleLine(true)
        e.textDirection = View.TEXT_DIRECTION_LTR
        e.background = shape(CARD, dp(c, 12).toFloat())
        e.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        e.inputType = when {
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            else -> InputType.TYPE_CLASS_TEXT
        }
        e.layoutParams = lp(c, 12)
        return e
    }
}

package com.haris.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

object Watchdog {
    private const val INTERVAL = 15 * 60 * 1000L

    private fun prefs(c: Context) = c.getSharedPreferences("guard", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = prefs(c).getBoolean("enabled", false)

    fun setEnabled(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("enabled", v).apply()
    }

    private fun pi(c: Context, code: Int): PendingIntent {
        val i = Intent(c, GuardReceiver::class.java).setAction("com.haris.guard.WATCHDOG")
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
    }

    fun schedule(c: Context, delayMs: Long = 0L) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (delayMs == 0L) {
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + INTERVAL, INTERVAL, pi(c, 1))
        } else {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi(c, 2))
        }
    }

    fun ensureRunning(c: Context) {
        if (!enabled(c) || GuardService.instance != null) return
        try {
            val i = Intent(c, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        } catch (e: Exception) {
            PhotoStore.log(c, "تعذّر إعادة تشغيل الحماية: " + e.javaClass.simpleName)
            Alerts.protectionStopped(c)
        }
    }
}

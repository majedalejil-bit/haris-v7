package com.haris.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

// Feature: tells the owner when protection stopped, with one tap to restart it.
object Alerts {
    private const val CHANNEL = "guard_alert"

    fun protectionStopped(c: Context) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val b = if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "تنبيهات الحماية", NotificationManager.IMPORTANCE_HIGH))
                Notification.Builder(c, CHANNEL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(c)
            }
            val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            val pi = PendingIntent.getActivity(c, 7, open, flags)
            val n = b.setContentTitle("حماية الهاتف متوقفة")
                .setContentText("اضغط هنا لإعادة تشغيل الحماية")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(2, n)
        } catch (_: Exception) {}
    }

    fun clear(c: Context) {
        try {
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(2)
        } catch (_: Exception) {}
    }
}

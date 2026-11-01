package com.haris.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onPasswordFailed(context: Context, intent: Intent) {
        PhotoStore.log(context, "محاولة فاشلة")
        val s = GuardService.instance
        if (s != null) s.capture() else PhotoStore.log(context, "الخدمة غير شغالة")
    }
}

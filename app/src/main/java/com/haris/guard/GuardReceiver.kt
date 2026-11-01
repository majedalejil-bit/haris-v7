package com.haris.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GuardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Watchdog.schedule(context)
        Watchdog.ensureRunning(context)
    }
}

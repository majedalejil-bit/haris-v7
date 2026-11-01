package com.haris.guard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

// Feature: the phone's location attached to each photo.
object LocationHelper {

    fun granted(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun mapLink(lat: Double, lng: Double): String = "https://maps.google.com/?q=$lat,$lng"

    @SuppressLint("MissingPermission")
    fun fetch(c: Context, done: (Location?) -> Unit) {
        if (!granted(c)) { done(null); return }
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { done(null); return }

        var best: Location? = null
        try {
            for (p in lm.getProviders(true)) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null }
                if (l != null && (best == null || l.time > best!!.time)) best = l
            }
        } catch (_: Exception) {}

        val b = best
        if (b != null && System.currentTimeMillis() - b.time < 2 * 60 * 1000L) { done(b); return }

        val main = Handler(Looper.getMainLooper())
        main.post { Request(lm, best, done, main).start() }
    }

    private class Request(
        val lm: LocationManager,
        val fallback: Location?,
        val done: (Location?) -> Unit,
        val main: Handler
    ) : LocationListener {
        private var finished = false

        @SuppressLint("MissingPermission")
        fun start() {
            var asked = false
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 0L, 0f, this, Looper.getMainLooper())
                        asked = true
                    }
                } catch (_: Exception) {}
            }
            if (!asked) { finish(null); return }
            main.postDelayed({ finish(null) }, 20000)
        }

        private fun finish(l: Location?) {
            if (finished) return
            finished = true
            try { lm.removeUpdates(this) } catch (_: Exception) {}
            done(l ?: fallback)
        }

        override fun onLocationChanged(location: Location) { finish(location) }
        @Deprecated("old API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}

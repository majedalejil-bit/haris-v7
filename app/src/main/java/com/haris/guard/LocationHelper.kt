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

    @Volatile private var cached: Location? = null
    private var tracker: Tracker? = null

    fun granted(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun deviceOn(c: Context): Boolean {
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    fun status(c: Context): String = when {
        !granted(c) -> "غير مسموح"
        !deviceOn(c) -> "مغلق في الهاتف"
        else -> "يعمل"
    }

    fun mapLink(lat: Double, lng: Double): String = "https://maps.google.com/?q=$lat,$lng"

    private fun better(a: Location?, b: Location?): Location? = when {
        a == null -> b
        b == null -> a
        b.time > a.time -> b
        else -> a
    }

    // Keeps a recent location while protection runs (network + passive: low battery).
    @SuppressLint("MissingPermission")
    fun startTracking(c: Context) {
        if (tracker != null || !granted(c)) return
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val t = Tracker()
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10 * 60 * 1000L, 50f, t, Looper.getMainLooper())
            }
            lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60 * 1000L, 0f, t, Looper.getMainLooper())
            tracker = t
        } catch (e: Exception) {
            PhotoStore.log(c, "تعذّر تتبع الموقع: " + e.javaClass.simpleName)
        }
    }

    fun stopTracking(c: Context) {
        val t = tracker ?: return
        try { (c.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(t) } catch (_: Exception) {}
        tracker = null
    }

    private class Tracker : LocationListener {
        override fun onLocationChanged(location: Location) { cached = better(cached, location) }
        @Deprecated("old API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(c: Context): Location? {
        if (!granted(c)) return cached
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return cached
        var best = cached
        try {
            for (p in lm.getProviders(false)) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null }
                best = better(best, l)
            }
        } catch (_: Exception) {}
        return best
    }

    @SuppressLint("MissingPermission")
    fun fetch(c: Context, done: (Location?) -> Unit) {
        if (!granted(c)) {
            PhotoStore.log(c, "الموقع: غير مسموح للتطبيق")
            done(null); return
        }
        if (!deviceOn(c)) PhotoStore.log(c, "الموقع: مغلق في الهاتف")
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { done(lastKnown(c)); return }

        val known = lastKnown(c)
        if (known != null && System.currentTimeMillis() - known.time < 2 * 60 * 1000L) { done(known); return }

        val main = Handler(Looper.getMainLooper())
        main.post { Request(c, lm, known, done, main).start() }
    }

    private class Request(
        val c: Context,
        val lm: LocationManager,
        val fallback: Location?,
        val done: (Location?) -> Unit,
        val main: Handler
    ) : LocationListener {
        private var finished = false

        @SuppressLint("MissingPermission")
        fun start() {
            var asked = false
            for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 0L, 0f, this, Looper.getMainLooper())
                        asked = true
                    }
                } catch (e: Exception) {
                    PhotoStore.log(c, "الموقع: " + e.javaClass.simpleName)
                }
            }
            if (!asked) { finish(null); return }
            main.postDelayed({ finish(null) }, 25000)
        }

        private fun finish(l: Location?) {
            if (finished) return
            finished = true
            try { lm.removeUpdates(this) } catch (_: Exception) {}
            val r = l ?: fallback
            if (r == null) PhotoStore.log(c, "الموقع: لم يتوفر خلال 25 ثانية")
            if (l != null) cached = better(cached, l)
            done(r)
        }

        override fun onLocationChanged(location: Location) { finish(location) }
        @Deprecated("old API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
}

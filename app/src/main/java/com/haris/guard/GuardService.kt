package com.haris.guard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.util.Size
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.io.File

class GuardService : Service() {
    companion object {
        @Volatile var instance: GuardService? = null
    }

    private val thread = HandlerThread("cam").also { it.start() }
    private val handler = Handler(thread.looper)
    @Volatile private var busy = false
    private var gen = 0
    private var camRef: CameraDevice? = null

    private val tick = object : Runnable {
        override fun run() {
            Uploader.flush(applicationContext)
            handler.postDelayed(this, 60000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Watchdog.schedule(applicationContext)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val nb = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("guard", "Guard", NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, "guard")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        note = nb
            .setContentTitle("حارس الهاتف يعمل")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
        if (!goForeground()) {
            PhotoStore.log(this, "تعذّر تشغيل الحماية في الخلفية، افتح التطبيق")
            Alerts.protectionStopped(applicationContext)
            instance = null
            stopSelf()
            return
        }
        Alerts.clear(applicationContext)
        PhotoStore.log(this, "الحماية بدأت")
        handler.post(tick)
    }

    private var note: Notification? = null

    private fun goForeground(): Boolean {
        val n = note ?: return false
        if (Build.VERSION.SDK_INT < 30) {
            return try { startForeground(1, n); true } catch (_: Exception) { false }
        }
        val cam = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (LocationHelper.granted(this)) {
            try {
                startForeground(1, n, cam or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                return true
            } catch (_: Exception) {}
        }
        return try { startForeground(1, n, cam); true } catch (_: Exception) { false }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (instance != null) goForeground()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Watchdog.schedule(applicationContext, 5000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        instance = null
        Watchdog.schedule(applicationContext, 30000)
        super.onDestroy()
    }

    private fun saveShot(raw: ByteArray) {
        val ctx = applicationContext
        val t = System.currentTimeMillis()
        val wait = File(PhotoStore.dir(ctx), "wait_$t.jpg")
        try {
            wait.writeBytes(PhotoEnhance.process(raw, File(cacheDir, "raw_$t.jpg")))
            wait.setLastModified(t)
        } catch (e: Exception) {
            PhotoStore.log(ctx, "خطأ حفظ: $e")
            return
        }
        PhotoStore.log(ctx, "تم حفظ صورة")
        LocationHelper.fetch(ctx) { loc ->
            if (loc != null) PhotoStore.saveLoc(wait, loc.latitude, loc.longitude, loc.accuracy.toDouble())
            PhotoStore.ready(wait)
            Uploader.flush(ctx)
        }
    }

    private fun release() {
        try { camRef?.close() } catch (_: Exception) {}
        camRef = null
        busy = false
    }

    @SuppressLint("MissingPermission")
    fun capture() {
        if (busy) return
        busy = true
        gen++
        val myGen = gen
        handler.post {
            try {
                val cm = getSystemService(CAMERA_SERVICE) as CameraManager
                val id = cm.cameraIdList.first {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT
                }
                val ch = cm.getCameraCharacteristics(id)
                val slow = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.minByOrNull { it.upper }
                val comp = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                val size = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.JPEG)
                    ?.filter { it.width <= 1600 && it.height <= 1600 }
                    ?.maxByOrNull { it.width * it.height }
                    ?: Size(640, 480)
                val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
                var frames = 0
                reader.setOnImageAvailableListener({ r ->
                    val img = r.acquireLatestImage()
                    if (img != null) {
                        frames++
                        if (frames == 20 && myGen == gen) {
                            val b = img.planes[0].buffer
                            val bytes = ByteArray(b.remaining())
                            b.get(bytes)
                            img.close()
                            release()
                            saveShot(bytes)
                            return@setOnImageAvailableListener
                        }
                        img.close()
                    }
                }, handler)
                cm.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(c: CameraDevice) {
                        camRef = c
                        try {
                            val req = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            req.addTarget(reader.surface)
                            req.set(CaptureRequest.JPEG_ORIENTATION, 270)
                            if (slow != null) req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, slow)
                            if (comp != null) req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, comp.upper)
                            @Suppress("DEPRECATION")
                            c.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(s: CameraCaptureSession) {
                                    try { s.setRepeatingRequest(req.build(), null, handler) }
                                    catch (e: Exception) { PhotoStore.log(this@GuardService, "خطأ: $e"); release() }
                                }
                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    PhotoStore.log(this@GuardService, "فشل إعداد الكاميرا")
                                    release()
                                }
                            }, handler)
                        } catch (e: Exception) {
                            PhotoStore.log(this@GuardService, "خطأ: $e")
                            release()
                        }
                    }
                    override fun onDisconnected(c: CameraDevice) { release() }
                    override fun onError(c: CameraDevice, e: Int) {
                        PhotoStore.log(this@GuardService, "خطأ كاميرا $e")
                        release()
                    }
                }, handler)
                handler.postDelayed({
                    if (busy && myGen == gen) {
                        PhotoStore.log(this@GuardService, "انتهت المهلة بدون صورة")
                        release()
                    }
                }, 12000)
            } catch (e: Exception) {
                PhotoStore.log(this@GuardService, "خطأ: $e")
                release()
            }
        }
    }
}

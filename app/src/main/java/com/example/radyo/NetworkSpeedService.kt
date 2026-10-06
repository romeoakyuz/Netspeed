package com.example.radyo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Icon
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale

class NetworkSpeedService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var txtWidgetSpeed: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var lastRxBytes: Long = 0
    private var lastTime: Long = 0
    private var lastOrientation: Int = Configuration.ORIENTATION_UNDEFINED
    private lateinit var runnable: Runnable

    companion object { const val CHANNEL_ID = "NetSpeedChannel" }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTime = System.currentTimeMillis()
        lastOrientation = resources.configuration.orientation

        startForegroundServiceWithBlankNotification()
        createFloatingWidget()
        startSpeedCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_UPDATE_SETTINGS") updateWidgetParams()
        return START_STICKY 
    }

    private fun startForegroundServiceWithBlankNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "NetSpeed", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID).setContentTitle("NetSpeed Başlatılıyor...").setSmallIcon(R.drawable.ic_logo).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("NetSpeed Başlatılıyor...").setSmallIcon(R.drawable.ic_logo).build()
        }
        startForeground(1, notification)
    }

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.widget_floating, null)
        txtWidgetSpeed = floatingView.findViewById(R.id.txtWidgetSpeed)
        
        val params = getLayoutParamsFromSettings()
        windowManager.addView(floatingView, params)
    }

    private fun updateWidgetParams() {
        try {
            if (::floatingView.isInitialized) {
                val params = getLayoutParamsFromSettings()
                windowManager.updateViewLayout(floatingView, params)
            }
        } catch (_: Exception) {}
    }

    private fun getLayoutParamsFromSettings(): WindowManager.LayoutParams {
        val prefs = getSharedPreferences("NetSpeedPrefs", MODE_PRIVATE)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val x = if (isLandscape) prefs.getInt("pos_x_land", 500) else prefs.getInt("pos_x_port", 240)
        val y = if (isLandscape) prefs.getInt("pos_y_land", 10) else prefs.getInt("pos_y_port", 10)
        val touchPass = prefs.getBoolean("touch_pass", true)

        txtWidgetSpeed.textSize = prefs.getFloat("text_size", 11.0f)

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (touchPass) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            flags, PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = x
        params.y = y

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return params
    }

    private fun startSpeedCheck() {
        runnable = object : Runnable {
            override fun run() {
                val currentOrientation = resources.configuration.orientation
                if (currentOrientation != lastOrientation) {
                    lastOrientation = currentOrientation
                    updateWidgetParams()
                }

                val currentRxBytes = TrafficStats.getTotalRxBytes()
                val currentTime = System.currentTimeMillis()
                val timeElapsed = (currentTime - lastTime) / 1000.0

                if (timeElapsed > 0) {
                    val bytesPerSec = (currentRxBytes - lastRxBytes) / timeElapsed
                    val (value, unit) = formatSpeedPartsSpeedtest(bytesPerSec)
                    
                    txtWidgetSpeed.text = "$value $unit"
                    updateNotificationDynamicIcon(value, unit)
                }

                lastRxBytes = currentRxBytes
                lastTime = currentTime
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    // SPEEDTEST FORMATI: Mbps (Megabit/s) ve Kbps (Kilobit/s) 
    private fun formatSpeedPartsSpeedtest(bytesPerSec: Double): Pair<String, String> {
        val bitsPerSec = bytesPerSec * 8.0 // Byte'ı bit'e çeviriyoruz
        
        return when {
            bitsPerSec >= 1_000_000 -> Pair(String.format(Locale.US, "%.2f", bitsPerSec / 1_000_000), "Mbps")
            bitsPerSec >= 1_000 -> Pair(String.format(Locale.US, "%.1f", bitsPerSec / 1_000), "Kbps")
            else -> Pair(String.format(Locale.US, "%d", bitsPerSec.toInt()), "bps")
        }
    }

    private fun updateNotificationDynamicIcon(value: String, unit: String) {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        paint.textSize = 55f
        canvas.drawText(value, 64f, 65f, paint)
        paint.textSize = 35f
        canvas.drawText(unit, 64f, 110f, paint)
        
        val dynamicIcon = Icon.createWithBitmap(bitmap)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("NetSpeed: $value $unit")
                .setSmallIcon(dynamicIcon)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("NetSpeed: $value $unit")
                .setSmallIcon(dynamicIcon)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}

package com.example.radyo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
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
    private lateinit var runnable: Runnable

    companion object { const val CHANNEL_ID = "NetSpeedChannel" }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTime = System.currentTimeMillis()

        startForegroundServiceWithNotification()
        createFloatingWidget()
        startSpeedCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_UPDATE_SETTINGS") updateWidgetParams()
        return START_STICKY // Xiaomi cihazlarda RAM temizlendiğinde servisin yeniden uyanmasını sağlar
    }

    private fun startForegroundServiceWithNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "NetSpeed", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID).setContentTitle("NetSpeed Çalışıyor").setSmallIcon(R.drawable.ic_logo).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("NetSpeed Çalışıyor").setSmallIcon(R.drawable.ic_logo).build()
        }
        startForeground(1, notification)
    }

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.widget_floating, null)
        txtWidgetSpeed = floatingView.findViewById(R.id.txtWidgetSpeed)

        val prefs = getSharedPreferences("NetSpeedPrefs", MODE_PRIVATE)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val x = if (isLandscape) prefs.getInt("pos_x_land", 240) else prefs.getInt("pos_x_port", 240)
        val y = if (isLandscape) prefs.getInt("pos_y_land", 10) else prefs.getInt("pos_y_port", 10)
        val touchPass = prefs.getBoolean("touch_pass", true)

        txtWidgetSpeed.textSize = prefs.getFloat("text_size", 11.0f)

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (touchPass) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            flags, PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = x
        params.y = y
        windowManager.addView(floatingView, params)
    }

    private fun updateWidgetParams() {
        try {
            val prefs = getSharedPreferences("NetSpeedPrefs", MODE_PRIVATE)
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            
            val params = floatingView.layoutParams as WindowManager.LayoutParams
            params.x = if (isLandscape) prefs.getInt("pos_x_land", 240) else prefs.getInt("pos_x_port", 240)
            params.y = if (isLandscape) prefs.getInt("pos_y_land", 10) else prefs.getInt("pos_y_port", 10)
            txtWidgetSpeed.textSize = prefs.getFloat("text_size", 11.0f)
            
            if (prefs.getBoolean("touch_pass", true)) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            windowManager.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
    }

    private fun startSpeedCheck() {
        runnable = object : Runnable {
            override fun run() {
                val currentRxBytes = TrafficStats.getTotalRxBytes()
                val currentTime = System.currentTimeMillis()
                val timeElapsed = (currentTime - lastTime) / 1000.0

                if (timeElapsed > 0) {
                    val bps = ((currentRxBytes - lastRxBytes) * 8) / timeElapsed
                    val speedText = formatSpeed(bps)
                    txtWidgetSpeed.text = speedText
                    updateNotificationSpeed(speedText)
                }

                lastRxBytes = currentRxBytes
                lastTime = currentTime
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    private fun formatSpeed(bps: Double): String {
        return when {
            bps >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", bps / 1_000_000)
            bps >= 1_000 -> String.format(Locale.US, "%.1f Kbps", bps / 1_000)
            else -> "0 Kbps"
        }
    }

    private fun updateNotificationSpeed(speed: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID).setContentTitle("Hız: $speed").setSmallIcon(R.drawable.ic_logo).setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("Hız: $speed").setSmallIcon(R.drawable.ic_logo).setOngoing(true).build()
        }
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}

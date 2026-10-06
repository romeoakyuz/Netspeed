package com.example.radyo

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var txtOverlayStatus: TextView
    private lateinit var txtNotifStatus: TextView
    private lateinit var txtBatteryStatus: TextView
    private lateinit var txtAutoStatus: TextView
    private lateinit var btnGrantAuto: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        txtOverlayStatus = findViewById(R.id.txtOverlayStatus)
        txtNotifStatus = findViewById(R.id.txtNotifStatus)
        txtBatteryStatus = findViewById(R.id.txtBatteryStatus)
        txtAutoStatus = findViewById(R.id.txtAutoStatus)
        btnGrantAuto = findViewById(R.id.btnGrantAuto)

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        findViewById<Button>(R.id.btnGrantNotif).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            } else {
                Toast.makeText(this, "Bu sürümde bildirim izni otomatik verilir.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnGrantBattery).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Pil ayarları açılamadı.", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantAuto.setOnClickListener {
            try {
                val intent = Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                }
                startActivity(intent)
                // Kullanıcı tıkladığında yeşil tik vererek onaylandı hissi oluşturalım
                txtAutoStatus.text = "✅"
                btnGrantAuto.text = "Ayarlandı"
            } catch (e: Exception) {
                Toast.makeText(this, "Güvenlik merkezi açılamadı, manuel kontrol edin.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatusUI()
    }

    private fun updatePermissionStatusUI() {
        if (Settings.canDrawOverlays(this)) {
            txtOverlayStatus.text = "✅"
        } else {
            txtOverlayStatus.text = "❌"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                txtNotifStatus.text = "✅"
            } else {
                txtNotifStatus.text = "❌"
            }
        } else {
            txtNotifStatus.text = "✅"
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            txtBatteryStatus.text = "✅"
        } else {
            txtBatteryStatus.text = "❌"
        }
    }
}

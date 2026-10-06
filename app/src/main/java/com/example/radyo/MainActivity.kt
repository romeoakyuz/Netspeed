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
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.radyo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isUpdating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        loadSettingsToUI()
        setupListeners()
        setupPermissionButtons()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun setupPermissionButtons() {
        binding.btnBattery.setOnClickListener {
            val intent = Intent()
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Pil optimizasyonu zaten kapalı!", Toast.LENGTH_SHORT).show()
            } else {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        binding.btnAutoStart.setOnClickListener {
            try {
                val intent = Intent()
                intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                startActivity(intent)
                Toast.makeText(this, "Açılan listeden NetSpeed'i bulup aktifleştirin.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Cihazınızda bu menü bulunamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSettingsToUI() {
        val prefs = getSharedPreferences("NetSpeedPrefs", MODE_PRIVATE)
        isUpdating = true
        
        binding.etPortX.setText(prefs.getInt("pos_x_port", 240).toString())
        binding.etPortY.setText(prefs.getInt("pos_y_port", 10).toString())
        binding.etLandX.setText(prefs.getInt("pos_x_land", 500).toString())
        binding.etLandY.setText(prefs.getInt("pos_y_land", 10).toString())

        binding.seekBarPortX.progress = prefs.getInt("pos_x_port", 240)
        binding.seekBarPortY.progress = prefs.getInt("pos_y_port", 10)
        binding.seekBarLandX.progress = prefs.getInt("pos_x_land", 500)
        binding.seekBarLandY.progress = prefs.getInt("pos_y_land", 10)

        val textSize = prefs.getFloat("text_size", 11.0f)
        binding.seekBarTextSize.progress = textSize.toInt()
        binding.lblTextSize.text = "Metin Boyutu: $textSize sp"
        
        binding.switchTouchThrough.isChecked = prefs.getBoolean("touch_pass", true)
        binding.switchWidgetToggle.isChecked = prefs.getBoolean("is_active", true)
        isUpdating = false
    }

    private fun setupListeners() {
        val prefs = getSharedPreferences("NetSpeedPrefs", MODE_PRIVATE)

        binding.switchWidgetToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_active", isChecked).apply()
            val serviceIntent = Intent(this, NetworkSpeedService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
            } else {
                stopService(serviceIntent)
            }
        }

        binding.switchTouchThrough.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("touch_pass", isChecked).apply()
            sendUpdateBroadcast()
        }

        binding.seekBarTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val fProgress = progress.toFloat()
                binding.lblTextSize.text = "Metin Boyutu: $fProgress sp"
                prefs.edit().putFloat("text_size", fProgress).apply()
                sendUpdateBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        setupInputSync(binding.etPortX, binding.seekBarPortX, "pos_x_port")
        setupInputSync(binding.etPortY, binding.seekBarPortY, "pos_y_port")
        setupInputSync(binding.etLandX, binding.seekBarLandX, "pos_x_land")
        setupInputSync(binding.etLandY, binding.seekBarLandY, "pos_y_land")
    }

    private fun setupInputSync(editText: EditText, seekBar: SeekBar, prefKey: String) {
        val prefs = getSharedPreferences("NetSpeedPrefs", MODE_PRIVATE)
        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdating) return
                val value = s.toString().toIntOrNull() ?: return
                isUpdating = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (value in seekBar.min..seekBar.max) seekBar.progress = value
                }
                prefs.edit().putInt(prefKey, value).apply()
                sendUpdateBroadcast()
                isUpdating = false
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isUpdating) return
                isUpdating = true
                editText.setText(progress.toString())
                editText.setSelection(editText.text.length)
                prefs.edit().putInt(prefKey, progress).apply()
                sendUpdateBroadcast()
                isUpdating = false
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun sendUpdateBroadcast() {
        startService(Intent(this, NetworkSpeedService::class.java).apply { action = "ACTION_UPDATE_SETTINGS" })
    }
}

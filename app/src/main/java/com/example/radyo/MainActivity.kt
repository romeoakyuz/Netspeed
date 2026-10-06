package com.example.radyo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.radyo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isUpdating = false // Sonsuz döngüyü engellemek için

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        loadSettingsToUI()
        setupListeners()
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

    private fun loadSettingsToUI() {
        val prefs = getSharedPreferences("NetSpeedPrefs", MODE_PRIVATE)
        
        isUpdating = true
        val portX = prefs.getInt("pos_x_port", 240)
        val portY = prefs.getInt("pos_y_port", 10)
        val landX = prefs.getInt("pos_x_land", 500)
        val landY = prefs.getInt("pos_y_land", 10)
        
        binding.etPortX.setText(portX.toString())
        binding.etPortY.setText(portY.toString())
        binding.etLandX.setText(landX.toString())
        binding.etLandY.setText(landY.toString())

        binding.seekBarPortX.progress = portX
        binding.seekBarPortY.progress = portY
        binding.seekBarLandX.progress = landX
        binding.seekBarLandY.progress = landY

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

        // Kutu ve Çubukları Senkronize Eden Yardımcı Fonksiyonlar
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
        val intent = Intent(this, NetworkSpeedService::class.java).apply { action = "ACTION_UPDATE_SETTINGS" }
        startService(intent)
    }
}

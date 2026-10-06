package com.example.radyo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.radyo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
        
        val portX = prefs.getInt("pos_x_port", 240)
        val portY = prefs.getInt("pos_y_port", 10)
        val landX = prefs.getInt("pos_x_land", 500)
        val landY = prefs.getInt("pos_y_land", 10)
        
        val textSize = prefs.getFloat("text_size", 11.0f)
        val touchPass = prefs.getBoolean("touch_pass", true)
        val isActive = prefs.getBoolean("is_active", true)

        binding.seekBarPortX.progress = portX
        binding.seekBarPortY.progress = portY
        binding.seekBarLandX.progress = landX
        binding.seekBarLandY.progress = landY
        
        binding.lblPortX.text = "X Eksen: $portX px"
        binding.lblPortY.text = "Y Eksen: $portY px"
        binding.lblLandX.text = "X Eksen: $landX px"
        binding.lblLandY.text = "Y Eksen: $landY px"

        binding.seekBarTextSize.progress = textSize.toInt()
        binding.switchTouchThrough.isChecked = touchPass
        binding.switchWidgetToggle.isChecked = isActive
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

        val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val editor = prefs.edit()

                when (seekBar?.id) {
                    R.id.seekBarPortX -> { binding.lblPortX.text = "X Eksen: $progress px"; editor.putInt("pos_x_port", progress) }
                    R.id.seekBarPortY -> { binding.lblPortY.text = "Y Eksen: $progress px"; editor.putInt("pos_y_port", progress) }
                    R.id.seekBarLandX -> { binding.lblLandX.text = "X Eksen: $progress px"; editor.putInt("pos_x_land", progress) }
                    R.id.seekBarLandY -> { binding.lblLandY.text = "Y Eksen: $progress px"; editor.putInt("pos_y_land", progress) }
                    R.id.seekBarTextSize -> { 
                        val fProgress = progress.toFloat()
                        binding.lblTextSize.text = "Metin Boyutu: $fProgress sp"
                        editor.putFloat("text_size", fProgress) 
                    }
                }
                editor.apply()
                sendUpdateBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.seekBarPortX.setOnSeekBarChangeListener(seekBarChangeListener)
        binding.seekBarPortY.setOnSeekBarChangeListener(seekBarChangeListener)
        binding.seekBarLandX.setOnSeekBarChangeListener(seekBarChangeListener)
        binding.seekBarLandY.setOnSeekBarChangeListener(seekBarChangeListener)
        binding.seekBarTextSize.setOnSeekBarChangeListener(seekBarChangeListener)

        binding.switchTouchThrough.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("touch_pass", isChecked).apply()
            sendUpdateBroadcast()
        }
    }

    private fun sendUpdateBroadcast() {
        val intent = Intent(this, NetworkSpeedService::class.java).apply { action = "ACTION_UPDATE_SETTINGS" }
        startService(intent)
    }
}

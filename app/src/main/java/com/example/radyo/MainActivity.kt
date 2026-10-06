package com.example.radyo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        val x = if (isLandscape) prefs.getInt("pos_x_land", 240) else prefs.getInt("pos_x_port", 240)
        val y = if (isLandscape) prefs.getInt("pos_y_land", 10) else prefs.getInt("pos_y_port", 10)
        val textSize = prefs.getFloat("text_size", 11.0f)
        val touchPass = prefs.getBoolean("touch_pass", true)
        val isActive = prefs.getBoolean("is_active", true)

        binding.etX.setText(x.toString())
        binding.etY.setText(y.toString())
        binding.seekBarX.progress = x
        binding.seekBarY.progress = y
        binding.seekBarTextSize.progress = textSize.toInt()
        binding.switchTouchThrough.isChecked = touchPass
        binding.switchWidgetToggle.isChecked = isActive

        binding.lblX.text = "X Eksen Kaydır: $x px"
        binding.lblY.text = "Y Eksen Kaydır: $y px"
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
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val editor = prefs.edit()

                when (seekBar?.id) {
                    R.id.seekBarX -> {
                        binding.etX.setText(progress.toString())
                        binding.lblX.text = "X Eksen Kaydır: $progress px"
                        if (isLandscape) editor.putInt("pos_x_land", progress) else editor.putInt("pos_x_port", progress)
                    }
                    R.id.seekBarY -> {
                        binding.etY.setText(progress.toString())
                        binding.lblY.text = "Y Eksen Kaydır: $progress px"
                        if (isLandscape) editor.putInt("pos_y_land", progress) else editor.putInt("pos_y_port", progress)
                    }
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

        binding.seekBarX.setOnSeekBarChangeListener(seekBarChangeListener)
        binding.seekBarY.setOnSeekBarChangeListener(seekBarChangeListener)
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

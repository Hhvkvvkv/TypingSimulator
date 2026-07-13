package com.yourname.typingsimulator

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etTargetText = findViewById<EditText>(R.id.etTargetText)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnEnableAccessibility = findViewById<Button>(R.id.btnEnableAccessibility)
        val switchDarkMode = findViewById<Switch>(R.id.switchDarkMode)

        // === تحميل النص المحفوظ ===
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        etTargetText.setText(sharedPref.getString("TEXT_TO_TYPE", "") ?: "")

        // === تحميل حالة الوضع الليلي ===
        val isDarkMode = sharedPref.getBoolean("DARK_MODE", false)
        switchDarkMode.isChecked = isDarkMode
        applyDarkMode(isDarkMode)

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("DARK_MODE", isChecked).apply()
            applyDarkMode(isChecked)
        }

        // === حفظ النص ===
        btnSave.setOnClickListener {
            val text = etTargetText.text.toString()
            if (text.isNotEmpty()) {
                sharedPref.edit().putString("TEXT_TO_TYPE", text).apply()
                Toast.makeText(this, R.string.text_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show()
            }
        }

        // === فتح إعدادات إمكانية الوصول ===
        btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "فعّل خدمة ${getString(R.string.app_name)} من الإعدادات", Toast.LENGTH_LONG).show()
        }

        // === تهيئة Shizuku بأمان — لو حصل أي خطأ، مش بيكراش ===
        try {
            ShizukuHelper.init(this)
            if (ShizukuHelper.isAvailable()) {
                ShizukuHelper.requestPermission()
            }
        } catch (_: Exception) {
            // Shizuku مش موجود — طبيعي، التطبيق شغال بدونه
        }
    }

    private fun applyDarkMode(enabled: Boolean) {
        val mode = if (enabled) AppCompatDelegate.MODE_NIGHT_YES
                   else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppCompatDelegate.setDefaultNightMode(mode)
        // نحدّث resources عشان التغيير يظهر فوراً
        delegate.applyDayNight()
    }
}

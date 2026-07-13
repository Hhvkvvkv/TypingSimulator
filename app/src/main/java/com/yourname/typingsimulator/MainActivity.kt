package com.yourname.typingsimulator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("DARK_MODE", false)) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            val etTargetText = findViewById<EditText>(R.id.etTargetText)
            val btnSave = findViewById<Button>(R.id.btnSave)
            val btnEnableAccessibility = findViewById<Button>(R.id.btnEnableAccessibility)
            val switchDarkMode = findViewById<SwitchCompat>(R.id.switchDarkMode)

            etTargetText.setText(prefs.getString("TEXT_TO_TYPE", "") ?: "")
            switchDarkMode.isChecked = prefs.getBoolean("DARK_MODE", false)

            switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("DARK_MODE", isChecked).apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                )
                recreate()
            }

            btnSave.setOnClickListener {
                val text = etTargetText.text.toString()
                if (text.isNotEmpty()) {
                    prefs.edit().putString("TEXT_TO_TYPE", text).apply()
                    Toast.makeText(this, R.string.text_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show()
                }
            }

            btnEnableAccessibility.setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "فعّل خدمة ${getString(R.string.app_name)} من الإعدادات",
                    Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "خطأ: ${e.message}")
            Toast.makeText(this, "حدث خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

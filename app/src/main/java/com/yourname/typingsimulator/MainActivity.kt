package com.yourname.typingsimulator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etTargetText = findViewById<EditText>(R.id.etTargetText)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnEnableAccessibility = findViewById<Button>(R.id.btnEnableAccessibility)

        // جلب النص المحفوظ مسبقاً إن وجد
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedText = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""
        etTargetText.setText(savedText)

        btnSave.setOnClickListener {
            val text = etTargetText.text.toString()
            if (text.isNotEmpty()) {
                val editor = sharedPref.edit()
                editor.putString("TEXT_TO_TYPE", text)
                editor.apply()
                Toast.makeText(this, R.string.text_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show()
            }
        }

        btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "الرجاء تفعيل خدمة ${getString(R.string.app_name)} من الإعدادات",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

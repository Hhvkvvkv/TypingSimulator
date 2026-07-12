package com.yourname.typingsimulator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import moe.shizuku.api.Shizuku

class MainActivity : AppCompatActivity() {

    companion object {
        const val SHIZUKU_REQUEST_CODE = 10001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etTargetText = findViewById<EditText>(R.id.etTargetText)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnEnableAccessibility = findViewById<Button>(R.id.btnEnableAccessibility)

        // جلب النص المحفوظ مسبقاً
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        etTargetText.setText(sharedPref.getString("TEXT_TO_TYPE", "") ?: "")

        btnSave.setOnClickListener {
            val text = etTargetText.text.toString()
            if (text.isNotEmpty()) {
                sharedPref.edit().putString("TEXT_TO_TYPE", text).apply()
                Toast.makeText(this, R.string.text_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show()
            }
        }

        btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "فعّل خدمة ${getString(R.string.app_name)} من الإعدادات", Toast.LENGTH_LONG).show()
        }

        // محاولة الاتصال بـ Shizuku
        tryRequestShizuku()
    }

    private fun tryRequestShizuku() {
        try {
            if (Shizuku.ping()) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Toast.makeText(this, "التطبيق يحتاج صلاحية Shizuku للكتابة المباشرة", Toast.LENGTH_LONG).show()
                }
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        } catch (e: Exception) {
            // Shizuku مش مثبت أو مش شغال - طبيعي
        }
    }

    // استقبال نتيجة طلب صلاحية Shizuku
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SHIZUKU_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == 0) {
            Toast.makeText(this, "✅ Shizuku جاهز!", Toast.LENGTH_SHORT).show()
        }
    }
}

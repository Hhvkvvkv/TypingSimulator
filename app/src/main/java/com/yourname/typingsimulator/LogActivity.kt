package com.yourname.typingsimulator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = android.widget.ScrollView(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        tvLog = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        val btnClear = Button(this).apply {
            text = "مسح السجل"
            setOnClickListener {
                EventLog.clear(this@LogActivity)
                refresh()
            }
        }

        val btnRefresh = Button(this).apply {
            text = "تحديث"
            setOnClickListener { refresh() }
        }

        layout.addView(btnRefresh)
        layout.addView(btnClear)
        layout.addView(tvLog)
        scroll.addView(layout)
        setContentView(scroll)
        title = "سجل الأخطاء والمهام"
        refresh()
    }

    private fun refresh() {
        val entries = EventLog.getAll(this)
        if (entries.isEmpty()) {
            tvLog.text = "لا توجد سجلات بعد.\nجرّب تشغيل الكتابة أولاً ثم عُد هنا."
        } else {
            tvLog.text = entries.reversed().joinToString("\n")
        }
    }
}

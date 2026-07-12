package com.yourname.typingsimulator

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat

class TypingService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    var isTyping = false

    companion object {
        const val CHANNEL_ID = "typing_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_TYPING = "com.yourname.typingsimulator.START_TYPING"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة المحاكاة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار خدمة محاكاة الكتابة"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val startTypingIntent = Intent(this, TypingService::class.java).apply {
            action = ACTION_START_TYPING
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, startTypingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("محاكاة الكتابة")
            .setContentText("اضغط لبدء محاكاة الكتابة")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_TYPING) {
            startTyping()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لا نحتاج معالجة الأحداث هنا
    }

    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun startTyping() {
        if (isTyping) {
            Toast.makeText(this, R.string.typing_in_progress, Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val textToType = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""

        if (textToType.isEmpty()) {
            Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show()
            return
        }

        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            typeTextLetterByLetter(focusedNode, textToType)
        } else {
            Toast.makeText(this, R.string.tap_text_field, Toast.LENGTH_SHORT).show()
        }
    }

    private fun typeTextLetterByLetter(node: AccessibilityNodeInfo, text: String) {
        isTyping = true
        var currentText = ""
        var index = 0

        val runnable = object : Runnable {
            override fun run() {
                if (index < text.length) {
                    currentText += text[index]

                    val arguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            currentText
                        )
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

                    index++
                    handler.postDelayed(this, 100)
                } else {
                    isTyping = false
                }
            }
        }
        handler.post(runnable)
    }
}

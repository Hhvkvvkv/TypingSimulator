package com.yourname.typingsimulator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat

class TypingService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isTyping = false
    private var typingText = ""

    companion object {
        const val CHANNEL_ID = "typing_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_TYPING = "com.yourname.typingsimulator.START_TYPING"
        const val TAG = "TypingService"
    }

    override fun onCreate() {
        super.onCreate()
        // تهيئة Shizoku
        ShizukuHelper.init(this)
        createNotificationChannel()
        showPersistentNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "خدمة المحاكاة", NotificationManager.IMPORTANCE_LOW).apply {
                description = "إشعار خدمة محاكاة الكتابة"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun showPersistentNotification() {
        val startIntent = Intent(this, TypingService::class.java).apply { action = ACTION_START_TYPING }
        val pendingIntent = PendingIntent.getService(this, 0, startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val shizokuStatus = if (ShizukuHelper.isAvailable()) "✅ Shizuku" else "ℹ️ عادي"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("محاكاة الكتابة")
            .setContentText("$shizokuStatus | اضغط لبدء الكتابة")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34)
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
            else startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_TYPING) startTyping()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { isTyping = false; handler.removeCallbacksAndMessages(null) }

    // ===================== المحرك الرئيسي =====================

    private fun startTyping() {
        if (isTyping) { Toast.makeText(this, "الكتابة جارية...", Toast.LENGTH_SHORT).show(); return }

        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        typingText = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""
        if (typingText.isEmpty()) { Toast.makeText(this, "لا يوجد نص محفوظ", Toast.LENGTH_SHORT).show(); return }

        // === الطريقة 1: Shizuku Direct Input (الأمتن) ===
        if (ShizukuHelper.isAvailable()) {
            useShizukuTyping()
            return
        }

        // === الطريقة 2: Accessibility Keyboard Scanning ===
        useKeyboardScanning()
    }

    // ===================== الطريقة 1: Shizuku =====================

    private fun useShizukuTyping() {
        isTyping = true
        Toast.makeText(this, "⚡ Shizuku: كتابة مباشرة...", Toast.LENGTH_SHORT).show()

        var index = 0
        val totalChars = typingText.length

        val runnable = object : Runnable {
            override fun run() {
                if (index >= totalChars || !isTyping) {
                    isTyping = false
                    Toast.makeText(this@TypingService, "✅ تمت كتابة $totalChars حرف!", Toast.LENGTH_SHORT).show()
                    return
                }

                val char = typingText[index].toString()
                if (ShizukuHelper.inputText(char)) {
                    index++
                    handler.postDelayed(this, (80..150).random().toLong())
                } else {
                    Log.e(TAG, "Shizuku فشل في: '$char'")
                    isTyping = false
                    Toast.makeText(this@TypingService, "⚠️ Shizuku فشل، استخدم Accessibility", Toast.LENGTH_SHORT).show()
                    useKeyboardScanning()
                }
            }
        }
        handler.post(runnable)
    }

    // ===================== الطريقة 2: Accessibility Keyboard =====================

    private fun useKeyboardScanning() {
        printAllKeyboardKeys()
        Toast.makeText(this, "🔍 مسح الكيبورد...", Toast.LENGTH_SHORT).show()

        isTyping = true
        var delay = 0L

        for (char in typingText) {
            val currentDelay = delay
            handler.postDelayed({
                if (!isTyping) return@postDelayed
                findAndClickKey(char.toString())
            }, currentDelay)
            delay += (150..350).random().toLong()
        }

        handler.postDelayed({
            isTyping = false
            Toast.makeText(this, "✅ تمت الكتابة!", Toast.LENGTH_SHORT).show()
        }, delay + 500)
    }

    private fun findAndClickKey(targetChar: String) {
        try {
            val imeRootNode = windows.find { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root ?: return
            val keyRect = searchForCharNode(imeRootNode, targetChar.lowercase())

            if (keyRect != null) {
                clickAtPosition(keyRect.centerX().toFloat(), keyRect.centerY().toFloat())
            } else {
                Log.e(TAG, "❌ '$targetChar' مش موجود")
            }
            imeRootNode.recycle()
        } catch (e: Exception) { Log.e(TAG, "خطأ", e) }
    }

    private fun searchForCharNode(node: AccessibilityNodeInfo?, targetChar: String): Rect? {
        if (node == null) return null
        val contentDesc = node.contentDescription?.toString()?.lowercase()
        val text = node.text?.toString()?.lowercase()
        if ((contentDesc != null && contentDesc.contains(targetChar)) ||
            (text != null && text.contains(targetChar))) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) return rect
        }
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            val result = searchForCharNode(childNode, targetChar)
            if (result != null) { childNode?.recycle(); return result }
            childNode?.recycle()
        }
        return null
    }

    private fun clickAtPosition(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build(), null, null)
    }

    private fun printAllKeyboardKeys() {
        try {
            val imeRootNode = windows.find { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root ?: return
            fun traverse(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val desc = node.contentDescription?.toString()
                val txt = node.text?.toString()
                if (desc != null || txt != null) Log.d("KeyboardKeys", "🔑 '$txt' | '$desc'")
                for (i in 0 until node.childCount) traverse(node.getChild(i))
            }
            traverse(imeRootNode)
            imeRootNode.recycle()
        } catch (e: Exception) { Log.e(TAG, "Print error", e) }
    }
}

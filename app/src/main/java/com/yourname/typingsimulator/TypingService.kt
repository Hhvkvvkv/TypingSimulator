package com.yourname.typingsimulator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.annotation.SuppressLint
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
        const val KEYCODE_DEL = 67
        const val KEYCODE_ENTER = 66
        const val KEYCODE_SPACE = 62
    }

    /**
     * ✅ كل دالة محمية بـ Throwable — أي خطأ (حتى Error) مش بيكراش الخدمة
     */
    override fun onCreate() {
        try {
            super.onCreate()
            ShizukuHelper.init(this)
            createNotificationChannel()
            showPersistentNotification()
        } catch (e: Throwable) {
            Log.e(TAG, "onCreate خطأ: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID, "خدمة المحاكاة", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "إشعار خدمة محاكاة الكتابة" }
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            } catch (e: Throwable) {
                Log.e(TAG, "createNotificationChannel خطأ: ${e.message}")
            }
        }
    }

    private fun showPersistentNotification() {
        try {
            val startIntent = Intent(this, TypingService::class.java).apply {
                action = ACTION_START_TYPING
            }
            val pendingIntent = PendingIntent.getService(
                this, 0, startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val shizukuStatus = if (ShizukuHelper.isAvailable()) "⚡ Shizuku" else "🔍 Accessibility"
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("محاكاة الكتابة")
                .setContentText("اضغط لبدء الكتابة | $shizukuStatus")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "showPersistentNotification خطأ: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent?.action == ACTION_START_TYPING) startTyping()
        } catch (e: Throwable) {
            Log.e(TAG, "onStartCommand خطأ: ${e.message}")
        }
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Throwable) {
            START_STICKY
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
    }

    // ===================================================================
    //  المحرك الرئيسي
    // ===================================================================

    private fun startTyping() {
        if (isTyping) {
            showToast("الكتابة جارية بالفعل...")
            return
        }
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        typingText = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""
        if (typingText.isEmpty()) {
            showToast("لا يوجد نص محفوظ")
            return
        }
        Log.d(TAG, "بدء الكتابة: ${typingText.length} حرف")
        if (ShizukuHelper.isAvailable()) {
            useShizukuTyping()
            return
        }
        showToast("🔍 مسح الكيبورد...")
        printAllKeyboardKeys()
        useAccessibilityTyping()
    }

    // ===================== استراتيجية 1: Shizuku =====================

    private fun useShizukuTyping() {
        isTyping = true
        showToast("⚡ Shizuku: كتابة مباشرة...")
        var index = 0
        val totalChars = typingText.length
        val runnable = object : Runnable {
            override fun run() {
                try {
                    if (index >= totalChars || !isTyping) {
                        isTyping = false
                        if (index >= totalChars) showToast("✅ تمت كتابة $totalChars حرف!")
                        return
                    }
                    val char = typingText[index].toString()
                    val success = when (char) {
                        "\n" -> ShizukuHelper.keyEvent(KEYCODE_ENTER)
                        " " -> ShizukuHelper.keyEvent(KEYCODE_SPACE)
                        else -> ShizukuHelper.inputText(char)
                    }
                    if (success) {
                        index++
                        val delay = if (char == " " || char == "\n") 120..250 else 60..150
                        handler.postDelayed(this, delay.random().toLong())
                    } else {
                        Log.w(TAG, "Shizuku فشل للحرف '$char' — ننتقل Accessibility")
                        isTyping = false
                        useAccessibilityTyping()
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "خطأ في Shizuku: ${e.message}")
                    isTyping = false
                    useAccessibilityTyping()
                }
            }
        }
        handler.post(runnable)
    }

    // ============= استراتيجية 2: Accessibility Keyboard Scan =============

    @SuppressLint("RestrictedApi")
    private fun useAccessibilityTyping() {
        printAllKeyboardKeys()
        isTyping = true
        showToast("🔤 كتابة عبر الكيبورد...")
        var delay = 0L
        var successCount = 0
        var failCount = 0
        for (char in typingText) {
            val currentDelay = delay
            val currentChar = char.toString()
            handler.postDelayed({
                try {
                    if (!isTyping) return@postDelayed
                    val found = findAndClickKey(currentChar)
                    if (found) {
                        successCount++
                    } else {
                        failCount++
                        if (ShizukuHelper.isAvailable()) {
                            try {
                                when (currentChar) {
                                    "\n" -> ShizukuHelper.keyEvent(KEYCODE_ENTER)
                                    " " -> ShizukuHelper.keyEvent(KEYCODE_SPACE)
                                    else -> ShizukuHelper.inputText(currentChar)
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "Shizuku fallback فشل: ${e.message}")
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "useAccessibilityTyping loop خطأ: ${e.message}")
                }
            }, currentDelay)
            delay += (180..400).random().toLong()
        }
        val totalDelay = delay + 1000
        handler.postDelayed({
            isTyping = false
            showToast("✅ تمت الكتابة! (نجاح: $successCount, فشل: $failCount)")
        }, totalDelay)
    }

    private fun findAndClickKey(targetChar: String): Boolean {
        return try {
            val allWindows = windows
            if (allWindows.isEmpty()) return false
            val imeRootNode = allWindows.find {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }?.root ?: return false
            val keyRect = searchForCharNode(imeRootNode, targetChar.lowercase())
            if (keyRect != null && keyRect.width() > 0 && keyRect.height() > 0) {
                Log.d(TAG, "📍 '$targetChar' في ${keyRect.toShortString()}")
                clickAtPosition(keyRect.centerX().toFloat(), keyRect.centerY().toFloat())
                imeRootNode.recycle()
                true
            } else {
                imeRootNode.recycle()
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "findAndClickKey خطأ: ${e.message}")
            false
        }
    }

    private fun searchForCharNode(node: AccessibilityNodeInfo?, targetChar: String): Rect? {
        if (node == null) return null
        return try {
            val contentDesc = node.contentDescription?.toString()?.lowercase()?.trim()
            val text = node.text?.toString()?.lowercase()?.trim()
            val nodeText = contentDesc ?: text
            if (nodeText != null && nodeText.isNotEmpty()) {
                val matches = nodeText == targetChar ||
                        nodeText.contains(targetChar) ||
                        targetChar.contains(nodeText)
                if (matches) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) return rect
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                val result = searchForCharNode(child, targetChar)
                if (result != null) {
                    child?.recycle()
                    return result
                }
                child?.recycle()
            }
            null
        } catch (e: Throwable) {
            Log.e(TAG, "searchForCharNode خطأ: ${e.message}")
            null
        }
    }

    private fun clickAtPosition(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (e: Throwable) {
            Log.e(TAG, "clickAtPosition خطأ: ${e.message}")
        }
    }

    // ===================== أدوات التشخيص =====================

    private fun printAllKeyboardKeys() {
        try {
            val allWindows = windows
            val imeRootNode = allWindows.find {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }?.root ?: return
            Log.d(TAG, "======= أزرار الكيبورد =======")
            fun traverse(node: AccessibilityNodeInfo?, depth: Int = 0) {
                if (node == null) return
                val desc = node.contentDescription?.toString()
                val txt = node.text?.toString()
                val cls = node.className
                val rect = Rect().also { node.getBoundsInScreen(it) }
                if (desc != null || txt != null) {
                    Log.d(TAG, "${"  ".repeat(depth)}🔑 '$txt' | '$desc' | $cls | $rect")
                }
                for (i in 0 until node.childCount) traverse(node.getChild(i), depth + 1)
            }
            traverse(imeRootNode)
            Log.d(TAG, "================================")
            imeRootNode.recycle()
        } catch (e: Throwable) {
            Log.e(TAG, "printAllKeyboardKeys خطأ: ${e.message}")
        }
    }

    private fun showToast(msg: String) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Log.e(TAG, "showToast خطأ: ${e.message}")
        }
    }
}

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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import moe.shizuku.api.Shizuku

class TypingService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isTyping = false
    private var typingText = ""

    // خريطة الكيبورد (للطريقة الثانية)
    private val keyboardKeysMap = mutableMapOf<String, Rect>()

    companion object {
        const val CHANNEL_ID = "typing_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_TYPING = "com.yourname.typingsimulator.START_TYPING"
        const val TAG = "TypingService"
        const val SHIZUKU_REQUEST_CODE = 10001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showPersistentNotification()
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

    private fun showPersistentNotification() {
        val startIntent = Intent(this, TypingService::class.java).apply {
            action = ACTION_START_TYPING
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("محاكاة الكتابة")
            .setContentText("اضغط لبدء الكتابة التلقائية")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_TYPING) {
            startTyping()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
    }

    // ======================== المحرك الرئيسي ========================

    private fun startTyping() {
        if (isTyping) {
            Toast.makeText(this, "الكتابة جارية بالفعل...", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        typingText = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""

        if (typingText.isEmpty()) {
            Toast.makeText(this, "لا يوجد نص محفوظ", Toast.LENGTH_SHORT).show()
            return
        }

        // === محاولة 1: Shizuku Direct Input (الأقوى والأسرع) ===
        if (tryShizukuInput()) {
            return // نجحت
        }

        // === محاولة 2: النقر على أزرار الكيبورد بإحداثياتها ===
        mapKeyboardElements()
        if (keyboardKeysMap.isNotEmpty()) {
            typeUsingKeyboardClicks()
            return
        }

        // === محاولة 3: SET_TEXT على الـ EditText ===
        typeUsingSetText()
    }

    // ======================== الطريقة 1: Shizuku ========================
    // تستخدم Shizuku API لتشغيل أمر 'input text' على مستوى النظام

    private fun tryShizukuInput(): Boolean {
        return try {
            if (Shizuku.ping()) {
                Toast.makeText(this, "تم اكتشاف Shizuku، جاري الكتابة المباشرة...", Toast.LENGTH_SHORT).show()

                // تقسيم النص لأجزاء صغيرة لتجنب مشاكل الأحرف الخاصة
                val sanitized = typingText
                    .replace("'", "'\\''")  // للهروب من Single Quotes
                
                // تشغيل أمر input text عبر Shizuku
                val process = Shizuku.newProcess(
                    arrayOf("sh", "-c", "input text '$sanitized'"),
                    null, null
                )
                
                // قراءة المخرجات للتأكد من النجاح
                val reader = process.inputStream.bufferedReader()
                val output = reader.readText()
                reader.close()
                process.waitFor()
                
                Log.d(TAG, "Shizuku input result: exitCode=${process.exitValue()}, output=$output")
                
                if (process.exitValue() == 0) {
                    Toast.makeText(this, "✅ تمت الكتابة via Shizuku!", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    Log.e(TAG, "Shizuku input failed: $output")
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku error", e)
            false
        }
    }

    // ======================== الطريقة 2: كيبورد سكان ========================

    private fun mapKeyboardElements() {
        keyboardKeysMap.clear()
        try {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    val root = window.root
                    extractKeysFromNode(root)
                    root?.recycle()
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Keyboard scan error", e)
        }
    }

    private fun extractKeysFromNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrEmpty() && text.length <= 2) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                keyboardKeysMap[text.lowercase()] = rect
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            extractKeysFromNode(child)
            child?.recycle()
        }
    }

    private fun typeUsingKeyboardClicks() {
        isTyping = true
        var index = 0
        val runnable = object : Runnable {
            override fun run() {
                if (index >= typingText.length || !isTyping) {
                    isTyping = false
                    Toast.makeText(this@TypingService, "✅ تمت الكتابة", Toast.LENGTH_SHORT).show()
                    return
                }
                val char = typingText[index].lowercase()
                val rect = keyboardKeysMap[char]
                if (rect != null) {
                    val path = Path().apply {
                        moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
                    }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                        .build()
                    dispatchGesture(gesture, null, null)
                    index++
                    handler.postDelayed(this, 150)
                } else {
                    index++
                    handler.postDelayed(this, 50)
                }
            }
        }
        handler.post(runnable)
    }

    // ======================== الطريقة 3: SET_TEXT ========================

    private fun typeUsingSetText() {
        val inputNode = findActiveInputNode() ?: run {
            Toast.makeText(this, "اضغط على مربع نص أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        isTyping = true
        var index = 0
        var currentText = ""

        val runnable = object : Runnable {
            private var currentNode: AccessibilityNodeInfo? = inputNode

            override fun run() {
                if (index >= typingText.length || !isTyping) {
                    isTyping = false
                    currentNode?.recycle()
                    currentNode = null
                    Toast.makeText(this@TypingService, "✅ تمت الكتابة", Toast.LENGTH_SHORT).show()
                    return
                }

                try {
                    currentText += typingText[index]

                    // تحديث العقدة إن أمكن
                    val fresh = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (fresh != null && fresh.isEditable) {
                        currentNode?.recycle()
                        currentNode = fresh
                    } else {
                        fresh?.recycle()
                    }

                    val target = currentNode
                    if (target != null) {
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                currentText
                            )
                        }
                        var ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        if (!ok) {
                            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Thread.sleep(50)
                            ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        }
                    }
                    index++
                    handler.postDelayed(this, 100)

                } catch (e: Exception) {
                    Log.e(TAG, "SET_TEXT error", e)
                    isTyping = false
                    currentNode?.recycle()
                    currentNode = null
                    Toast.makeText(this@TypingService, "❌ فشل: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        handler.post(runnable)
    }

    private fun findActiveInputNode(): AccessibilityNodeInfo? {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable && focused.isEnabled) return focused
        focused?.recycle()
        return searchForEditableNode(rootInActiveWindow)
    }

    private fun searchForEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isEditable && root.isEnabled && root.isVisibleToUser) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = searchForEditableNode(child)
            if (result != null) {
                child?.recycle(); return result
            }
            child?.recycle()
        }
        return null
    }
}

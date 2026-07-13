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

        // ثوابت KeyEvent للأندرويد
        const val KEYCODE_DEL = 67
        const val KEYCODE_ENTER = 66
        const val KEYCODE_SPACE = 62
    }

    override fun onCreate() {
        super.onCreate()
        ShizukuHelper.init(this)
        createNotificationChannel()
        showPersistentNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة المحاكاة",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "إشعار خدمة محاكاة الكتابة" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
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

        val shizukuStatus = if (ShizukuHelper.isAvailable()) "⚡ Shizuku" else "🔍 Accessibility"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("محاكاة الكتابة")
            .setContentText("اضغط لبدء الكتابة | $shizukuStatus")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "فشل عرض الإشعار: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_TYPING) {
            startTyping()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لا نحتاج معالجة الأحداث — نستخدم onClick من الإشعار
    }

    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
    }

    // ===================================================================
    //  المحرك الرئيسي — استراتيجيتان للكتابة
    // ===================================================================

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

        Log.d(TAG, "بدء الكتابة: ${typingText.length} حرف")

        // استراتيجية 1: Shizuku — الأسرع والأدق
        if (ShizukuHelper.isAvailable()) {
            useShizukuTyping()
            return
        }

        // استراتيجية 2: Accessibility — البحث عن الحروف في الكيبورد والنقر عليها
        Toast.makeText(this, "🔍 مسح الكيبورد...", Toast.LENGTH_SHORT).show()
        printAllKeyboardKeys()
        useAccessibilityTyping()
    }

    // ===================== استراتيجية 1: Shizuku =====================

    /**
     * Shizuku Direct Input — يكتب النص مباشرة على مستوى النظام
     * إذا فشل حرف معيّن، ننتقل تلقائياً لاستراتيجية Accessibility.
     */
    private fun useShizukuTyping() {
        if (!ShizukuHelper.isAvailable()) {
            useAccessibilityTyping()
            return
        }

        isTyping = true
        Toast.makeText(this, "⚡ Shizuku: كتابة مباشرة...", Toast.LENGTH_SHORT).show()

        var index = 0
        val totalChars = typingText.length

        val runnable = object : Runnable {
            override fun run() {
                if (index >= totalChars || !isTyping) {
                    isTyping = false
                    if (index >= totalChars) {
                        Toast.makeText(this@TypingService,
                            "✅ تمت كتابة $totalChars حرف!", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val char = typingText[index].toString()

                // للأحرف الخاصة نستخدم keyevent
                val success = when (char) {
                    "\n" -> ShizukuHelper.keyEvent(KEYCODE_ENTER)
                    " " -> ShizukuHelper.keyEvent(KEYCODE_SPACE)
                    else -> ShizukuHelper.inputText(char)
                }

                if (success) {
                    index++
                    // تأخير بشري عشوائي بين الحروف
                    val delay = if (char == " " || char == "\n") 120..250 else 60..150
                    handler.postDelayed(this, delay.random().toLong())
                } else {
                    Log.w(TAG, "Shizuku فشل للحرف '$char' — ننتقل Accessibility")
                    isTyping = false
                    useAccessibilityTyping()
                }
            }
        }
        handler.post(runnable)
    }

    // ============= استراتيجية 2: Accessibility Keyboard Scan =============

    /**
     * Accessibility Keyboard Typing — يبحث عن الحرف في شجرة الكيبورد
     * ويستخدم dispatchGesture للنقر على إحداثياته
     */
    private fun useAccessibilityTyping() {
        // نطبع جميع أزرار الكيبورد في Logcat أولاً للتشخيص
        printAllKeyboardKeys()

        isTyping = true
        Toast.makeText(this, "🔤 كتابة عبر الكيبورد...", Toast.LENGTH_SHORT).show()

        var delay = 0L
        var successCount = 0
        var failCount = 0

        for (char in typingText) {
            val currentDelay = delay
            val currentChar = char.toString()

            handler.postDelayed({
                if (!isTyping) return@postDelayed

                val found = findAndClickKey(currentChar)
                if (found) {
                    successCount++
                    Log.d(TAG, "✅ '$currentChar' تم النقر")
                } else {
                    failCount++
                    Log.w(TAG, "❌ '$currentChar' لم يوجد — نحاول Shizuku fallback")
                    // fallback: نحاول Shizuku
                    if (ShizukuHelper.isAvailable()) {
                        when (currentChar) {
                            "\n" -> ShizukuHelper.keyEvent(KEYCODE_ENTER)
                            " " -> ShizukuHelper.keyEvent(KEYCODE_SPACE)
                            else -> ShizukuHelper.inputText(currentChar)
                        }
                    }
                }
            }, currentDelay)

            // تأخير بشري عشوائي 180-400ms
            delay += (180..400).random().toLong()
        }

        // إنهاء الكتابة بعد انتهاء جميع الحروف
        val totalDelay = delay + 1000
        handler.postDelayed({
            isTyping = false
            Toast.makeText(this@TypingService,
                "✅ تمت الكتابة! (نجاح: $successCount, فشل: $failCount)",
                Toast.LENGTH_SHORT).show()
        }, totalDelay)
    }

    /**
     * البحث عن الحرف في نافذة الكيبورد وإرجاع إحداثياته
     * ثم النقر عليه باستخدام dispatchGesture
     */
    private fun findAndClickKey(targetChar: String): Boolean {
        return try {
            val allWindows = windows
            if (allWindows.isEmpty()) {
                Log.e(TAG, "لا توجد نوافذ!")
                return false
            }

            val imeRootNode = allWindows.find {
                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }?.root

            if (imeRootNode == null) {
                Log.e(TAG, "نافذة الكيبورد غير موجودة (هل الكيبورد مفتوح؟)")
                return false
            }

            // بحث متعمق عن الحرف
            val keyRect = searchForCharNode(imeRootNode, targetChar.lowercase())

            if (keyRect != null && keyRect.width() > 0 && keyRect.height() > 0) {
                Log.d(TAG, "📍 '$targetChar' في ${keyRect.toShortString()}")
                // تأخير قصير قبل النقر عشان الكيبورد يستقر
                try { Thread.sleep(30) } catch (_: InterruptedException) {}
                clickAtPosition(keyRect.centerX().toFloat(), keyRect.centerY().toFloat())
                imeRootNode.recycle()
                true
            } else {
                Log.w(TAG, "🔍 '$targetChar' لم يوجد في شجرة الكيبورد")
                imeRootNode.recycle()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "findAndClickKey خطأ: ${e.message}")
            false
        }
    }

    /**
     * دالة تتبع عكسي (Recursive) تبحث في كل أبناء العقدة
     * عن عقدة تحتوي على الحرف المطلوب في contentDescription أو text
     */
    private fun searchForCharNode(node: AccessibilityNodeInfo?, targetChar: String): Rect? {
        if (node == null) return null

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
                if (rect.width() > 0 && rect.height() > 0) {
                    return rect
                }
            }
        }

        // البحث في الأبناء
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = searchForCharNode(child, targetChar)
            if (result != null) {
                child?.recycle()
                return result
            }
            child?.recycle()
        }

        return null
    }

    /**
     * النقر على إحداثية محددة باستخدام dispatchGesture
     * — هذا يحاكي النقر البشري الحقيقي على الشاشة
     */
    private fun clickAtPosition(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = StrokeDescription(path, 0, 50) // 50ms نقرة
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            dispatchGesture(gesture, null, null)
            Log.d(TAG, "👆 نقرة في ($x, $y)")
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture فشل: ${e.message}")
        }
    }

    // ===================== أدوات التشخيص =====================

    /**
     * طباعة كل أزرار الكيبورد الموجودة حالياً في Logcat
     * — استخدمها لترى الـ ContentDescription الفعلية لكل زر في كيبوردك
     */
    private fun printAllKeyboardKeys() {
        try {
            val allWindows = windows
            if (allWindows.isEmpty()) return

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

                for (i in 0 until node.childCount) {
                    traverse(node.getChild(i), depth + 1)
                }
            }

            traverse(imeRootNode)
            Log.d(TAG, "================================")
            imeRootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "printAllKeyboardKeys خطأ: ${e.message}")
        }
    }
}

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
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat

class TypingService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isTyping = false
    private var typingText = ""
    private var useDarkNotification = false

    companion object {
        const val CHANNEL_ID = "typing_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_TYPING = "com.yourname.typingsimulator.START_TYPING"
        const val TAG = "TypingService"

        // ثوابت KeyEvent للأندرويد
        const val KEYCODE_DEL = 67
        const val KEYCODE_ENTER = 66
        const val KEYCODE_SPACE = 62
        const val KEYCODE_SHIFT_LEFT = 59
        const val KEYCODE_BACK = 4
    }

    override fun onCreate() {
        super.onCreate()
        ShizukuHelper.init(this)
        loadDarkModePref()
        createNotificationChannel()
        showPersistentNotification()
    }

    private fun loadDarkModePref() {
        useDarkNotification = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .getBoolean("DARK_MODE", false)
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

        // نستخدم priority عالية في الوضع العادي عشان الإشعار يفضل ظاهر
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("محاكاة الكتابة")
            .setContentText("اضغط لبدء الكتابة | $shizukuStatus")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            // Android 13+ لازم نستخدم notification بشكل مباشر
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
        // لا نحتاج معالجة الأحداث هنا — نستخدم onClick من الإشعار
    }

    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
    }

    // ===================================================================
    //  المحرك الرئيسي — ثلاث استراتيجيات للكتابة
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
     * ملاحظة: `input text` يدعم الأحرف اللاتينية والمسافات.
     * للأحرف العربية قد لا يعمل على بعض الأجهزة.
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
                    Toast.makeText(this@TypingService,
                        "✅ تمت كتابة $totalChars حرف!", Toast.LENGTH_SHORT).show()
                    return
                }

                val char = typingText[index].toString()

                // للأحرف الخاصة نستخدم keyevent
                when (char) {
                    "\n" -> ShizukuHelper.keyEvent(KEYCODE_ENTER)
                    " " -> ShizukuHelper.keyEvent(KEYCODE_SPACE)
                    else -> {
                        // محاولة كتابة الحرف عبر input text
                        val success = ShizukuHelper.inputText(char)
                        if (!success) {
                            Log.w(TAG, "Shizuku فشل للحرف '$char' — ننتقل Accessibility")
                            isTyping = false
                            useAccessibilityTyping()
                            return
                        }
                    }
                }

                index++
                // تأخير بشري عشوائي بين الحروف
                val delay = if (char == " " || char == "\n") 120..250 else 60..150
                handler.postDelayed(this, delay.random().toLong())
            }
        }
        handler.post(runnable)
    }

    // ============= استراتيجية 2: Accessibility Keyboard Scan =============

    /**
     * Accessibility Keyboard Typing — يبحث عن الحرف في شجرة الكيبورد
     * ويستخدم dispatchGesture للنقر على إحداثياته
     */
    @SuppressLint("RestrictedApi")
    private fun useAccessibilityTyping() {
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

                // محاولة النقر على الحرف
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

            // تأخير بشري عشوائي 150-400ms
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
                Thread.sleep(30)
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

        // محاولة قراءة النص من contentDescription أولاً (طريقة Gboard)
        val contentDesc = node.contentDescription?.toString()?.lowercase()?.trim()
        var text = node.text?.toString()?.lowercase()?.trim()

        // بعض أنواع الكيبورد تخزن الحرف في className
        val className = node.className?.toString()?.lowercase()

        val nodeText = contentDesc ?: text

        if (nodeText != null && nodeText.isNotEmpty()) {
            // تحقق إذا كان النص يطابق الحرف المطلوب
            val matches = nodeText == targetChar ||
                    nodeText.contains(targetChar) ||
                    // بعض الكيبوردات تضيف بادئة/لاحقة
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
                if (child != null) child.recycle()
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

    /**
     * ضغطة مطوّلة (Long Press) في إحداثية — لبعض الحالات الخاصة
     */
    private fun longPressAtPosition(x: Float, y: Float, durationMs: Long = 400) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "longPress فشل: ${e.message}")
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

    /**
     * استراتيجية بديلة: السحب (Swipe) على الكيبورد
     * يمكن استخدامها كخيار إضافي
     */
    private fun useSwipeTyping() {
        isTyping = true
        Toast.makeText(this, "👆 كتابة بالسحب...", Toast.LENGTH_SHORT).show()

        // حساب أبعاد الشاشة بناءً على rootInActiveWindow
        val root = rootInActiveWindow ?: return
        val displayRect = Rect()
        root.getBoundsInScreen(displayRect)
        val screenWidth = displayRect.width()
        val screenHeight = displayRect.height()

        // صفوف الكيبورد التقريبية (كل جهاز可能有 أبعاد مختلفة)
        val row1Y = screenHeight * 0.78  // الصف العلوي
        val row2Y = screenHeight * 0.85  // الصف الأوسط
        val row3Y = screenHeight * 0.92  // الصف السفلي
        val keyWidth = screenWidth / 10  // تقريباً 10 أزرار في كل صف

        // توزيع الحروف على الأزرار (تقريبي)
        val row1Chars = "qwertyuiop"
        val row2Chars = "asdfghjkl"
        val row3Chars = "zxcvbnm"

        var delay = 0L

        for (char in typingText.lowercase()) {
            val currentDelay = delay
            val currentChar = char.toString()

            handler.postDelayed({
                if (!isTyping) return@postDelayed

                // حساب إحداثيات الحرف
                var x = 0f
                var y = 0f
                var found = false

                if (row1Chars.contains(char)) {
                    x = keyWidth * row1Chars.indexOf(char) + keyWidth / 2
                    y = row1Y.toFloat()
                    found = true
                } else if (row2Chars.contains(char)) {
                    x = keyWidth * row2Chars.indexOf(char) + keyWidth / 2
                    y = row2Y.toFloat()
                    found = true
                } else if (row3Chars.contains(char)) {
                    x = keyWidth * row3Chars.indexOf(char) + keyWidth / 2
                    y = row3Y.toFloat()
                    found = true
                }

                if (found) {
                    val path = Path().apply {
                        moveTo(x, y)
                        // حركة سريعة للداخل ثم الخارج (محاكاة النقر)
                        lineTo(x, y)
                    }
                    val stroke = StrokeDescription(path, 0, 80)
                    val gesture = GestureDescription.Builder()
                        .addStroke(stroke).build()
                    dispatchGesture(gesture, null, null)
                } else {
                    // الحرف مش موجود (مسافة, enter, etc)
                    when (char) {
                        ' ' -> {
                            // النقر على منتصف المسافة
                            val spaceX = screenWidth / 2f
                            val spaceY = row3Y.toFloat() + keyWidth / 2
                            val path = Path().apply { moveTo(spaceX, spaceY) }
                            dispatchGesture(GestureDescription.Builder()
                                .addStroke(StrokeDescription(path, 0, 80)).build(), null, null)
                        }
                    }
                }
            }, currentDelay)

            delay += (200..400).random().toLong()
        }

        handler.postDelayed({
            isTyping = false
            Toast.makeText(this, "✅ تمت الكتابة بالسحب!", Toast.LENGTH_SHORT).show()
        }, delay + 500)
    }
}

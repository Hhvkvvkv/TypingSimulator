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
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat

class TypingService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isTyping = false
    private var typingText = ""

    // منع التكرار: توقيت آخر كتابة + تبريد 8 ثواني
    private var lastTypingEndTime = 0L
    private val cooldownMs = 8000L

    companion object {
        const val CHANNEL_ID = "typing_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_TYPING = "com.yourname.typingsimulator.START_TYPING"
        const val TAG = "TypingService"

        private val QWERTY = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private val ARABIC = listOf("ضصثقفغعهخحجد", "شسيبلاتنمكط", "ءؤرلالاىةوزظ")
    }

    override fun onCreate() {
        try {
            super.onCreate()
            createNotificationChannel()
            showPersistentNotification()
            EventLog.info(this, "الخدمة مرتبطة بالتطبيق وتنتظر الأمر")
        } catch (e: Throwable) {
            Log.e(TAG, "onCreate خطأ: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID, "خدمة المحاكاة", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "إشعار خدمة محاكاة الكتابة" }
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            } catch (e: Throwable) { Log.e(TAG, "createNotificationChannel خطأ: ${e.message}") }
        }
    }

    private fun showPersistentNotification() {
        try {
            val startIntent = Intent(this, TypingService::class.java).apply { action = ACTION_START_TYPING }
            val pendingIntent = PendingIntent.getService(
                this, 0, startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("محاكاة الكتابة")
                .setContentText("اضغط لبدء الكتابة أو من التطبيق")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) { Log.e(TAG, "showPersistentNotification خطأ: ${e.message}") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent?.action == ACTION_START_TYPING) {
                handler.postDelayed({ startTyping() }, 500)
            }
        } catch (e: Throwable) { Log.e(TAG, "onStartCommand خطأ: ${e.message}") }
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Throwable) { START_STICKY }
    }

    // ✅ زر إمكانية الوصول — ضغطة = تشغيل/إيقاف الكتابة
    // توقيع API 33+ (displayId: Int): Boolean — شغال على أندرويد 14
    @Suppress("DEPRECATION")
    // ✅ زر إمكانية الوصول — ضغطة = تشغيل، ضغطة ثانية = إيقاف
    // متوفر في compileSdk 33 (API 33+) مع Boolean return
    override fun onAccessibilityButtonClicked(displayId: Int): Boolean {
        try {
            if (isTyping) {
                isTyping = false
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ lastTypingEndTime = System.currentTimeMillis() }, 100)
                EventLog.info(this, "إيقاف الكتابة من زر الوصول")
                showToast("🛑 تم إيقاف الكتابة")
            } else {
                EventLog.info(this, "تشغيل الكتابة من زر إمكانية الوصول")
                showToast("تم التفعيل ✅")
                handler.postDelayed({ startTyping() }, 700)
            }
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "زر الوصول خطأ: ${e.message}")
            return false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
        EventLog.info(this, "الخدمة توقفت (onInterrupt)")
    }

    // ===================================================================

    private fun startTyping() {
        if (isTyping) {
            showToast("الكتابة جارية بالفعل...")
            return
        }

        // تبريد
        val now = System.currentTimeMillis()
        if (now - lastTypingEndTime < cooldownMs) {
            val remaining = (cooldownMs - (now - lastTypingEndTime)) / 1000
            showToast("⏳ انتظر ${remaining}ث قبل إعادة التشغيل", Toast.LENGTH_LONG)
            return
        }

        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        typingText = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""
        if (typingText.isEmpty()) {
            showToast("لا يوجد نص محفوظ — افتح التطبيق واحفظ نصاً")
            return
        }

        // ⛔️ مهم: ما نبدأش إلا لو الكيبورد مفتوح فعلاً
        val kbRect = getKeyboardRect()
        if (kbRect == null || kbRect.isEmpty()) {
            showToast("⌨️ افتح لوحة المفاتيح في تطبيق الدردشة أولاً (نافذة IME غير موجودة)", Toast.LENGTH_LONG)
            EventLog.warn(this, "الكيبورد غير مكتشف — IME window غير موجود")
            return
        }

        val wTypes = try { windows.map { it.type }.joinToString(",") } catch (e: Throwable) { "ERR" }
        val sr = isScreenReaderActive()

        EventLog.info(this, "=== بدء الكتابة: ${typingText.length} حرف ===")
        EventLog.info(this, "الكيبورد: $kbRect | نوافذ: [$wTypes] | قارئ شاشة: ${if (sr) "نعم" else "لا"}")

        printAllKeyboardKeys()
        useKeyboardTyping()
    }

    @SuppressLint("RestrictedApi")
    private fun useKeyboardTyping() {
        isTyping = true
        var delay = 0L
        for (char in typingText) {
            val currentDelay = delay
            val currentChar = char.toString()
            handler.postDelayed({
                try {
                    if (!isTyping) return@postDelayed
                    val found = tapChar(currentChar)
                    if (found) EventLog.ok(this, "✓ '$currentChar' → نُقر")
                    else EventLog.warn(this, "✗ '$currentChar' → لم يُعثر عليه")
                } catch (e: Throwable) {
                    EventLog.error(this, "خطأ '$currentChar': ${e.message}")
                }
            }, currentDelay)
            // تأخير بشري 200-400ms
            delay += (200..400).random().toLong()
        }
        val totalDelay = delay + 800
        handler.postDelayed({
            isTyping = false
            lastTypingEndTime = System.currentTimeMillis()
            EventLog.info(this, "=== انتهت الكتابة ===")
            showToast("✅ انتهت الكتابة", Toast.LENGTH_LONG)
        }, totalDelay)
    }

    private fun tapChar(targetChar: String): Boolean {
        if (targetChar == " " || targetChar == "\n") {
            val p = findSpecialPoint(targetChar)
            if (p != null) { EventLog.info(this, "مسافة/إدخال $p"); clickAtPosition(p.x.toFloat(), p.y.toFloat()); return true }
            return false
        }
        val rect = scanForChar(targetChar)
        if (rect != null) { EventLog.info(this, "'$targetChar' شجرة: $rect"); clickAtPosition(rect.centerX().toFloat(), rect.centerY().toFloat()); return true }
        val p = computeKeyPoint(targetChar.first())
        if (p != null) { EventLog.info(this, "'$targetChar' إحداثي: $p"); clickAtPosition(p.x.toFloat(), p.y.toFloat()); return true }
        EventLog.warn(this, "'$targetChar' لم يوجد")
        return false
    }

    private fun scanForChar(targetChar: String): Rect? {
        return try {
            val roots = getAllRoots()
            val lower = targetChar.lowercase()
            for (root in roots) {
                val r = searchForCharNode(root, lower)
                root?.recycle()
                if (r != null) return r
            }
            null
        } catch (e: Throwable) { EventLog.error(this, "scanForChar: ${e.message}"); null }
    }

    private fun getAllRoots(): List<AccessibilityNodeInfo?> {
        val list = mutableListOf<AccessibilityNodeInfo?>()
        try { for (w in windows) list.add(w.root) } catch (_: Throwable) {}
        try { list.add(rootInActiveWindow) } catch (_: Throwable) {}
        return list.filterNotNull().distinctBy { System.identityHashCode(it) }
    }

    private fun searchForCharNode(node: AccessibilityNodeInfo?, targetChar: String): Rect? {
        if (node == null) return null
        return try {
            val contentDesc = node.contentDescription?.toString()?.lowercase()?.trim()
            val text = node.text?.toString()?.lowercase()?.trim()
            val nodeText = contentDesc ?: text
            if (nodeText != null && nodeText.isNotEmpty()) {
                val matches = nodeText == targetChar || nodeText.contains(targetChar) || targetChar.contains(nodeText)
                if (matches) {
                    val rect = Rect(); node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) return rect
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                val result = searchForCharNode(child, targetChar)
                if (result != null) { child?.recycle(); return result }
                child?.recycle()
            }
            null
        } catch (e: Throwable) { null }
    }

    private fun findSpecialPoint(targetChar: String): Point? {
        val kw = if (targetChar == " ") listOf("space", "مسافة", "spacebar")
                 else listOf("enter", "إدخال", "done", "go", "next", "search")
        for (root in getAllRoots()) {
            val r = searchSpecial(root, kw)
            root?.recycle()
            if (r != null) return Point(r.centerX(), r.centerY())
        }
        // بديل إحداثي — فقط لو الكيبورد متأكد منه
        val rect = getKeyboardRect() ?: return null
        return if (targetChar == " ") Point(rect.centerX(), rect.bottom - rect.height() / 8)
        else Point(rect.right - rect.width() / 12, rect.bottom - rect.height() / 8)
    }

    private fun searchSpecial(node: AccessibilityNodeInfo?, keywords: List<String>): Rect? {
        if (node == null) return null
        return try {
            val contentDesc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val nodeText = "$contentDesc $text"
            if (keywords.any { nodeText.contains(it) }) {
                val rect = Rect(); node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) return rect
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                val result = searchSpecial(child, keywords)
                if (result != null) { child?.recycle(); return result }
                child?.recycle()
            }
            null
        } catch (e: Throwable) { null }
    }

    private fun getKeyboardRect(): Rect? {
        return try {
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    val r = Rect(); w.getBoundsInScreen(r)
                    if (r.width() > 0 && r.height() > 0) return r
                }
            }
            // ⛔️ ما نستخدمش تقدير لو الكيبورد مش مكتشف — نرجع null أحسن
            null
        } catch (e: Throwable) { null }
    }

    private fun computeKeyPoint(rawChar: Char): Point? {
        val rect = getKeyboardRect() ?: return null
        val lower = rawChar.lowercaseChar()
        val isArabic = rawChar in '\u0600'..'\u06FF'
        val layout = if (isArabic) ARABIC else QWERTY
        for ((rowIdx, row) in layout.withIndex()) {
            val col = row.indexOf(lower)
            if (col >= 0) {
                val bands = 5
                val bandH = rect.height() / bands
                val y = rect.top + bandH * (rowIdx + 1) + bandH / 2
                val keyW = rect.width() / row.length
                val x = rect.left + keyW * col + keyW / 2
                return Point(x, y)
            }
        }
        return null
    }

    private fun clickAtPosition(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = StrokeDescription(path, 0, 70)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val ok = dispatchGesture(gesture, null, null)
            Log.d(TAG, "👆 ($x, $y) → $ok")
        } catch (e: Throwable) { EventLog.error(this, "clickAtPosition: ${e.message}") }
    }

    private fun isScreenReaderActive(): Boolean {
        return try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            am.isTouchExplorationEnabled
        } catch (e: Throwable) { false }
    }

    private fun printAllKeyboardKeys() {
        try {
            val roots = getAllRoots()
            var count = 0
            val sample = StringBuilder()
            for (root in roots) {
                fun traverse(node: AccessibilityNodeInfo?, depth: Int = 0) {
                    if (node == null) return
                    val desc = node.contentDescription?.toString()
                    val txt = node.text?.toString()
                    val cls = node.className
                    val rect = Rect().also { node.getBoundsInScreen(it) }
                    if (desc != null || txt != null) {
                        count++
                        sample.append("${"  ".repeat(depth)}🔑 '$txt' | '$desc' | $cls | $rect\n")
                    }
                    for (i in 0 until node.childCount) traverse(node.getChild(i), depth + 1)
                }
                traverse(root)
            }
            EventLog.info(this, "أزرار الكيبورد المكتشفة: $count")
            if (count > 0) EventLog.info(this, "العينة:\n$sample")
            for (r in roots) r?.recycle()
        } catch (e: Throwable) { EventLog.error(this, "printAllKeyboardKeys: ${e.message}") }
    }

    private fun showToast(msg: String, dur: Int = Toast.LENGTH_SHORT) {
        try { Toast.makeText(this, msg, dur).show() } catch (e: Throwable) {}
    }
}

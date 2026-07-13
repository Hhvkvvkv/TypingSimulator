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
                .setContentText("اضغط هنا لبدء الكتابة")
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
                handler.postDelayed({ startTyping() }, 700)
            }
        } catch (e: Throwable) { Log.e(TAG, "onStartCommand خطأ: ${e.message}") }
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Throwable) { START_STICKY }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
    }

    // ===================================================================

    private fun startTyping() {
        if (isTyping) {
            showToast("الكتابة جارية بالفعل...")
            return
        }
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        typingText = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""
        if (typingText.isEmpty()) {
            showToast("لا يوجد نص محفوظ — افتح التطبيق واحفظ نصاً")
            EventLog.warn(this, "بدء الكتابة: لا يوجد نص محفوظ")
            return
        }

        val kbRect = getKeyboardRect()
        val wTypes = try { windows.map { it.type }.joinToString(",") } catch (e: Throwable) { "ERR" }
        val sr = isScreenReaderActive()

        // تسجيل التشخيص
        EventLog.info(this, "=== بدء الكتابة: ${typingText.length} حرف ===")
        EventLog.info(this, "الكيبورد مكتشف: ${if (kbRect != null) "نعم $kbRect" else "لا"}")
        EventLog.info(this, "أنواع النوافذ: [$wTypes]")
        EventLog.info(this, "قارئ شاشة مُفعّل: ${if (sr) "نعم" else "لا"}")

        val diag = "🔎 كيبورد: ${if (kbRect != null) "نعم" else "لا"} | نوافذ: [$wTypes] | قارئ شاشة: ${if (sr) "نعم" else "لا"}"
        Log.d(TAG, diag)
        showToast(diag, Toast.LENGTH_LONG)

        if (kbRect == null) {
            showToast("⌨️ افتح لوحة المفاتيح في تطبيق الدردشة أولاً", Toast.LENGTH_LONG)
            EventLog.error(this, "الكيبورد غير مكتشف — لم تبدأ الكتابة")
            return
        }

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
                    if (found) {
                        EventLog.ok(this, "✓ الحرف '$currentChar' → نُقر بنجاح")
                    } else {
                        EventLog.warn(this, "✗ الحرف '$currentChar' → لم يُعثر عليه / فشل النقر")
                    }
                } catch (e: Throwable) {
                    EventLog.error(this, "خطأ أثناء '$currentChar': ${e.message}")
                }
            }, currentDelay)
            delay += (180..380).random().toLong()
        }
        val totalDelay = delay + 1000
        handler.postDelayed({
            isTyping = false
            EventLog.info(this, "=== انتهت الكتابة ===")
            showToast("✅ انتهت الكتابة — راجع سجل الأخطاء", Toast.LENGTH_LONG)
        }, totalDelay)
    }

    private fun tapChar(targetChar: String): Boolean {
        if (targetChar == " " || targetChar == "\n") {
            val p = findSpecialPoint(targetChar)
            if (p != null) {
                EventLog.info(this, "مسافة/إدخال: إحداثي $p")
                clickAtPosition(p.x.toFloat(), p.y.toFloat())
                return true
            }
            EventLog.warn(this, "لم يُعثر على زر المسافة/الإدخال")
            return false
        }
        val rect = scanForChar(targetChar)
        if (rect != null) {
            EventLog.info(this, "'$targetChar' وُجد في الشجرة: $rect")
            clickAtPosition(rect.centerX().toFloat(), rect.centerY().toFloat())
            return true
        }
        val p = computeKeyPoint(targetChar.first())
        if (p != null) {
            EventLog.info(this, "'$targetChar' إحداثي محسوب: $p")
            Log.d(TAG, "📐 إحداثي لـ '$targetChar' = $p")
            clickAtPosition(p.x.toFloat(), p.y.toFloat())
            return true
        }
        EventLog.warn(this, "'$targetChar' لم يُعثر عليه نهائياً")
        return false
    }

    // ===== 1) البحث في شجرة الكيبورد =====

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
        } catch (e: Throwable) {
            EventLog.error(this, "scanForChar خطأ: ${e.message}")
            null
        }
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
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
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
        val rect = getKeyboardRect() ?: return null
        return if (targetChar == " ") {
            Point(rect.centerX(), rect.bottom - rect.height() / 8)
        } else {
            Point(rect.right - rect.width() / 12, rect.bottom - rect.height() / 8)
        }
    }

    private fun searchSpecial(node: AccessibilityNodeInfo?, keywords: List<String>): Rect? {
        if (node == null) return null
        return try {
            val contentDesc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val nodeText = "$contentDesc $text"
            if (keywords.any { nodeText.contains(it) }) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
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

    // ===== 2) الحساب الإحداثي =====

    private fun getKeyboardRect(): Rect? {
        return try {
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    val r = Rect()
                    w.getBoundsInScreen(r)
                    if (r.width() > 0 && r.height() > 0) return r
                }
            }
            val dm = resources.displayMetrics
            Rect(0, (dm.heightPixels * 0.5).toInt(), dm.widthPixels, dm.heightPixels)
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
            EventLog.info(this, "👆 نقرة في ($x, $y) → ${if (ok) "تم الإرسال" else "فشل الإرسال"}")
            Log.d(TAG, "👆 نقرة في ($x, $y) → $ok")
        } catch (e: Throwable) {
            EventLog.error(this, "clickAtPosition خطأ: ${e.message}")
        }
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
            EventLog.info(this, "إجمالي الأزرار المكتشفة في الكيبورد: $count")
            if (count > 0) EventLog.info(this, "عينة أزرار:\n$sample")
            for (r in roots) r?.recycle()
        } catch (e: Throwable) {
            EventLog.error(this, "printAllKeyboardKeys خطأ: ${e.message}")
        }
    }

    private fun showToast(msg: String, dur: Int = Toast.LENGTH_SHORT) {
        try { Toast.makeText(this, msg, dur).show() } catch (e: Throwable) {}
    }
}

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
        const val TAG = "TypingSimulator"
    }

    override fun onCreate() {
        super.onCreate()
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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("محاكاة الكتابة")
            .setContentText("اضغط لبدء الكتابة التلقائية")
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

        // سكن للكيبورد: نطبع كل الأزرار في Logcat عشان نشوف أسماء الحروف
        printAllKeyboardKeys()

        isTyping = true
        var delay = 0L
        val baseDelay = (100..200).random() // تأخير عشوائي بشري

        for (char in typingText) {
            val charStr = char.toString()
            val currentDelay = delay

            handler.postDelayed({
                if (!isTyping) return@postDelayed
                findAndClickKey(charStr)
            }, currentDelay)

            delay += baseDelay + (50..150).random() // بين 150 و 350 مللي
        }

        // رسالة النهاية
        handler.postDelayed({
            isTyping = false
            Toast.makeText(this, "✅ تمت الكتابة!", Toast.LENGTH_SHORT).show()
        }, delay + 500)
    }

    // ===================== البحث عن الحرف والنقر =====================

    private fun findAndClickKey(targetChar: String) {
        val allWindows = windows
        var imeRootNode: AccessibilityNodeInfo? = null

        // 1. البحث عن نافذة لوحة المفاتيح (TYPE_INPUT_METHOD)
        for (window in allWindows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                imeRootNode = window.root
                break
            }
        }

        if (imeRootNode == null) {
            Log.e(TAG, "لوحة المفاتيح غير ظاهرة!")
            return
        }

        // 2. البحث عن الحرف داخل شجرة الكيبورد
        val keyRect = searchForCharNode(imeRootNode, targetChar.lowercase())

        if (keyRect != null) {
            // 3. تم العثور على الحرف → نضغط على منتصفه
            Log.d(TAG, "✅ تم التقاط '$targetChar' في ${keyRect.toShortString()}")
            clickAtPosition(keyRect.centerX().toFloat(), keyRect.centerY().toFloat())
        } else {
            Log.e(TAG, "❌ لم يتم العثور على '$targetChar' في لوحة المفاتيح")
        }

        imeRootNode.recycle()
    }

    // دالة تتبع عكسي (Recursive) للبحث عن الحرف في كل أزرار الكيبورد
    // المفتاح السحري: استخدام contentDescription لأن Gboard بيحط الحروف فيه
    private fun searchForCharNode(node: AccessibilityNodeInfo?, targetChar: String): Rect? {
        if (node == null) return null

        val contentDesc = node.contentDescription?.toString()?.lowercase()
        val text = node.text?.toString()?.lowercase()

        // مطابقة الحرف: Gboard بيحط الحرف غالباً في contentDescription
        if ((contentDesc != null && contentDesc.contains(targetChar)) ||
            (text != null && text.contains(targetChar))) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // نتأكد إن الزرار له حجم فعلي
            if (rect.width() > 0 && rect.height() > 0) {
                return rect
            }
        }

        // البحث في العناصر الفرعية
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            val result = searchForCharNode(childNode, targetChar)
            if (result != null) {
                childNode?.recycle()
                return result
            }
            childNode?.recycle()
        }

        return null
    }

    // النقر بإحداثيات محددة باستخدام dispatchGesture
    private fun clickAtPosition(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50)) // 50ms نقرة
            .build()

        dispatchGesture(gesture, null, null)
    }

    // ===================== دالة التشخيص: كشف أسماء الأزرار =====================
    // استخدمها لو بعض الحروف مش بتتضغط — بتطبع كل الأزرار في Logcat

    private fun printAllKeyboardKeys() {
        try {
            val imeRootNode = windows.find { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root ?: return

            fun traverseAndPrint(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val desc = node.contentDescription?.toString()
                val txt = node.text?.toString()

                if (desc != null || txt != null) {
                    Log.d("KeyboardKeys", "🔑 Text: '$txt' | ContentDesc: '$desc'")
                }

                for (i in 0 until node.childCount) {
                    traverseAndPrint(node.getChild(i))
                }
            }

            traverseAndPrint(imeRootNode)
            imeRootNode.recycle()
            Log.d("KeyboardKeys", "✅ تم فحص الكيبورد")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في فحص الكيبورد", e)
        }
    }
}

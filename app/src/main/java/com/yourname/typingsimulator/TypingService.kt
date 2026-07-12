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

    // خريطة لتخزين الحرف والإحداثيات بتاعته من لوحة المفاتيح
    private val keyboardKeysMap = mutableMapOf<String, Rect>()

    companion object {
        const val CHANNEL_ID = "typing_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_TYPING = "com.yourname.typingsimulator.START_TYPING"
        const val TAG = "TypingService"
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // يمكن استخدام الأحداث لتحسين التجربة مستقبلاً
    }

    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun startTyping() {
        if (isTyping) {
            Toast.makeText(this, "الكتابة جارية بالفعل...", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        typingText = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""

        if (typingText.isEmpty()) {
            Toast.makeText(this, "لا يوجد نص محفوظ لمحاكاته", Toast.LENGTH_SHORT).show()
            return
        }

        // أولاً: نحاول البحث عن لوحة المفاتيح وخرائط الأزرار بتاعتها
        Toast.makeText(this, "جارٍ مسح لوحة المفاتيح...", Toast.LENGTH_SHORT).show()
        mapKeyboardElements()

        if (keyboardKeysMap.isNotEmpty()) {
            Log.d(TAG, "تم العثور على ${keyboardKeysMap.size} زر في الكيبورد")
            Toast.makeText(this, "تم العثور على ${keyboardKeysMap.size} زر", Toast.LENGTH_SHORT).show()
            // استخدام النقر على أزرار الكيبورد الحقيقية
            typeUsingKeyboardClicks()
        } else {
            Log.d(TAG, "لم يتم العثور على الكيبورد، استخدام طريقة SET_TEXT البديلة")
            Toast.makeText(this, "لم يتم العثور على الكيبورد، استخدام الطريقة البديلة", Toast.LENGTH_SHORT).show()
            // الرجوع للطريقة البديلة (SET_TEXT)
            typeUsingSetText()
        }
    }

    // ======================== الطريقة الأولى: اختراق نافذة الكيبورد ========================

    /**
     * البحث عن نافذة لوحة المفاتيح واستخراج كل الأزرار بإحداثياتها
     */
    private fun mapKeyboardElements() {
        keyboardKeysMap.clear()

        // 1. جلب كل النوافذ المعروضة على الشاشة حالياً
        val allWindows = windows

        var keyboardNode: AccessibilityNodeInfo? = null

        // 2. البحث عن النافذة الخاصة بلوحة المفاتيح (Input Method Window)
        for (window in allWindows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                keyboardNode = window.root
                Log.d(TAG, "تم العثور على نافذة الكيبورد: ${window.id}")
                break
            }
        }

        if (keyboardNode == null) {
            Log.e(TAG, "لم يتم العثور على لوحة المفاتيح!")
            return
        }

        // 3. استخراج العناصر (الأزرار) من شجرة الكيبورد
        extractKeysFromNode(keyboardNode)
        keyboardNode.recycle()

        Log.d(TAG, "تم التقاط الأزرار: ${keyboardKeysMap.keys}")
    }

    /**
     * البحث المتعمق داخل شجرة الكيبورد لاستخراج الحروف والإحداثيات
     */
    private fun extractKeysFromNode(node: AccessibilityNodeInfo?) {
        if (node == null) return

        // قارئات الشاشة بتعتمد على contentDescription أو text لقراءة الزرار
        val nodeText = node.text?.toString() ?: node.contentDescription?.toString()

        if (!nodeText.isNullOrEmpty() && nodeText.length <= 2) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // نتأكد إن الزرار له حجم فعلي على الشاشة
            if (rect.width() > 0 && rect.height() > 0) {
                keyboardKeysMap[nodeText.lowercase()] = rect
                Log.d(TAG, "زرار: '$nodeText' -> ($rect)")
            }
        }

        // الدخول في العناصر الفرعية (الأبناء)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let { extractKeysFromNode(it) }
            child?.recycle()
        }
    }

    /**
     * الكتابة عن طريق النقر الفعلي على أزرار الكيبورد باستخدام الإحداثيات
     */
    private fun typeUsingKeyboardClicks() {
        isTyping = true
        var index = 0

        val runnable = object : Runnable {
            override fun run() {
                if (index >= typingText.length || !isTyping) {
                    isTyping = false
                    Toast.makeText(this@TypingService, "✅ تمت الكتابة بنجاح", Toast.LENGTH_SHORT).show()
                    return
                }

                val char = typingText[index].lowercase()
                val rect = keyboardKeysMap[char]

                if (rect != null) {
                    val centerX = rect.centerX()
                    val centerY = rect.centerY()

                    Log.d(TAG, "النقر على: '$char' في ($centerX, $centerY)")

                    val clickPath = Path().apply {
                        moveTo(centerX.toFloat(), centerY.toFloat())
                    }

                    val clickGesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))
                        .build()

                    dispatchGesture(clickGesture, null, null)

                    index++
                    handler.postDelayed(this, 150)
                } else {
                    Log.e(TAG, "الحرف '$char' مش موجود في خريطة الكيبورد!")
                    index++
                    handler.postDelayed(this, 50)
                }
            }
        }
        handler.post(runnable)
    }

    // ======================== الطريقة الثانية: SET_TEXT (Fallback) ========================

    /**
     * الطريقة البديلة: استخدام ACTION_SET_TEXT مباشرة على EditText
     */
    private fun typeUsingSetText() {
        val inputNode = findActiveInputNode() ?: run {
            Toast.makeText(this, "الرجاء الضغط على مربع نص أولاً", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@TypingService, "✅ تمت الكتابة بنجاح", Toast.LENGTH_SHORT).show()
                    return
                }

                try {
                    currentText += typingText[index]

                    // محاولة الحصول على عقدة محدثة
                    val freshFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (freshFocus != null && freshFocus.isEditable) {
                        currentNode?.recycle()
                        currentNode = freshFocus
                    } else {
                        freshFocus?.recycle()
                    }

                    val targetNode = currentNode

                    if (targetNode != null) {
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                currentText
                            )
                        }

                        var success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

                        if (!success) {
                            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Thread.sleep(50)
                            success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        }
                    }

                    index++
                    handler.postDelayed(this, 100)

                } catch (e: Exception) {
                    Log.e(TAG, "خطأ في الكتابة", e)
                    isTyping = false
                    currentNode?.recycle()
                    currentNode = null
                    Toast.makeText(this@TypingService, "❌ فشلت الكتابة: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        handler.post(runnable)
    }

    /**
     * البحث عن مربع النص النشط في الشاشة الحالية
     */
    private fun findActiveInputNode(): AccessibilityNodeInfo? {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable && focusedNode.isEnabled) {
            return focusedNode
        }
        focusedNode?.recycle()
        return findFirstEditableNode(rootInActiveWindow)
    }

    private fun findFirstEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isEditable && root.isEnabled && root.isVisibleToUser) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = findFirstEditableNode(child)
            if (result != null) {
                child?.recycle()
                return result
            }
            child?.recycle()
        }
        return null
    }
}

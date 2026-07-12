package com.yourname.typingsimulator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

        // البحث عن مربع النص النشط
        val inputNode = findActiveInputNode()
        
        if (inputNode != null) {
            Log.d(TAG, "تم العثور على مربع نص: ${inputNode.className}")
            typeTextLetterByLetter(inputNode)
        } else {
            Toast.makeText(this, "الرجاء الضغط على مربع نص (مربع دردشة) أولاً", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * البحث عن مربع النص النشط في الشاشة الحالية
     */
    private fun findActiveInputNode(): AccessibilityNodeInfo? {
        // أولاً: البحث عن الـ EditText الم聚焦 (focused)
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable && focusedNode.isEnabled) {
            return focusedNode
        }
        focusedNode?.recycle()

        // ثانياً: البحث عن أول EditText في الشاشة إذا لم يتم العثور على focused
        return findFirstEditableNode(rootInActiveWindow)
    }

    /**
     * البحث عن أول عنصر نصي قابل للتحرير في شجرة الواجهة
     */
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

    /**
     * كتابة النص حرفاً بحرف مع تحديث العقدة في كل مرة
     */
    private fun typeTextLetterByLetter(initialNode: AccessibilityNodeInfo) {
        isTyping = true
        var index = 0
        var currentText = ""
        var node = initialNode

        val runnable = object : Runnable {
            override fun run() {
                if (index >= typingText.length || !isTyping) {
                    isTyping = false
                    node.recycle()
                    Toast.makeText(this@TypingService, "✅ تمت الكتابة بنجاح", Toast.LENGTH_SHORT).show()
                    return
                }

                try {
                    // إضافة الحرف الجديد
                    currentText += typingText[index]

                    // محاولة الحصول على عقدة محدثة
                    val freshFocus = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    val targetNode = if (freshFocus != null && freshFocus.isEditable) {
                        node.recycle()
                        freshFocus
                    } else {
                        freshFocus?.recycle()
                        node
                    }

                    // كتابة النص باستخدام ACTION_SET_TEXT
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            currentText
                        )
                    }
                    
                    val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    
                    if (!success) {
                        // محاولة بديلة: استخدم ACTION_CLICK أولاً ثم ACTION_SET_TEXT
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Thread.sleep(50)
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }

                    node = targetNode
                    index++
                    handler.postDelayed(this, 100)

                } catch (e: Exception) {
                    Log.e(TAG, "خطأ في الكتابة", e)
                    isTyping = false
                    node.recycle()
                    Toast.makeText(this@TypingService, "❌ فشلت الكتابة: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        handler.post(runnable)
    }
}

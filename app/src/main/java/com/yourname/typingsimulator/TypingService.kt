package com.yourname.typingsimulator

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
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
            ).apply { description = "إشعار خدمة محاكاة الكتابة" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
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
            if (Build.VERSION.SDK_INT >= 34) {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
            } else startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_TYPING) startTyping()
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { isTyping = false; handler.removeCallbacksAndMessages(null) }

    // ========================= المحرك الرئيسي =========================

    private fun startTyping() {
        if (isTyping) { Toast.makeText(this, "الكتابة جارية...", Toast.LENGTH_SHORT).show(); return }
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        typingText = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""
        if (typingText.isEmpty()) { Toast.makeText(this, "لا يوجد نص محفوظ", Toast.LENGTH_SHORT).show(); return }

        // === الخطة 1: Clipboard + Paste ===
        if (tryClipboardPaste()) return

        // === الخطة 2: SET_TEXT ===
        typeUsingSetText()
    }

    // ==================== الخطة 1: Clipboard + Paste ====================
    // الأكثر ضماناً - تشتغل على واتساب، تيليجرام، أي تطبيق

    private fun tryClipboardPaste(): Boolean {
        try {
            // 1. نسخ النص إلى الحافظة
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("typing", typingText))

            // 2. البحث عن مربع النص
            val targetNode = findBestTextNode()
            if (targetNode == null) {
                Log.e(TAG, "لم يتم العثور على مربع نص")
                return false
            }

            // 3. الضغط على المربع لتفعيله
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            Thread.sleep(300)

            // 4. محاولة اللصق عبر Accessibility ACTION_PASTE
            val pasted = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasted) {
                Log.d(TAG, "✅ تم اللصق بنجاح!")
                targetNode.recycle()
                Toast.makeText(this, "✅ تمت الكتابة!", Toast.LENGTH_SHORT).show()
                return true
            }

            // 5. لو ACTION_PASTE مشتغلش، نجرب نضغط long click عشان يظهر Paste
            targetNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            Thread.sleep(500) // نستنى القائمة تظهر

            // البحث عن زر Paste في الشاشة
            val pasteButton = findPasteButton()
            if (pasteButton != null) {
                pasteButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                pasteButton.recycle()
                targetNode.recycle()
                Toast.makeText(this, "✅ تمت الكتابة!", Toast.LENGTH_SHORT).show()
                return true
            }

            targetNode.recycle()
            Log.e(TAG, "لم يتم العثور على زر Paste")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Clipboard error", e)
            return false
        }
    }

    // البحث عن زر Paste في الـ UI الحالي
    private fun findPasteButton(): AccessibilityNodeInfo? {
        return searchForText(rootInActiveWindow, "paste", "لصق")
    }

    private fun searchForText(node: AccessibilityNodeInfo?, vararg texts: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val nodeText = node.text?.toString()?.lowercase() ?: node.contentDescription?.toString()?.lowercase() ?: ""
        for (searchText in texts) {
            if (nodeText.contains(searchText.lowercase())) {
                if (node.isClickable) return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = searchForText(child, *texts)
            if (result != null) { child?.recycle(); return result }
            child?.recycle()
        }
        return null
    }

    // ==================== الخطة 2: SET_TEXT ====================

    private fun typeUsingSetText() {
        val node = findBestTextNode() ?: run {
            Toast.makeText(this, "اضغط على مربع نص أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        isTyping = true
        var index = 0

        val runnable = object : Runnable {
            private var currentNode: AccessibilityNodeInfo? = node

            override fun run() {
                if (index >= typingText.length || !isTyping) {
                    isTyping = false
                    currentNode?.recycle()
                    currentNode = null
                    Toast.makeText(this@TypingService, "✅ تمت الكتابة!", Toast.LENGTH_SHORT).show()
                    return
                }

                try {
                    // تحديث العقدة
                    val fresh = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (fresh != null && fresh.isEditable) {
                        currentNode?.recycle()
                        currentNode = fresh
                    } else fresh?.recycle()

                    // كتابة النص حتى الآن
                    val textSoFar = typingText.substring(0, index + 1)
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textSoFar)
                    }
                    currentNode?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

                    index++
                    handler.postDelayed(this, 80)

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

    // ==================== البحث المتقدم عن مربع النص ====================

    private fun findBestTextNode(): AccessibilityNodeInfo? {
        // 1. البحث عن المربع الم聚焦
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        focused?.recycle()

        // 2. البحث في شجرة الواجهة بالكامل
        val found = searchForEditable(rootInActiveWindow)
        if (found != null) return found

        // 3. البحث في كل النوافذ المفتوحة (مش بس النشطة)
        try {
            for (window in windows) {
                if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val root = window.root
                    val result = searchForEditable(root)
                    if (result != null) { root?.recycle(); return result }
                    root?.recycle()
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Window search error", e) }

        return null
    }

    private fun searchForEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isEnabled && node.isVisibleToUser) return node
        if (node.className?.toString()?.contains("EditText") == true
            && node.isEnabled && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = searchForEditable(child)
            if (result != null) { child?.recycle(); return result }
            child?.recycle()
        }
        return null
    }
}

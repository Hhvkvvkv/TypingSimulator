package com.yourname.typingsimulator

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TypingService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    var isTyping = false

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // لا نحتاج معالجة الأحداث هنا، نستخدم زر الوصول فقط
    }

    override fun onInterrupt() {
        isTyping = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onAccessibilityButtonClicked() {
        if (isTyping) {
            Toast.makeText(this, R.string.typing_in_progress, Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val textToType = sharedPref.getString("TEXT_TO_TYPE", "") ?: ""

        if (textToType.isEmpty()) {
            Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show()
            return
        }

        // البحث عن مربع النص (EditText) النشط حالياً في الشاشة
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            typeTextLetterByLetter(focusedNode, textToType)
        } else {
            Toast.makeText(this, R.string.tap_text_field, Toast.LENGTH_SHORT).show()
        }
    }

    private fun typeTextLetterByLetter(node: AccessibilityNodeInfo, text: String) {
        isTyping = true
        var currentText = ""
        var index = 0

        val runnable = object : Runnable {
            override fun run() {
                if (index < text.length) {
                    currentText += text[index]

                    // وضع النص المحدث داخل العقدة (مربع النص)
                    val arguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            currentText
                        )
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

                    index++
                    // تأخير 100 ملي ثانية بين كل حرف لتبدو وكأنها كتابة طبيعية
                    handler.postDelayed(this, 100)
                } else {
                    isTyping = false
                }
            }
        }
        handler.post(runnable)
    }
}

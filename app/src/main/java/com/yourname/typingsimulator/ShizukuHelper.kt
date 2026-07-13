package com.yourname.typingsimulator

import android.util.Log
import moe.shizuku.api.Shizuku

/**
 * helper لاستخدام Shizuku في تنفيذ أوامر الإدخال على مستوى النظام
 */
object ShizukuHelper {

    const val TAG = "ShizukuHelper"
    const val SHIZUKU_PERMISSION_CODE = 1001

    /**
     * هل Shizuku شغال وجاهز؟
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.ping()
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku not available", e)
            false
        }
    }

    /**
     * طلب صلاحية Shizuku
     */
    fun requestPermission() {
        try {
            if (isAvailable()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permission request failed", e)
        }
    }

    /**
     * كتابة نص مباشرةً على مستوى النظام باستخدام input text
     * هذه أمتن طريقة للكتابة لأنها تشتغل على أي تطبيق وأي كيبورد
     */
    fun inputText(text: String): Boolean {
        return try {
            // input text يقبل النص كمعامل واحد
            val process = Shizuku.newProcess(
                arrayOf("/system/bin/input", "text", text),
                null, null
            )
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                Log.d(TAG, "✅ input text نجح: $text")
                true
            } else {
                val error = process.errorStream.bufferedReader().readText()
                Log.e(TAG, "❌ input text فشل: $error")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "input text exception", e)
            false
        }
    }

    /**
     * النقر على إحداثيات محددة في الشاشة
     * يستخدم لمحاكاة النقر على أزرار الكيبورد
     */
    fun tap(x: Int, y: Int): Boolean {
        return try {
            val process = Shizuku.newProcess(
                arrayOf("/system/bin/input", "tap", x.toString(), y.toString()),
                null, null
            )
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "tap exception", e)
            false
        }
    }

    /**
     * الضغط على زر معين (KeyEvent)
     */
    fun keyEvent(keycode: Int): Boolean {
        return try {
            val process = Shizuku.newProcess(
                arrayOf("/system/bin/input", "keyevent", keycode.toString()),
                null, null
            )
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "keyevent exception", e)
            false
        }
    }

    /**
     * كتابة نص حرفاً حرفاً مع ضغطات أزرار حقيقية
     * يستخدم input text لكل حرف عشان يدي إحساس بالكتابة البشرية
     */
    fun typeTextCharacterByCharacter(text: String, onCharTyped: (Int) -> Unit = {}): Boolean {
        var success = true
        for (i in text.indices) {
            val char = text[i].toString()
            if (!inputText(char)) {
                success = false
                break
            }
            onCharTyped(i)
            try {
                Thread.sleep((80..150).random().toLong())
            } catch (_: InterruptedException) {}
        }
        return success
    }
}

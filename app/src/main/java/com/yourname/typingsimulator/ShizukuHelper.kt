package com.yourname.typingsimulator

import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku helper — تستخدم API Shizuku الحديث لتنفيذ أوامر على مستوى النظام
 * لا تحتاج AIDL يدوي، Shizuku.newProcess() متاح مباشرة من dev.rikka.shizuku:api
 */
object ShizukuHelper {

    const val TAG = "ShizukuHelper"
    const val PERMISSION_REQUEST_CODE = 1001

    @Volatile
    private var _available = false

    @Volatile
    private var _version = 0

    fun isAvailable(): Boolean = _available

    fun getVersion(): Int = _version

    /**
     * تهيئة Shizuku — آمنة تماماً، مش بتعمل كراش لو Shizuku مش مثبت
     */
    fun init(context: android.content.Context) {
        try {
            if (Shizuku.pingBinder()) {
                _version = Shizuku.getVersion()
                _available = true
                Log.d(TAG, "✅ Shizuku متصل! API v$_version")
            } else {
                Log.d(TAG, "ℹ️ Shizuku غير متاح (pingBinder فشل)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku init فشل (غير مثبت؟): ${e.message}")
            _available = false
        }
    }

    /**
     * طلب صلاحية Shizuku
     */
    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "طلب الصلاحية فشل: ${e.message}")
        }
    }

    /**
     * كتابة نص مباشرةً على مستوى النظام
     */
    fun inputText(text: String): Boolean {
        if (!_available) {
            Log.e(TAG, "Shizuku غير متاح")
            return false
        }
        return try {
            val process = Shizuku.newProcess(
                arrayOf("/system/bin/input", "text", text),
                null, null
            )
            val exitCode = process.waitFor()
            process.destroy()
            Log.d(TAG, "inputText('$text') → exit $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "inputText استثناء: ${e.message}")
            false
        }
    }

    /**
     * النقر على إحداثيات (x, y)
     */
    fun tap(x: Int, y: Int): Boolean {
        if (!_available) return false
        return try {
            val process = Shizuku.newProcess(
                arrayOf("/system/bin/input", "tap", x.toString(), y.toString()),
                null, null
            )
            val exitCode = process.waitFor()
            process.destroy()
            Log.d(TAG, "tap($x, $y) → exit $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "tap استثناء: ${e.message}")
            false
        }
    }

    /**
     * إرسال keyevent (مثل KEYCODE_DEL=67, KEYCODE_ENTER=66, KEYCODE_SPACE=62)
     */
    fun keyEvent(keyCode: Int): Boolean {
        if (!_available) return false
        return try {
            val process = Shizuku.newProcess(
                arrayOf("/system/bin/input", "keyevent", keyCode.toString()),
                null, null
            )
            val exitCode = process.waitFor()
            process.destroy()
            Log.d(TAG, "keyevent($keyCode) → exit $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "keyevent استثناء: ${e.message}")
            false
        }
    }

    /**
     * تمرير/swipe من نقطة لأخرى (محاكاة السحب على الكيبورد)
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 100): Boolean {
        if (!_available) return false
        return try {
            val process = Shizuku.newProcess(
                arrayOf("/system/bin/input", "swipe",
                    x1.toString(), y1.toString(),
                    x2.toString(), y2.toString(),
                    durationMs.toString()),
                null, null
            )
            val exitCode = process.waitFor()
            process.destroy()
            Log.d(TAG, "swipe($x1,$y1→$x2,$y2,$durationMs) → exit $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "swipe استثناء: ${e.message}")
            false
        }
    }

    /**
     * كتابة نص حرفاً حرفاً عبر input text — ينفع للأحرف اللاتينية
     */
    fun inputTextLetterByLetter(text: String, onCharTyped: ((Int) -> Unit)? = null): Boolean {
        if (!_available) return false
        return try {
            for ((i, char) in text.withIndex()) {
                val process = Shizuku.newProcess(
                    arrayOf("/system/bin/input", "text", char.toString()),
                    null, null
                )
                val exitCode = process.waitFor()
                process.destroy()
                if (exitCode != 0) {
                    Log.e(TAG, "inputText فشل للحرف '$char' في index $i")
                    return false
                }
                onCharTyped?.invoke(i)
                Thread.sleep((50..100).random().toLong())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "inputTextLetterByLetter استثناء: ${e.message}")
            false
        }
    }
}

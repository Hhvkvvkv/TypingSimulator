package com.yourname.typingsimulator

import android.util.Log
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

/**
 * محرك Shizuku للكتابة المباشرة على مستوى النظام
 * يستخدم newProcess من IShizukuService لتنفيذ أوامر input text
 */
object ShizukuHelper {

    const val TAG = "ShizukuHelper"
    const val PERMISSION_REQUEST_CODE = 1001

    private var _service: IShizukuService? = null
    private var _available = false

    fun isAvailable(): Boolean = _available

    /**
     * تهيئة الاتصال بـ Shizuku
     */
    fun init(context: android.content.Context) {
        try {
            // طلب Binder من Shizuku
            ShizukuProvider.requestBinderForNonProviderProcess(context)
            
            if (Shizuku.pingBinder()) {
                val binder = Shizuku.getBinder()
                if (binder != null) {
                    _service = IShizukuService.Stub.asInterface(binder)
                    _available = true
                    Log.d(TAG, "✅ Shizuku connected! API v${Shizuku.getVersion()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku init error", e)
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
            Log.e(TAG, "Permission request error", e)
        }
    }

    /**
     * كتابة نص مباشرةً باستخدام input text على مستوى النظام
     * هذه أسرع وأمتن طريقة للكتابة
     */
    fun inputText(text: String): Boolean {
        val service = _service ?: return false
        return try {
            val process = service.newProcess(
                arrayOf("/system/bin/input", "text", text),
                null, null
            )
            val exitCode = process.waitFor()
            process.destroy()
            
            if (exitCode == 0) {
                Log.d(TAG, "✅ input text: '$text'")
                true
            } else {
                Log.e(TAG, "❌ input text failed: exit=$exitCode")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "input text exception", e)
            false
        }
    }

    /**
     * النقر على إحداثيات معينة في الشاشة
     * لمحاكاة النقر على أزرار الكيبورد
     */
    fun tap(x: Int, y: Int): Boolean {
        val service = _service ?: return false
        return try {
            val process = service.newProcess(
                arrayOf("/system/bin/input", "tap", x.toString(), y.toString()),
                null, null
            )
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "tap exception", e)
            false
        }
    }

    /**
     * كتابة نص حرفاً حرفاً مع تأخير بشري
     */
    fun typeTextCharacterByCharacter(text: String, onCharTyped: ((Int) -> Unit)? = null): Boolean {
        var allSuccess = true
        for (i in text.indices) {
            val char = text[i].toString()
            if (!inputText(char)) {
                allSuccess = false
                break
            }
            onCharTyped?.invoke(i)
            try {
                Thread.sleep((80..150).random().toLong())
            } catch (_: InterruptedException) {}
        }
        return allSuccess
    }
}

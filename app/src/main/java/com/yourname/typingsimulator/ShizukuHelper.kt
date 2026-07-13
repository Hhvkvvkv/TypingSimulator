package com.yourname.typingsimulator

import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Shizuku helper — اختياري وآمن تماماً
 * أي خطأ (حتى Error) مش بيكراش التطبيق
 */
object ShizukuHelper {

    const val TAG = "ShizukuHelper"
    const val PERMISSION_REQUEST_CODE = 1001

    @Volatile
    private var _service: IShizukuService? = null

    @Volatile
    private var _available = false

    @Volatile
    private var _initialized = false

    fun isAvailable(): Boolean = _available

    /**
     * تهيئة Shizuku — آمنة ضد أي خطأ
     * نستخدم Throwable عشان نمسك حتى NoClassDefFoundError
     */
    fun init(context: android.content.Context) {
        if (_initialized) return
        _initialized = true
        try {
            if (Shizuku.pingBinder()) {
                val binder = Shizuku.getBinder()
                if (binder != null) {
                    _service = IShizukuService.Stub.asInterface(binder)
                    _available = true
                    Log.d(TAG, "✅ Shizuku متصل! API v${Shizuku.getVersion()}")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku غير متاح: ${e.message}")
            _available = false
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "طلب الصلاحية فشل: ${e.message}")
        }
    }

    fun inputText(text: String): Boolean {
        val service = _service ?: return false
        return try {
            val process = service.newProcess(
                arrayOf("/system/bin/input", "text", text), null, null
            )
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Throwable) {
            Log.e(TAG, "inputText استثناء: ${e.message}")
            false
        }
    }

    fun keyEvent(keyCode: Int): Boolean {
        val service = _service ?: return false
        return try {
            val process = service.newProcess(
                arrayOf("/system/bin/input", "keyevent", keyCode.toString()), null, null
            )
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0
        } catch (e: Throwable) {
            Log.e(TAG, "keyevent استثناء: ${e.message}")
            false
        }
    }
}

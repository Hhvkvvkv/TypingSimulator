package com.yourname.typingsimulator

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ملف تسجيل الأحداث (Log) داخل التطبيق
 * كل ما يحصل حدث في الخدمة بنسجّله هنا عشان المستخدم يشوفه من شاشة السجل
 */
object EventLog {

    private const val PREF = "EventLogPref"
    private const val KEY = "LOG_ENTRIES"

    // أنواع الأحداث للتلوين/التصنيف
    const val TYPE_INFO = "INFO"
    const val TYPE_OK = "OK"
    const val TYPE_WARN = "WARN"
    const val TYPE_ERROR = "ERROR"

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun add(context: Context, type: String, message: String) {
        try {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val time = fmt.format(Date())
            val entry = "[$time][$type] $message"
            val existing = prefs.getString(KEY, "") ?: ""
            // نحتفظ بآخر 200 سجل
            val lines = (existing.split("\n").filter { it.isNotBlank() } + entry)
                .takeLast(200)
            prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
        } catch (_: Exception) {}
    }

    fun info(context: Context, msg: String) = add(context, TYPE_INFO, msg)
    fun ok(context: Context, msg: String) = add(context, TYPE_OK, msg)
    fun warn(context: Context, msg: String) = add(context, TYPE_WARN, msg)
    fun error(context: Context, msg: String) = add(context, TYPE_ERROR, msg)

    fun getAll(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            (prefs.getString(KEY, "") ?: "").split("\n").filter { it.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        } catch (_: Exception) {}
    }
}

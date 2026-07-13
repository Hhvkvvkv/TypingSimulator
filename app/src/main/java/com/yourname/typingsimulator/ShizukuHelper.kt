package com.yourname.typingsimulator

/**
 * تم إزالة تكامل Shizuku لتجنب أي تعارض مع كلاسات AIDL وقت إقلاع النظام.
 * التطبيق الآن يعتمد كلياً على خدمة إمكانية الوصول (Accessibility Service)
 * لمحاكاة الكتابة البشرية على لوحة المفاتيح.
 *
 * هذه الكائن محتفظ به كـ no-op آمن فقط لو احتجنا التوسعة لاحقاً.
 */
object ShizukuHelper {
    fun isAvailable(): Boolean = false
    fun init(context: android.content.Context) {}
    fun inputText(text: String): Boolean = false
    fun keyEvent(keyCode: Int): Boolean = false
}

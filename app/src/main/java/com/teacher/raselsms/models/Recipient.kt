package com.teacher.raselsms.models

enum class SendStatus {
    PENDING,    // لم يُرسل بعد (في الانتظار)
    SENDING,    // جاري الإرسال الآن
    SENT,       // تم الإرسال بنجاح ✓
    STOPPED,    // تم التوقف قبل الإرسال ⏸
    FAILED,     // فشل الإرسال ✕
    INVALID     // رقم غير صالح ⚠
}

data class Recipient(
    val id: Int,
    val name: String,
    val rawPhone: String,
    val cleanPhone: String,
    val isValid: Boolean,
    var isSelected: Boolean = true,
    var status: SendStatus = if (isValid) SendStatus.PENDING else SendStatus.INVALID,
    var statusMessage: String = ""
)

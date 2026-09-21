package com.teacher.raselsms.models

enum class SendStatus {
    PENDING,    // في الانتظار
    SENDING,    // جاري الإرسال
    SENT,       // تم الإرسال بنجاح
    FAILED,     // فشل الإرسال
    INVALID     // رقم غير صالح
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

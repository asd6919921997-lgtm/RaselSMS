package com.teacher.raselsms.utils

data class SmsStats(
    val charCount: Int,
    val partsCount: Int,
    val isUnicode: Boolean,
    val charsRemainingInPart: Int,
    val isSinglePart: Boolean,
    val adviceText: String
)

object SmsCounter {

    /**
     * فحص هل يحتوي النص على حروف عربية أو يونيكود تتطلب ترميز UCS-2
     */
    fun isUnicodeEncoding(text: String): Boolean {
        for (ch in text) {
            // التحقق من الحروف العربية والرموز الخاصة
            val code = ch.code
            if (code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF ||
                code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF || code > 127) {
                return true
            }
        }
        return false
    }

    /**
     * حساب إحصائيات الرسالة وتفاصيل الأجزاء والتكلفة بدقة
     */
    fun calculate(text: String): SmsStats {
        val len = text.length
        if (len == 0) {
            return SmsStats(
                charCount = 0,
                partsCount = 0,
                isUnicode = false,
                charsRemainingInPart = 70,
                isSinglePart = true,
                adviceText = "اكتب نص الرسالة أعلاه لمشاهدة عدد الحروف والأجزاء."
            )
        }

        val unicode = isUnicodeEncoding(text)
        val singleLimit = if (unicode) 70 else 160
        val multiLimit = if (unicode) 67 else 153

        if (len <= singleLimit) {
            val remaining = singleLimit - len
            return SmsStats(
                charCount = len,
                partsCount = 1,
                isUnicode = unicode,
                charsRemainingInPart = remaining,
                isSinglePart = true,
                adviceText = "ممتاز: الرسالة تقع ضمن حدود (1 SMS) - متبقي لك $remaining حرفاً لتبقى رسالة واحدة."
            )
        } else {
            val parts = kotlin.math.ceil(len.toDouble() / multiLimit.toDouble()).toInt()
            val usedInLastPart = len % multiLimit
            val remainingInPart = if (usedInLastPart == 0) 0 else multiLimit - usedInLastPart
            val charsToReduceToOne = len - singleLimit

            return SmsStats(
                charCount = len,
                partsCount = parts,
                isUnicode = unicode,
                charsRemainingInPart = remainingInPart,
                isSinglePart = false,
                adviceText = "تنبيه: أصبحت الرسالة ($parts أجزاء SMS). يمكنك حذف ($charsToReduceToOne حرفاً) لتصبح رسالة واحدة وتوفر الرصيد!"
            )
        }
    }
}

package com.teacher.raselsms.models

data class SimInfo(
    val slotIndex: Int,          // 0 للشريحة 1، 1 للشريحة 2
    val subscriptionId: Int,     // المعرف البرمجي للشريحة في نظام أندرويد
    val carrierName: String,     // اسم مزود الخدمة (مثل STC, Mobily, Zain)
    val displayName: String,     // الاسم الظاهر في إعدادات الهاتف
    val countryIso: String = ""  // رمز الدولة
) {
    fun getLabel(): String {
        val slotText = "الشريحة ${slotIndex + 1}"
        return if (carrierName.isNotBlank() && carrierName.lowercase() != "unknown") {
            "$slotText ($carrierName)"
        } else if (displayName.isNotBlank()) {
            "$slotText ($displayName)"
        } else {
            slotText
        }
    }
}

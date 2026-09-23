package com.teacher.raselsms.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.teacher.raselsms.models.SimInfo

object SimHelper {

    /**
     * فحص هل أذونات قراءة الشرائح ممنوحة
     */
    fun hasSimPermission(context: Context): Boolean {
        val statePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        return statePermission
    }

    /**
     * جلب الشرائح النشطة في جهاز الأندرويد (SIM 1 و SIM 2)
     */
    fun getActiveSimCards(context: Context): List<SimInfo> {
        val simList = mutableListOf<SimInfo>()

        if (!hasSimPermission(context)) {
            // إضافة خيارات افتراضية في حال كانت الصلاحية غير ممنوحة بعد
            simList.add(SimInfo(0, -1, "الشريحة الافتراضية 1", "SIM 1"))
            simList.add(SimInfo(1, -1, "الشريحة 2", "SIM 2"))
            return simList
        }

        try {
            val subscriptionManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(SubscriptionManager::class.java)
            } else {
                SubscriptionManager.from(context)
            }

            val activeSubscriptions: List<SubscriptionInfo>? =
                subscriptionManager?.activeSubscriptionInfoList

            if (!activeSubscriptions.isNullOrEmpty()) {
                for (sub in activeSubscriptions) {
                    val slot = sub.simSlotIndex
                    val carrier = sub.carrierName?.toString() ?: ""
                    val displayName = sub.displayName?.toString() ?: ""
                    val country = sub.countryIso ?: ""

                    simList.add(
                        SimInfo(
                            slotIndex = slot,
                            subscriptionId = sub.subscriptionId,
                            carrierName = carrier,
                            displayName = displayName,
                            countryIso = country
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // إذا لم يتم العثور على شرائح أو جهاز ذو شريحة واحدة
        if (simList.isEmpty()) {
            simList.add(SimInfo(0, -1, "شريحة 1", "SIM 1"))
        }

        return simList
    }

    /**
     * تنظيف رقم الهاتف وتوحيده بصيغة فلسطينية موحدة
     */
    fun cleanPhoneNumber(phone: String): String {
        return SmartContentParser.normalizePalestinePhone(phone)
    }

    /**
     * التحقق من صحة رقم الجوال الفلسطيني (جوال 059 أو أريدو 056)
     */
    fun isValidPalestinianNumber(phone: String): Boolean {
        return SmartContentParser.isPhoneNumberCell(phone)
    }
}


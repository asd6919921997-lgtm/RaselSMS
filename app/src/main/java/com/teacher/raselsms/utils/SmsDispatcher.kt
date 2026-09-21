package com.teacher.raselsms.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import com.teacher.raselsms.models.Recipient
import com.teacher.raselsms.models.SendStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class SmsDispatcher(private val context: Context) {

    private val isCancelled = AtomicBoolean(false)

    fun cancel() {
        isCancelled.set(true)
    }

    /**
     * إرسال الرسائل للمستلمين المحددين مع فاصل زمني آمن وتحديث فوري للحالة
     */
    suspend fun dispatchBulk(
        recipients: List<Recipient>,
        rawMessage: String,
        subscriptionId: Int,
        delaySeconds: Int,
        onProgress: (current: Int, total: Int, recipient: Recipient) -> Unit,
        onCompleted: (sentCount: Int, failedCount: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        val selectedRecipients = recipients.filter { it.isSelected && it.isValid }
        val total = selectedRecipients.size

        if (total == 0) {
            withContext(Dispatchers.Main) {
                onCompleted(0, 0)
            }
            return@withContext
        }

        var sentSuccess = 0
        var sentFailed = 0

        // الحصول على SmsManager للشريحة المحددة
        val smsManager = getSmsManager(context, subscriptionId)

        for ((index, recipient) in selectedRecipients.withIndex()) {
            if (isCancelled.get()) break

            recipient.status = SendStatus.SENDING
            withContext(Dispatchers.Main) {
                onProgress(index + 1, total, recipient)
            }

            // تخصيص نص الرسالة في حال وجود متغير {الاسم} أو {اسم الطالب}
            val customizedMessage = rawMessage
                .replace("{الاسم}", recipient.name)
                .replace("{اسم الطالب}", recipient.name)
                .replace("{طالب}", recipient.name)

            val success = sendSingleSms(smsManager, recipient.cleanPhone, customizedMessage)

            if (success) {
                recipient.status = SendStatus.SENT
                sentSuccess++
            } else {
                recipient.status = SendStatus.FAILED
                sentFailed++
            }

            withContext(Dispatchers.Main) {
                onProgress(index + 1, total, recipient)
            }

            // الفاصل الزمني الآمن بين الرسائل لحماية الشريحة
            if (index < total - 1 && !isCancelled.get()) {
                delay(delaySeconds * 1000L)
            }
        }

        withContext(Dispatchers.Main) {
            onCompleted(sentSuccess, sentFailed)
        }
    }

    /**
     * تهيئة SmsManager المناسب للشريحة المحددة
     */
    private fun getSmsManager(context: Context, subscriptionId: Int): SmsManager {
        return try {
            if (subscriptionId != -1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            }
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    /**
     * إرسال رسالة نصية فردية والتأكد من دعم الرسائل المقسمة إذا زادت عن 70 حرفاً
     */
    private fun sendSingleSms(smsManager: SmsManager, phone: String, message: String): Boolean {
        return try {
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

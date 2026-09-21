package com.teacher.raselsms.utils

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import com.teacher.raselsms.models.Recipient
import com.teacher.raselsms.models.SendStatus
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
     * إرسال الرسائل للمستلمين المحددين مع دعم الإيقاف الفوري وتمييز من أرسل ومن لم يرسل له
     */
    suspend fun dispatchBulk(
        recipients: List<Recipient>,
        rawMessage: String,
        subscriptionId: Int,
        delaySeconds: Int,
        onProgress: (current: Int, total: Int, recipient: Recipient) -> Unit,
        onCompleted: (sentCount: Int, failedCount: Int, stoppedCount: Int, isStoppedByUser: Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        val selectedRecipients = recipients.filter { it.isSelected && it.isValid }
        val total = selectedRecipients.size

        if (total == 0) {
            withContext(Dispatchers.Main) {
                onCompleted(0, 0, 0, false)
            }
            return@withContext
        }

        var sentSuccess = 0
        var sentFailed = 0
        var stoppedCount = 0

        val smsManager = getSmsManager(context, subscriptionId)

        for ((index, recipient) in selectedRecipients.withIndex()) {
            // إذا طلب المستخدم إيقاف الإرسال
            if (isCancelled.get()) {
                // وضع علامة "تم التوقف قبل الإرسال" لكافة المستلمين المتبقين في القائمة
                for (remainingIdx in index until selectedRecipients.size) {
                    val remainingRecipient = selectedRecipients[remainingIdx]
                    if (remainingRecipient.status != SendStatus.SENT) {
                        remainingRecipient.status = SendStatus.STOPPED
                        stoppedCount++
                    }
                }
                break
            }

            recipient.status = SendStatus.SENDING
            withContext(Dispatchers.Main) {
                onProgress(index + 1, total, recipient)
            }

            // تخصيص اسم الطالب في الرسالة
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

            // الفاصل الزمني الآمن بين كل رسالة
            if (index < total - 1 && !isCancelled.get()) {
                delay(delaySeconds * 1000L)
            }
        }

        val wasStopped = isCancelled.get()
        withContext(Dispatchers.Main) {
            onCompleted(sentSuccess, sentFailed, stoppedCount, wasStopped)
        }
    }

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

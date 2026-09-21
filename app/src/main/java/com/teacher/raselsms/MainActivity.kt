package com.teacher.raselsms

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.teacher.raselsms.adapters.RecipientAdapter
import com.teacher.raselsms.databinding.ActivityMainBinding
import com.teacher.raselsms.models.Recipient
import com.teacher.raselsms.models.SimInfo
import com.teacher.raselsms.utils.ExcelHelper
import com.teacher.raselsms.utils.SimHelper
import com.teacher.raselsms.utils.SmsCounter
import com.teacher.raselsms.utils.SmsDispatcher
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var recipientList = mutableListOf<Recipient>()
    private lateinit var adapter: RecipientAdapter
    private var activeSims = listOf<SimInfo>()
    private var selectedSubscriptionId: Int = -1
    private var isSending: Boolean = false
    private var smsDispatcher: SmsDispatcher? = null

    // منتقي الملفات لفتح ملفات الإكسل و CSV
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            loadRecipientsFromUri(uri)
        }
    }

    // طلب أذونات النظام (SMS والشرائح)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] == true
        val phoneGranted = permissions[Manifest.permission.READ_PHONE_STATE] == true

        if (smsGranted && phoneGranted) {
            binding.cardPermission.visibility = View.GONE
            setupSimCards()
            Toast.makeText(this, "تم منح الصلاحيات بنجاح", Toast.LENGTH_SHORT).show()
        } else {
            binding.cardPermission.visibility = View.VISIBLE
            Toast.makeText(
                this,
                "الصلاحيات ضرورية لإرسال الرسائل واختيار الشريحة",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRecyclerView()
        initDelaySpinner()
        initMessageComposer()
        initListeners()
        checkPermissionsAndInit()
    }

    private fun initRecyclerView() {
        adapter = RecipientAdapter(recipientList) { selected, total ->
            binding.tvSelectedCount.text = "المحدد: $selected من $total"
            binding.btnSendSms.text = "🚀 إرسال الرسائل للمحددين ($selected)"
        }
        binding.rvRecipients.layoutManager = LinearLayoutManager(this)
        binding.rvRecipients.adapter = adapter
    }

    private fun initDelaySpinner() {
        val delayOptions = listOf(
            "ثانيتان (موصى به - آمن وسريع)",
            "3 ثواني (آمن جداً)",
            "5 ثواني (حماية قصوى)",
            "ثانية واحدة"
        )
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            delayOptions
        )
        binding.spinnerDelay.adapter = spinnerAdapter
    }

    /**
     * إعداد محرر الرسالة الموحدة مع المراقبة الحية للحروف وحساب التكلفة
     */
    private fun initMessageComposer() {
        // نص افتراضي مقترح للمعلم يوضح الفكرة
        val defaultText = "السلام عليكم ولي أمر الطالب {الاسم}، نفيدكم بمستوى الطالب المميز ونتطلع لاستمرار التعاون."
        binding.etMessage.setText(defaultText)
        updateSmsStats(defaultText)

        // مراقبة التغيير اليدوي للنص فوراً
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSmsStats(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // زر إدراج متغير الاسم
        binding.btnInsertTag.setOnClickListener {
            val cursorPosition = binding.etMessage.selectionStart
            val tag = "{الاسم}"
            binding.etMessage.text?.insert(cursorPosition.coerceAtLeast(0), tag)
        }
    }

    /**
     * تحديث إحصائيات الرسالة والتنبيهات الملونة
     */
    private fun updateSmsStats(text: String) {
        val stats = SmsCounter.calculate(text)

        binding.tvSmsStats.text = "عدد الحروف: ${stats.charCount} | أجزاء الرسالة: ${stats.partsCount} SMS"

        if (stats.isSinglePart) {
            binding.tvCostBadge.text = "1 SMS (عادي)"
            binding.tvCostBadge.setTextColor(ContextCompat.getColor(this, R.color.success))
            binding.tvCostBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))

            binding.tvSmsNotice.text = stats.adviceText
            binding.tvSmsNotice.setTextColor(ContextCompat.getColor(this, R.color.success))
            binding.tvSmsNotice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0FDF4"))
        } else {
            binding.tvCostBadge.text = "${stats.partsCount} أجزاء SMS"
            binding.tvCostBadge.setTextColor(ContextCompat.getColor(this, R.color.warning))
            binding.tvCostBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))

            binding.tvSmsNotice.text = stats.adviceText
            binding.tvSmsNotice.setTextColor(ContextCompat.getColor(this, R.color.warning))
            binding.tvSmsNotice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFBEB"))
        }
    }

    private fun initListeners() {
        // زر منح الصلاحيات
        binding.btnGrantPermissions.setOnClickListener {
            requestAppPermissions()
        }

        // اختيار ملف الإكسل
        binding.btnImportExcel.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "text/csv",
                    "text/comma-separated-values",
                    "text/plain",
                    "*/*"
                )
            )
        }

        // تحديد الكل / إلغاء التحديد
        binding.btnSelectAll.setOnClickListener {
            adapter.selectAll(true)
        }
        binding.btnDeselectAll.setOnClickListener {
            adapter.selectAll(false)
        }

        // اختيار الشريحة من الـ RadioGroup
        binding.rgSimSelection.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbSim1 && activeSims.isNotEmpty()) {
                selectedSubscriptionId = activeSims[0].subscriptionId
            } else if (checkedId == R.id.rbSim2 && activeSims.size > 1) {
                selectedSubscriptionId = activeSims[1].subscriptionId
            }
        }

        // زر بدء الإرسال
        binding.btnSendSms.setOnClickListener {
            startSendingProcess()
        }

        // زر إيقاف الإرسال
        binding.btnStopSending.setOnClickListener {
            smsDispatcher?.cancel()
            binding.btnStopSending.isEnabled = false
            binding.btnStopSending.text = "جاري الإيقاف..."
        }
    }

    private fun checkPermissionsAndInit() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            binding.cardPermission.visibility = View.GONE
            setupSimCards()
        } else {
            binding.cardPermission.visibility = View.VISIBLE
            requestAppPermissions()
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    /**
     * كشف وعرض الشرائح المتوفرة في الهاتف (SIM 1 و SIM 2)
     */
    private fun setupSimCards() {
        activeSims = SimHelper.getActiveSimCards(this)

        if (activeSims.isEmpty()) {
            binding.tvSimInfoDetails.text = "لم يتم الكشف عن شرائح نشطة"
            return
        }

        // الشريحة الأولى
        val sim1 = activeSims[0]
        binding.rbSim1.text = sim1.getLabel()
        binding.rbSim1.isChecked = true
        selectedSubscriptionId = sim1.subscriptionId

        // الشريحة الثانية إن وجدت
        if (activeSims.size > 1) {
            val sim2 = activeSims[1]
            binding.rbSim2.text = sim2.getLabel()
            binding.rbSim2.visibility = View.VISIBLE
            binding.tvSimInfoDetails.text = "تم العثور على شريحتين (SIM 1 و SIM 2) بنجاح."
        } else {
            binding.rbSim2.visibility = View.GONE
            binding.tvSimInfoDetails.text = "تم تفعيل الشريحة الأولى (SIM 1)."
        }
    }

    /**
     * استيراد البيانات من ملف الإكسل أو الـ CSV
     */
    private fun loadRecipientsFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.tvFileSummary.text = "جاري قراءة وتحليل بيانات الملف..."
                val list = ExcelHelper.parseFile(this@MainActivity, uri)

                if (list.isEmpty()) {
                    binding.tvFileSummary.text = "الملف فارغ أو لم يتم التعرف على الأعمدة!"
                    Toast.makeText(this@MainActivity, "لم يتم العثور على أرقام في الملف", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                recipientList.clear()
                recipientList.addAll(list)
                adapter.updateData(recipientList)

                val validCount = list.count { it.isValid }
                val invalidCount = list.size - validCount

                binding.tvFileSummary.text = "✓ تم استخراج ${list.size} طالب ($validCount أرقام صحيحة، $invalidCount غير صالحة)"
                Toast.makeText(this@MainActivity, "تم تحميل ${list.size} طالب بنجاح", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                binding.tvFileSummary.text = "خطأ في قراءة الملف: ${e.localizedMessage}"
                Toast.makeText(this@MainActivity, "فشل قراءة الملف", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * بدء عملية الإرسال الجماعي
     */
    private fun startSendingProcess() {
        val message = binding.etMessage.text?.toString()?.trim() ?: ""
        if (message.isBlank()) {
            Toast.makeText(this, "يرجى كتابة نص الرسالة أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedCount = adapter.getSelectedCount()
        if (selectedCount == 0) {
            Toast.makeText(this, "يرجى تحديد طالب واحد على الأقل للإرسال", Toast.LENGTH_SHORT).show()
            return
        }

        val delaySeconds = when (binding.spinnerDelay.selectedItemPosition) {
            1 -> 3
            2 -> 5
            3 -> 1
            else -> 2
        }

        val selectedSimLabel = if (binding.rbSim2.isChecked && activeSims.size > 1) {
            binding.rbSim2.text
        } else {
            binding.rbSim1.text
        }

        // نافذة تأكيد قبل الإرسال الفعلي
        AlertDialog.Builder(this)
            .setTitle("تأكيد إرسال الرسائل")
            .setMessage("هل أنت متأكد من بدء إرسال الرسائل لـ ($selectedCount) ولي أمر عبر ($selectedSimLabel) بفاصل زمني ($delaySeconds ثواني)؟")
            .setPositiveButton("نعم، ابدأ الإرسال") { _, _ ->
                executeSending(message, delaySeconds)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun executeSending(message: String, delaySeconds: Int) {
        isSending = true
        binding.layoutProgress.visibility = View.VISIBLE
        binding.progressBarSending.progress = 0
        binding.btnSendSms.isEnabled = false
        binding.btnStopSending.visibility = View.VISIBLE
        binding.btnStopSending.isEnabled = true
        binding.btnStopSending.text = getString(R.string.btn_stop_sending)

        val dispatcher = SmsDispatcher(this)
        smsDispatcher = dispatcher

        lifecycleScope.launch {
            dispatcher.dispatchBulk(
                recipients = recipientList,
                rawMessage = message,
                subscriptionId = selectedSubscriptionId,
                delaySeconds = delaySeconds,
                onProgress = { current, total, _ ->
                    val percentage = (current.toDouble() / total * 100).toInt()
                    binding.progressBarSending.progress = percentage
                    binding.tvProgressStatus.text = "جاري الإرسال ($current من $total)... $percentage%"
                    adapter.notifyDataSetChanged()
                },
                onCompleted = { sentCount, failedCount ->
                    isSending = false
                    binding.btnSendSms.isEnabled = true
                    binding.btnStopSending.visibility = View.GONE
                    binding.tvProgressStatus.text = "اكتمل الإرسال! تم بنجاح: $sentCount | فشل: $failedCount"
                    adapter.notifyDataSetChanged()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("تقرير الإرسال")
                        .setMessage("اكتملت عملية الإرسال:\n\n✓ تم الإرسال بنجاح: $sentCount رسالة\n✕ فشل الإرسال: $failedCount رسالة")
                        .setPositiveButton("حسناً", null)
                        .show()
                }
            )
        }
    }
}

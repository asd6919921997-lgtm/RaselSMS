package com.teacher.raselsms

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.teacher.raselsms.adapters.*
import com.teacher.raselsms.database.SchoolDbHelper
import com.teacher.raselsms.databinding.ActivityMainBinding
import com.teacher.raselsms.databinding.DialogAllSchoolAbsentBinding
import com.teacher.raselsms.databinding.DialogClassDetailsBinding
import com.teacher.raselsms.databinding.DialogPasteImportBinding
import com.teacher.raselsms.models.*
import com.teacher.raselsms.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: SchoolDbHelper

    // أجهزة الشريحة والفاصل الزمني
    private var activeSims: List<SimInfo> = emptyList()
    private var selectedSimId: Int = -1
    private var selectedDelaySeconds: Int = 3

    // قوائم الإرسال السريع
    private val recipientsList = mutableListOf<Recipient>()
    private lateinit var recipientAdapter: RecipientAdapter
    private lateinit var smsDispatcher: SmsDispatcher
    private var isSendingActive = false

    // قائمة حضور وغياب الشعبة
    private var currentAttendanceDate: String = ""
    private var allClassesList = mutableListOf<SchoolClass>()
    private var selectedClassId: Long = -1L
    private val currentClassStudents = mutableListOf<StudentAttendanceItem>()
    private lateinit var attendanceAdapter: AttendanceStudentAdapter

    // قوائم الإحصائيات وسجل الرسائل والإجازات
    private lateinit var classStatsAdapter: ClassStatsAdapter
    private val classStatsList = mutableListOf<ClassStats>()
    private lateinit var smsLogAdapter: SmsLogAdapter
    private val smsLogsList = mutableListOf<SmsLogEntry>()
    private lateinit var holidayAdapter: HolidayAdapter
    private val holidaysList = mutableListOf<Holiday>()

    // مستقبِلات فتح الملفات وجهات الاتصال
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { parseAndLoadFile(it) }
    }

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { parseAndAddContact(it) }
    }

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

        dbHelper = SchoolDbHelper(this)
        smsDispatcher = SmsDispatcher(this)
        currentAttendanceDate = SchoolCalendarHelper.getTodayDateString()

        setupHeader()
        setupBottomNavigation()
        setupAttendanceModule()
        setupStatsModule()
        setupSmsModule()
        setupHistoryModule()
        setupSettingsModule()

        checkPermissionsAndInit()
    }

    // ==========================================
    // 0. ترويسة المدرسة والأذونات والتنقل
    // ==========================================

    private fun setupHeader() {
        val schoolName = dbHelper.getSetting("school_name", "مدرسة الأمل الأساسية للبنين")
        binding.tvSchoolHeaderName.text = schoolName
        binding.tvHeaderDate.text = "اليوم: ${SchoolCalendarHelper.formatDateForDisplay(currentAttendanceDate)}"
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            hideAllTabs()
            when (item.itemId) {
                R.id.nav_attendance -> {
                    binding.layoutAttendance.visibility = View.VISIBLE
                    loadAttendanceClassData()
                    updateSchoolAbsentBadge()
                    true
                }
                R.id.nav_stats -> {
                    binding.layoutStats.visibility = View.VISIBLE
                    loadStatsData()
                    true
                }
                R.id.nav_sms -> {
                    binding.layoutSms.visibility = View.VISIBLE
                    true
                }
                R.id.nav_history -> {
                    binding.layoutHistory.visibility = View.VISIBLE
                    loadSmsHistory("")
                    true
                }
                R.id.nav_settings -> {
                    binding.layoutSettings.visibility = View.VISIBLE
                    loadSettingsData()
                    true
                }
                else -> false
            }
        }
    }

    private fun hideAllTabs() {
        binding.layoutAttendance.visibility = View.GONE
        binding.layoutStats.visibility = View.GONE
        binding.layoutSms.visibility = View.GONE
        binding.layoutHistory.visibility = View.GONE
        binding.layoutSettings.visibility = View.GONE
    }

    private fun checkPermissionsAndInit() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

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

        binding.btnGrantPermission.setOnClickListener {
            requestAppPermissions()
        }
    }

    private fun requestAppPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        requestPermissionsLauncher.launch(permissions)
    }

    private fun setupSimCards() {
        activeSims = SimHelper.getActiveSimCards(this)
        val simNames = activeSims.map { "${it.displayName} (${it.carrierName})" }

        val simAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simNames)
        binding.spnSimCard.adapter = simAdapter

        binding.spnSimCard.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in activeSims.indices) {
                    selectedSimId = activeSims[position].subscriptionId
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // الفاصل الزمني
        val delays = listOf("1 ثانية", "2 ثانية", "3 ثوانٍ (موصى به)", "5 ثوانٍ", "10 ثوانٍ")
        val delayValues = listOf(1, 2, 3, 5, 10)
        val delayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, delays)
        binding.spnDelay.adapter = delayAdapter
        binding.spnDelay.setSelection(2)

        binding.spnDelay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDelaySeconds = delayValues[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ==========================================
    // 1. نظام الحضور والغياب اليومي (Attendance)
    // ==========================================

    private fun setupAttendanceModule() {
        attendanceAdapter = AttendanceStudentAdapter(currentClassStudents) { _, _ ->
            // تفاعل فوري عند التبديل
        }
        binding.rvAttendanceStudents.layoutManager = LinearLayoutManager(this)
        binding.rvAttendanceStudents.adapter = attendanceAdapter

        updateDateDisplayAndValidation()

        // اختيار التاريخ
        binding.btnPickAttendanceDate.setOnClickListener {
            showDatePicker(currentAttendanceDate) { newDate ->
                currentAttendanceDate = newDate
                updateDateDisplayAndValidation()
                loadAttendanceClassData()
                updateSchoolAbsentBadge()
            }
        }

        // تحديد الكل حاضر / غائب
        binding.btnMarkAllPresent.setOnClickListener {
            attendanceAdapter.markAll(absent = false)
        }
        binding.btnMarkAllAbsent.setOnClickListener {
            attendanceAdapter.markAll(absent = true)
        }

        // حفظ رصد الشعبة الحالية
        binding.btnSaveClassAttendance.setOnClickListener {
            saveCurrentClassAttendance()
        }

        // زر غياب كامل المدرسة لهذا اليوم
        binding.btnSendAllSchoolAbsent.setOnClickListener {
            openAllSchoolAbsentDialog()
        }

        // زر لصق شعبة جديدة
        binding.btnOpenPasteStudents.setOnClickListener {
            openPasteStudentsDialog()
        }

        // شريط البحث داخل الشعبة
        binding.etSearchClassStudents.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterClassStudents(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateDateDisplayAndValidation() {
        binding.tvSelectedDateDisplay.text = "تاريخ الرصد: $currentAttendanceDate"
        binding.tvHeaderDate.text = "اليوم: ${SchoolCalendarHelper.formatDateForDisplay(currentAttendanceDate)}"

        val validation = SchoolCalendarHelper.validateAttendanceDate(currentAttendanceDate, dbHelper)
        if (!validation.isValidForAttendance) {
            binding.cardCalendarWarning.visibility = View.VISIBLE
            binding.tvCalendarWarningText.text = validation.message
            binding.btnSaveClassAttendance.isEnabled = false
            binding.btnSaveClassAttendance.alpha = 0.5f
        } else {
            binding.cardCalendarWarning.visibility = View.GONE
            binding.btnSaveClassAttendance.isEnabled = true
            binding.btnSaveClassAttendance.alpha = 1.0f
        }
    }

    private fun loadAttendanceClassesSpinner() {
        lifecycleScope.launch {
            allClassesList = withContext(Dispatchers.IO) {
                dbHelper.getAllClasses().toMutableList()
            }

            if (allClassesList.isEmpty()) {
                // إنشاء شعب افتراضية في حال كانت أول مرة
                withContext(Dispatchers.IO) {
                    dbHelper.getOrCreateClass("الصف السابع", "1")
                    dbHelper.getOrCreateClass("الصف السابع", "2")
                    dbHelper.getOrCreateClass("الصف الثامن", "1")
                }
                allClassesList = withContext(Dispatchers.IO) {
                    dbHelper.getAllClasses().toMutableList()
                }
            }

            val classDisplayNames = allClassesList.map { it.displayName }
            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, classDisplayNames)
            binding.spnAttendanceClass.adapter = adapter

            binding.spnAttendanceClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position in allClassesList.indices) {
                        selectedClassId = allClassesList[position].id
                        loadAttendanceClassData()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            if (selectedClassId == -1L && allClassesList.isNotEmpty()) {
                selectedClassId = allClassesList[0].id
                loadAttendanceClassData()
            }
        }
    }

    private fun loadAttendanceClassData() {
        if (selectedClassId == -1L) {
            loadAttendanceClassesSpinner()
            return
        }

        lifecycleScope.launch {
            val students = withContext(Dispatchers.IO) {
                dbHelper.getStudentsByClass(selectedClassId)
            }
            val existingAttendance = withContext(Dispatchers.IO) {
                dbHelper.getAttendanceForClassAndDate(selectedClassId, currentAttendanceDate)
            }

            currentClassStudents.clear()
            for (student in students) {
                val isAbsent = existingAttendance[student.id] ?: false
                currentClassStudents.add(StudentAttendanceItem(student = student, isAbsent = isAbsent))
            }
            attendanceAdapter.notifyDataSetChanged()
        }
    }

    private fun filterClassStudents(query: String) {
        if (query.isBlank()) {
            attendanceAdapter.updateList(currentClassStudents)
            return
        }
        val q = query.trim()
        val filtered = currentClassStudents.filter {
            it.student.name.contains(q, ignoreCase = true) || it.student.phone.contains(q)
        }
        attendanceAdapter.updateList(filtered)
    }

    private fun saveCurrentClassAttendance() {
        if (selectedClassId == -1L || currentClassStudents.isEmpty()) {
            Toast.makeText(this, "لا يوجد طلاب في هذه الشعبة لرصدهم", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val recordsToSave = currentClassStudents.map { Pair(it.student.id, it.isAbsent) }
            withContext(Dispatchers.IO) {
                dbHelper.saveAttendance(currentAttendanceDate, selectedClassId, recordsToSave)
            }

            val absentCount = currentClassStudents.count { it.isAbsent }
            val currentClass = allClassesList.find { it.id == selectedClassId }
            val className = currentClass?.displayName ?: "الشعبة"

            updateSchoolAbsentBadge()

            if (absentCount > 0) {
                // إظهار نافذة التخيير: إرسال الآن للشعبة أم لاحقاً
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("تم حفظ رصد الشعبة بنجاح ✓")
                    .setMessage("تم رصد ($absentCount) طالب غائب في ($className).\n\nهل ترغب في إرسال إشعار غياب لأولياء أمورهم الآن أم رصد باقي الصفوف أولاً؟")
                    .setPositiveButton("إرسال الآن") { _, _ ->
                        val absentRecipients = currentClassStudents.filter { it.isAbsent }.map {
                            Recipient(id = it.student.id, name = it.student.name, cleanPhone = it.student.phone, rawPhone = it.student.phone, isValid = true)
                        }
                        openSendModalForList(absentRecipients, "إشعار غياب: $className")
                    }
                    .setNegativeButton("لاحقاً") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(this@MainActivity, "تم الحفظ. يمكنك إرسال غياب المدرسة كاملة بعد إتمام باقي الصفوف", Toast.LENGTH_LONG).show()
                    }
                    .show()
            } else {
                Toast.makeText(this@MainActivity, "تم حفظ الرصد بنجاح (لا يوجد غياب في $className) ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSchoolAbsentBadge() {
        lifecycleScope.launch {
            val allSchoolAbsents = withContext(Dispatchers.IO) {
                dbHelper.getAllSchoolAbsentsForDate(currentAttendanceDate)
            }
            binding.btnSendAllSchoolAbsent.text = "غياب كامل المدرسة (${allSchoolAbsents.size})"
        }
    }

    private fun openAllSchoolAbsentDialog() {
        lifecycleScope.launch {
            val absents = withContext(Dispatchers.IO) {
                dbHelper.getAllSchoolAbsentsForDate(currentAttendanceDate)
            }

            if (absents.isEmpty()) {
                Toast.makeText(this@MainActivity, "لا يوجد أي غياب مسجل لكامل المدرسة في هذا اليوم!", Toast.LENGTH_LONG).show()
                return@launch
            }

            val absentRecipients = absents.map {
                Recipient(
                    id = it.first.id,
                    name = it.first.name,
                    cleanPhone = it.first.phone,
                    rawPhone = it.first.phone,
                    isValid = true
                )
            }

            openSendModalForList(absentRecipients, "إشعار غياب كامل المدرسة")
        }
    }

    private fun openSendModalForList(recipients: List<Recipient>, title: String) {
        val dialogBinding = DialogAllSchoolAbsentBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvModalTitle.text = title
        dialogBinding.tvAbsentSummaryBadge.text = "إجمالي الغائبين: ${recipients.size} طالب"

        // عداد الحروف الذكي
        fun updateCharCount() {
            val text = dialogBinding.etAbsentCustomMessage.text.toString()
            val charCount = text.length
            val parts = if (charCount <= 70) 1 else ((charCount - 70) / 67) + 2
            dialogBinding.tvAbsentCharCount.text = "الحروف: $charCount | الأجزاء: $parts رسالة (70 حرف)"
        }
        updateCharCount()

        dialogBinding.etAbsentCustomMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCharCount()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.btnInsertNamePlaceholder.setOnClickListener {
            val current = dialogBinding.etAbsentCustomMessage.text.toString()
            dialogBinding.etAbsentCustomMessage.setText("$current {الاسم}")
            dialogBinding.etAbsentCustomMessage.setSelection(dialogBinding.etAbsentCustomMessage.text?.length ?: 0)
        }

        // إعدادات الشريحة والفاصل في المودال
        val simNames = activeSims.map { "${it.displayName} (${it.carrierName})" }
        val simAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simNames)
        dialogBinding.spnModalSim.adapter = simAdapter

        val delays = listOf("1 ثانية", "2 ثانية", "3 ثوانٍ", "5 ثوانٍ")
        val delayValues = listOf(1, 2, 3, 5)
        val delayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, delays)
        dialogBinding.spnModalDelay.adapter = delayAdapter
        dialogBinding.spnModalDelay.setSelection(2)

        dialogBinding.btnCancelModal.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnStartSendingAbsent.setOnClickListener {
            val message = dialogBinding.etAbsentCustomMessage.text.toString().trim()
            if (message.isBlank()) {
                Toast.makeText(this, "يرجى كتابة نص الرسالة أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val simPos = dialogBinding.spnModalSim.selectedItemPosition
            val subId = if (simPos in activeSims.indices) activeSims[simPos].subscriptionId else -1
            val delaySec = delayValues[dialogBinding.spnModalDelay.selectedItemPosition]

            dialog.dismiss()

            // الانتقال لتبويب الإرسال وعرض التقدم الحي
            binding.bottomNavigation.selectedItemId = R.id.nav_sms
            recipientsList.clear()
            recipientsList.addAll(recipients)
            recipientAdapter.notifyDataSetChanged()
            binding.etMessage.setText(message)

            startBulkSmsSending(subId, delaySec)
        }

        dialog.show()
    }

    private fun openPasteStudentsDialog() {
        val dialogBinding = DialogPasteImportBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // استخراج الصف والشعبة الحاليين كاقتراح
        val currentClass = allClassesList.find { it.id == selectedClassId }
        dialogBinding.etTargetGrade.setText(currentClass?.grade ?: "الصف السابع")
        dialogBinding.etTargetSection.setText(currentClass?.section ?: "1")

        var parsedResults: List<ParsedStudentRow> = emptyList()

        dialogBinding.btnAnalyzePaste.setOnClickListener {
            val text = dialogBinding.etPastedData.text.toString()
            parsedResults = SmartContentParser.parsePastedText(text)
            if (parsedResults.isNotEmpty()) {
                dialogBinding.tvPreviewStats.visibility = View.VISIBLE
                dialogBinding.tvPreviewStats.text = "✓ تم اكتشاف (${parsedResults.size}) طالب ورقم هاتف بنجاح!"
            } else {
                dialogBinding.tvPreviewStats.visibility = View.VISIBLE
                dialogBinding.tvPreviewStats.text = "✕ لم يتم العثور على أرقام هواتف مطابقة. تأكد من لصق الجدول كاملاً."
            }
        }

        dialogBinding.btnSaveParsedStudents.setOnClickListener {
            val grade = dialogBinding.etTargetGrade.text.toString().trim().ifBlank { "الصف العام" }
            val section = dialogBinding.etTargetSection.text.toString().trim().ifBlank { "1" }

            if (parsedResults.isEmpty()) {
                val text = dialogBinding.etPastedData.text.toString()
                parsedResults = SmartContentParser.parsePastedText(text)
            }

            if (parsedResults.isEmpty()) {
                Toast.makeText(this, "يرجى لصق بيانات صالحة أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val classId = withContext(Dispatchers.IO) {
                    dbHelper.getOrCreateClass(grade, section)
                }

                withContext(Dispatchers.IO) {
                    for (row in parsedResults) {
                        dbHelper.addOrUpdateStudent(classId, row.name, row.phone)
                    }
                }

                Toast.makeText(this@MainActivity, "تم حفظ (${parsedResults.size}) طالب في $grade - شعبة $section ✓", Toast.LENGTH_LONG).show()
                dialog.dismiss()

                loadAttendanceClassesSpinner()
            }
        }

        dialog.show()
    }

    // ==========================================
    // 2. لوحة الإحصائيات المدرسية (Dashboard)
    // ==========================================

    private fun setupStatsModule() {
        classStatsAdapter = ClassStatsAdapter(classStatsList) { classStat ->
            openClassDetailRosterDialog(classStat)
        }
        binding.rvClassStats.layoutManager = LinearLayoutManager(this)
        binding.rvClassStats.adapter = classStatsAdapter

        binding.etSearchAllSchool.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAllSchool(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadStatsData() {
        lifecycleScope.launch {
            val term1Start = dbHelper.getSetting("term1_start", "2026-09-01")
            val term1End = dbHelper.getSetting("term1_end", "2027-01-15")

            val overview = withContext(Dispatchers.IO) {
                dbHelper.getSchoolOverview(currentAttendanceDate, term1Start, term1End)
            }

            binding.tvOverviewTotalStudents.text = "إجمالي الطلاب: ${overview.totalStudents}"
            binding.tvOverviewPresentToday.text = "حاضر اليوم: ${overview.presentToday}"
            binding.tvOverviewAbsentToday.text = "غائب اليوم: ${overview.absentToday}"
            binding.tvOverviewTodayRate.text = String.format(Locale.ENGLISH, "نسبة اليوم: %.1f%%", overview.todayAttendanceRate)
            binding.tvOverviewCumulativeRate.text = String.format(Locale.ENGLISH, "التراكمية للفصل: %.1f%%", overview.cumulativeAttendanceRate)

            // إحصائيات كل صف
            val classes = withContext(Dispatchers.IO) { dbHelper.getAllClasses() }
            classStatsList.clear()

            withContext(Dispatchers.IO) {
                for (sc in classes) {
                    val students = dbHelper.getStudentsByClass(sc.id)
                    val (presentToday, absentToday) = dbHelper.getClassDailyStats(sc.id, currentAttendanceDate)
                    val (cumPresent, cumAbsent) = dbHelper.getClassCumulativeStats(sc.id, term1Start, term1End)

                    val recordedToday = presentToday + absentToday
                    val todayRate = if (recordedToday > 0) (presentToday.toDouble() / recordedToday) * 100.0 else 100.0

                    val recordedCum = cumPresent + cumAbsent
                    val cumRate = if (recordedCum > 0) (cumPresent.toDouble() / recordedCum) * 100.0 else 100.0

                    classStatsList.add(
                        ClassStats(
                            schoolClass = sc,
                            totalStudents = students.size,
                            presentToday = presentToday,
                            absentToday = absentToday,
                            todayAttendanceRate = todayRate,
                            cumulativeAttendanceRate = cumRate
                        )
                    )
                }
            }

            classStatsAdapter.notifyDataSetChanged()
        }
    }

    private fun filterAllSchool(query: String) {
        if (query.isBlank()) {
            classStatsAdapter.updateList(classStatsList)
            return
        }
        val q = query.trim()
        val filtered = classStatsList.filter {
            it.schoolClass.displayName.contains(q, ignoreCase = true)
        }
        classStatsAdapter.updateList(filtered)
    }

    private fun openClassDetailRosterDialog(classStat: ClassStats) {
        val dialogBinding = DialogClassDetailsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvClassDetailTitle.text = "تفاصيل ${classStat.schoolClass.displayName}"

        val studentStatsList = mutableListOf<StudentStats>()
        val adapter = StudentStatsAdapter(studentStatsList)
        dialogBinding.rvClassDetailStudents.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvClassDetailStudents.adapter = adapter

        lifecycleScope.launch {
            val term1Start = dbHelper.getSetting("term1_start", "2026-09-01")
            val term1End = dbHelper.getSetting("term1_end", "2027-01-15")

            val students = withContext(Dispatchers.IO) {
                dbHelper.getStudentsByClass(classStat.schoolClass.id)
            }

            withContext(Dispatchers.IO) {
                for (s in students) {
                    val (presentDays, absentDays) = dbHelper.getStudentAttendanceStats(s.id, term1Start, term1End)
                    val total = presentDays + absentDays
                    val rate = if (total > 0) (presentDays.toDouble() / total) * 100.0 else 100.0
                    studentStatsList.add(
                        StudentStats(
                            student = s,
                            presentDays = presentDays,
                            absentDays = absentDays,
                            totalSchoolDays = total,
                            attendanceRate = rate,
                            absenceRate = 100.0 - rate
                        )
                    )
                }
            }
            adapter.notifyDataSetChanged()
        }

        dialogBinding.etSearchClassDetail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty().trim()
                val filtered = studentStatsList.filter { it.student.name.contains(q, ignoreCase = true) || it.student.phone.contains(q) }
                adapter.updateList(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialogBinding.btnCloseClassDetail.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ==========================================
    // 3. الإرسال المباشر والرسائل (Direct SMS)
    // ==========================================

    private fun setupSmsModule() {
        recipientAdapter = RecipientAdapter(recipientsList) { _, _ ->
            updateRecipientStats()
        }
        binding.rvRecipients.layoutManager = LinearLayoutManager(this)
        binding.rvRecipients.adapter = recipientAdapter

        binding.btnImportExcel.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "text/csv",
                    "text/plain"
                )
            )
        }

        binding.btnPasteTable.setOnClickListener {
            openPasteStudentsDialog()
        }

        binding.btnContacts.setOnClickListener {
            pickContactLauncher.launch(null)
        }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCharCount()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSendSms.setOnClickListener {
            if (isSendingActive) return@setOnClickListener
            startBulkSmsSending(selectedSimId, selectedDelaySeconds)
        }

        binding.btnStopSending.setOnClickListener {
            smsDispatcher.cancel()
            Toast.makeText(this, "جاري إيقاف الإرسال بأمان...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCharCount() {
        val text = binding.etMessage.text.toString()
        val count = text.length
        val parts = if (count <= 70) 1 else ((count - 70) / 67) + 2

        binding.tvCharCount.text = "الحروف: $count | الأجزاء: $parts رسالة (70 حرف)"
        if (parts > 1) {
            binding.tvCharCount.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        } else {
            binding.tvCharCount.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        }
    }

    private fun updateRecipientStats() {
        val total = recipientsList.size
        val selected = recipientsList.count { it.isSelected && it.isValid }
        binding.tvRecipientStats.text = "المحدد: $selected من $total"
    }

    private fun startBulkSmsSending(subId: Int, delaySec: Int) {
        val message = binding.etMessage.text.toString().trim()
        if (message.isBlank()) {
            Toast.makeText(this, "يرجى كتابة نص الرسالة أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val validCount = recipientsList.count { it.isSelected && it.isValid }
        if (validCount == 0) {
            Toast.makeText(this, "لا توجد أرقام صالحة محددة للإرسال", Toast.LENGTH_SHORT).show()
            return
        }

        isSendingActive = true
        binding.btnSendSms.visibility = View.GONE
        binding.btnStopSending.visibility = View.VISIBLE
        binding.cardProgress.visibility = View.VISIBLE
        binding.progressBar.progress = 0

        lifecycleScope.launch {
            val nowTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)

            smsDispatcher.dispatchBulk(
                recipients = recipientsList,
                rawMessage = message,
                subscriptionId = subId,
                delaySeconds = delaySec,
                onProgress = { current, total, recipient ->
                    binding.progressBar.progress = ((current.toFloat() / total) * 100).toInt()
                    binding.tvProgressText.text = "جاري إرسال $current من $total: ${recipient.name}"
                    recipientAdapter.notifyDataSetChanged()
                },
                onCompleted = { sentCount, failedCount, stoppedCount, wasStopped ->
                    isSendingActive = false
                    binding.btnSendSms.visibility = View.VISIBLE
                    binding.btnStopSending.visibility = View.GONE
                    binding.cardProgress.visibility = View.GONE

                    val summary = if (wasStopped) {
                        "تم إيقاف الإرسال بطلبك ⏸\nأُرسل بنجاح: $sentCount | فشل: $failedCount | توقف قبل الإرسال: $stoppedCount"
                    } else {
                        "اكتمل الإرسال بنجاح ✓\nأُرسل: $sentCount | فشل: $failedCount"
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("تقرير الإرسال")
                        .setMessage(summary)
                        .setPositiveButton("حسناً", null)
                        .show()
                },
                onMessageDispatched = { recipient, customizedMsg, success ->
                    val nowStr = nowTimeFormat.format(Date())
                    val status = if (success) "SENT" else "FAILED"
                    dbHelper.insertSmsLog(
                        SmsLogEntry(
                            studentName = recipient.name,
                            phone = recipient.cleanPhone,
                            message = customizedMsg,
                            sentAt = nowStr,
                            status = status,
                            simSlot = if (subId == -1) 0 else 1,
                            category = "SMS"
                        )
                    )
                }
            )
        }
    }

    private fun parseAndLoadFile(uri: Uri) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "جاري فحص وقراءة الملف بأمان...", Toast.LENGTH_SHORT).show()
            val parsed = withContext(Dispatchers.IO) {
                ExcelHelper.parseFile(this@MainActivity, uri)
            }
            recipientsList.clear()
            recipientsList.addAll(parsed)
            recipientAdapter.notifyDataSetChanged()
            updateRecipientStats()
            Toast.makeText(this@MainActivity, "تم استيراد ${parsed.size} مستلم بنجاح", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseAndAddContact(uri: Uri) {
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idCol = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameCol = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val hasPhoneCol = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    val contactId = it.getString(idCol)
                    val name = it.getString(nameCol) ?: "مستلم"
                    val hasPhone = it.getInt(hasPhoneCol)

                    if (hasPhone > 0) {
                        val pCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )
                        pCursor?.use { pc ->
                            if (pc.moveToFirst()) {
                                val phoneNumCol = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val rawPhone = pc.getString(phoneNumCol) ?: ""
                                val cleanPhone = ExcelHelper.normalizePhoneNumber(rawPhone)
                                val isValid = ExcelHelper.isValidPhone(cleanPhone)

                                recipientsList.add(
                                    Recipient(
                                        id = (recipientsList.size + 1).toLong(),
                                        name = name,
                                        rawPhone = rawPhone,
                                        cleanPhone = cleanPhone,
                                        isValid = isValid
                                    )
                                )
                                recipientAdapter.notifyDataSetChanged()
                                updateRecipientStats()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر قراءة جهة الاتصال", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // 4. سجل المراسلات والاستعلام (History)
    // ==========================================

    private fun setupHistoryModule() {
        smsLogAdapter = SmsLogAdapter(smsLogsList)
        binding.rvSmsLogs.layoutManager = LinearLayoutManager(this)
        binding.rvSmsLogs.adapter = smsLogAdapter

        binding.etSearchSmsHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadSmsHistory(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadSmsHistory(query: String) {
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                dbHelper.searchSmsLogs(query)
            }
            smsLogsList.clear()
            smsLogsList.addAll(results)
            smsLogAdapter.notifyDataSetChanged()

            binding.tvEmptyHistory.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ==========================================
    // 5. الضبط المدرسي والتقويم (Settings)
    // ==========================================

    private fun setupSettingsModule() {
        holidayAdapter = HolidayAdapter(holidaysList) { holiday ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { dbHelper.deleteHoliday(holiday.id) }
                loadHolidaysData()
                updateDateDisplayAndValidation()
            }
        }
        binding.rvHolidaysList.layoutManager = LinearLayoutManager(this)
        binding.rvHolidaysList.adapter = holidayAdapter

        binding.btnSaveSchoolName.setOnClickListener {
            val name = binding.etSchoolNameInput.text.toString().trim()
            if (name.isNotBlank()) {
                dbHelper.setSetting("school_name", name)
                binding.tvSchoolHeaderName.text = name
                Toast.makeText(this, "تم حفظ اسم المدرسة بنجاح ✓", Toast.LENGTH_SHORT).show()
            }
        }

        // تواريخ الفصول
        setupTermDateButtons()

        // إضافة إجازة جديدة
        var selectedHolidayDate = SchoolCalendarHelper.getTodayDateString()
        binding.btnPickHolidayDate.setOnClickListener {
            showDatePicker(selectedHolidayDate) { newDate ->
                selectedHolidayDate = newDate
                binding.btnPickHolidayDate.text = newDate
            }
        }

        binding.btnAddHoliday.setOnClickListener {
            val title = binding.etNewHolidayTitle.text.toString().trim()
            if (title.isBlank()) {
                Toast.makeText(this, "يرجى كتابة اسم الإجازة (مثل: المولد النبوي)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    dbHelper.addHoliday(selectedHolidayDate, title)
                }
                binding.etNewHolidayTitle.setText("")
                loadHolidaysData()
                updateDateDisplayAndValidation()
                Toast.makeText(this@MainActivity, "تم إضافة الإجازة بنجاح ✓", Toast.LENGTH_SHORT).show()
            }
        }

        // بطاقة المطور
        binding.cardDeveloperContact.setOnClickListener {
            val phone = "+970592898375"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$phone")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                startActivity(dialIntent)
            }
        }
    }

    private fun setupTermDateButtons() {
        binding.btnTerm1Start.setOnClickListener {
            val cur = dbHelper.getSetting("term1_start", "2026-09-01")
            showDatePicker(cur) {
                dbHelper.setSetting("term1_start", it)
                binding.btnTerm1Start.text = "البداية: $it"
            }
        }
        binding.btnTerm1End.setOnClickListener {
            val cur = dbHelper.getSetting("term1_end", "2027-01-15")
            showDatePicker(cur) {
                dbHelper.setSetting("term1_end", it)
                binding.btnTerm1End.text = "النهاية: $it"
            }
        }
        binding.btnVacationStart.setOnClickListener {
            val cur = dbHelper.getSetting("vacation_start", "2027-01-16")
            showDatePicker(cur) {
                dbHelper.setSetting("vacation_start", it)
                binding.btnVacationStart.text = "من: $it"
            }
        }
        binding.btnVacationEnd.setOnClickListener {
            val cur = dbHelper.getSetting("vacation_end", "2027-01-24")
            showDatePicker(cur) {
                dbHelper.setSetting("vacation_end", it)
                binding.btnVacationEnd.text = "إلى: $it"
            }
        }
        binding.btnTerm2Start.setOnClickListener {
            val cur = dbHelper.getSetting("term2_start", "2027-01-25")
            showDatePicker(cur) {
                dbHelper.setSetting("term2_start", it)
                binding.btnTerm2Start.text = "البداية: $it"
            }
        }
        binding.btnTerm2End.setOnClickListener {
            val cur = dbHelper.getSetting("term2_end", "2027-06-10")
            showDatePicker(cur) {
                dbHelper.setSetting("term2_end", it)
                binding.btnTerm2End.text = "النهاية: $it"
            }
        }
    }

    private fun loadSettingsData() {
        val schoolName = dbHelper.getSetting("school_name", "مدرسة الأمل الأساسية للبنين")
        binding.etSchoolNameInput.setText(schoolName)

        binding.btnTerm1Start.text = "البداية: ${dbHelper.getSetting("term1_start", "2026-09-01")}"
        binding.btnTerm1End.text = "النهاية: ${dbHelper.getSetting("term1_end", "2027-01-15")}"
        binding.btnVacationStart.text = "من: ${dbHelper.getSetting("vacation_start", "2027-01-16")}"
        binding.btnVacationEnd.text = "إلى: ${dbHelper.getSetting("vacation_end", "2027-01-24")}"
        binding.btnTerm2Start.text = "البداية: ${dbHelper.getSetting("term2_start", "2027-01-25")}"
        binding.btnTerm2End.text = "النهاية: ${dbHelper.getSetting("term2_end", "2027-06-10")}"

        loadHolidaysData()
    }

    private fun loadHolidaysData() {
        lifecycleScope.launch {
            val holidays = withContext(Dispatchers.IO) { dbHelper.getHolidays() }
            holidaysList.clear()
            holidaysList.addAll(holidays)
            holidayAdapter.notifyDataSetChanged()
        }
    }

    private fun showDatePicker(initialDateStr: String, onSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()
        try {
            val df = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val d = df.parse(initialDateStr)
            if (d != null) cal.time = d
        } catch (e: Exception) {}

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formattedMonth = String.format(Locale.ENGLISH, "%02d", month + 1)
                val formattedDay = String.format(Locale.ENGLISH, "%02d", dayOfMonth)
                val resultDate = "$year-$formattedMonth-$formattedDay"
                onSelected(resultDate)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

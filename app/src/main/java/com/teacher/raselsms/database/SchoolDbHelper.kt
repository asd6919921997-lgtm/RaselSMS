package com.teacher.raselsms.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.teacher.raselsms.models.*

class SchoolDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "rasel_school.db"
        const val DATABASE_VERSION = 2

        // جدول الشعب والصفوف
        const val TABLE_CLASSES = "classes"
        const val COL_CLASS_ID = "id"
        const val COL_CLASS_STAGE = "stage"
        const val COL_CLASS_GRADE = "grade"
        const val COL_CLASS_SECTION = "section"

        // جدول الطلاب
        const val TABLE_STUDENTS = "students"
        const val COL_STUDENT_ID = "id"
        const val COL_STUDENT_CLASS_ID = "class_id"
        const val COL_STUDENT_NAME = "name"
        const val COL_STUDENT_PHONE = "phone"
        const val COL_STUDENT_IS_ACTIVE = "is_active"

        // جدول الحضور والغياب
        const val TABLE_ATTENDANCE = "attendance"
        const val COL_ATT_ID = "id"
        const val COL_ATT_DATE = "date"
        const val COL_ATT_STUDENT_ID = "student_id"
        const val COL_ATT_CLASS_ID = "class_id"
        const val COL_ATT_IS_ABSENT = "is_absent" // 1 = غائب, 0 = حاضر

        // جدول أرشيف الرسائل المرسلة
        const val TABLE_SMS_LOGS = "sms_logs"
        const val COL_LOG_ID = "id"
        const val COL_LOG_STUDENT_NAME = "student_name"
        const val COL_LOG_PHONE = "phone"
        const val COL_LOG_MESSAGE = "message"
        const val COL_LOG_SENT_AT = "sent_at"
        const val COL_LOG_STATUS = "status"
        const val COL_LOG_SIM_SLOT = "sim_slot"
        const val COL_LOG_CATEGORY = "category"

        // جدول الإجازات والعطلات
        const val TABLE_HOLIDAYS = "holidays"
        const val COL_HOL_ID = "id"
        const val COL_HOL_DATE = "date"
        const val COL_HOL_TITLE = "title"

        // جدول إعدادات المدرسة والتقويم
        const val TABLE_SETTINGS = "school_settings"
        const val COL_SET_KEY = "setting_key"
        const val COL_SET_VAL = "setting_val"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_CLASSES (
                $COL_CLASS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CLASS_STAGE TEXT DEFAULT 'المرحلة الأساسية',
                $COL_CLASS_GRADE TEXT NOT NULL,
                $COL_CLASS_SECTION TEXT NOT NULL,
                UNIQUE($COL_CLASS_GRADE, $COL_CLASS_SECTION)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_STUDENTS (
                $COL_STUDENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_STUDENT_CLASS_ID INTEGER NOT NULL,
                $COL_STUDENT_NAME TEXT NOT NULL,
                $COL_STUDENT_PHONE TEXT NOT NULL,
                $COL_STUDENT_IS_ACTIVE INTEGER DEFAULT 1,
                FOREIGN KEY($COL_STUDENT_CLASS_ID) REFERENCES $TABLE_CLASSES($COL_CLASS_ID) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_ATTENDANCE (
                $COL_ATT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ATT_DATE TEXT NOT NULL,
                $COL_ATT_STUDENT_ID INTEGER NOT NULL,
                $COL_ATT_CLASS_ID INTEGER NOT NULL,
                $COL_ATT_IS_ABSENT INTEGER NOT NULL,
                UNIQUE($COL_ATT_DATE, $COL_ATT_STUDENT_ID),
                FOREIGN KEY($COL_ATT_STUDENT_ID) REFERENCES $TABLE_STUDENTS($COL_STUDENT_ID) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_SMS_LOGS (
                $COL_LOG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LOG_STUDENT_NAME TEXT NOT NULL,
                $COL_LOG_PHONE TEXT NOT NULL,
                $COL_LOG_MESSAGE TEXT NOT NULL,
                $COL_LOG_SENT_AT TEXT NOT NULL,
                $COL_LOG_STATUS TEXT NOT NULL,
                $COL_LOG_SIM_SLOT INTEGER DEFAULT 0,
                $COL_LOG_CATEGORY TEXT DEFAULT 'ABSENCE'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_HOLIDAYS (
                $COL_HOL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HOL_DATE TEXT NOT NULL UNIQUE,
                $COL_HOL_TITLE TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_SETTINGS (
                $COL_SET_KEY TEXT PRIMARY KEY,
                $COL_SET_VAL TEXT NOT NULL
            )
        """.trimIndent())

        // إعدادات أولية افتراضية للمدرسة
        val defaultSettings = mapOf(
            "school_name" to "مدرسة الأمل الأساسية للبنين",
            "term1_start" to "2026-09-01",
            "term1_end" to "2027-01-15",
            "term2_start" to "2027-01-25",
            "term2_end" to "2027-06-10",
            "vacation_start" to "2027-01-16",
            "vacation_end" to "2027-01-24"
        )
        for ((key, value) in defaultSettings) {
            val cv = ContentValues().apply {
                put(COL_SET_KEY, key)
                put(COL_SET_VAL, value)
            }
            db.insert(TABLE_SETTINGS, null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // الترقية المستقبلية دون فقدان البيانات
    }

    // ==========================================
    // إدارة الصفوف والشعب (Classes & Sections)
    // ==========================================

    @Synchronized
    fun getOrCreateClass(grade: String, section: String): Long {
        val cleanGrade = grade.trim().ifBlank { "عام" }
        val cleanSection = section.trim().ifBlank { "1" }

        val db = readableDatabase
        val cursor = db.query(
            TABLE_CLASSES,
            arrayOf(COL_CLASS_ID),
            "$COL_CLASS_GRADE = ? AND $COL_CLASS_SECTION = ?",
            arrayOf(cleanGrade, cleanSection),
            null, null, null
        )
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            cursor.close()
            return id
        }
        cursor.close()

        val wDb = writableDatabase
        val cv = ContentValues().apply {
            put(COL_CLASS_GRADE, cleanGrade)
            put(COL_CLASS_SECTION, cleanSection)
        }
        return wDb.insertWithOnConflict(TABLE_CLASSES, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun getAllClasses(): List<SchoolClass> {
        val list = mutableListOf<SchoolClass>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CLASSES,
            arrayOf(COL_CLASS_ID, COL_CLASS_GRADE, COL_CLASS_SECTION),
            null, null, null, null,
            "$COL_CLASS_GRADE ASC, $COL_CLASS_SECTION ASC"
        )
        while (cursor.moveToNext()) {
            list.add(
                SchoolClass(
                    id = cursor.getLong(0),
                    grade = cursor.getString(1),
                    section = cursor.getString(2)
                )
            )
        }
        cursor.close()
        return list
    }

    // ==========================================
    // إدارة الطلاب (Students)
    // ==========================================

    @Synchronized
    fun addOrUpdateStudent(classId: Long, name: String, phone: String): Long {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        if (cleanName.isBlank() || cleanPhone.isBlank()) return -1L

        val db = writableDatabase
        // فحص هل الطالب موجود مسبقاً في الشعبة
        val cursor = db.query(
            TABLE_STUDENTS,
            arrayOf(COL_STUDENT_ID),
            "$COL_STUDENT_CLASS_ID = ? AND $COL_STUDENT_NAME = ?",
            arrayOf(classId.toString(), cleanName),
            null, null, null
        )
        if (cursor.moveToFirst()) {
            val existingId = cursor.getLong(0)
            cursor.close()
            val cv = ContentValues().apply {
                put(COL_STUDENT_PHONE, cleanPhone)
                put(COL_STUDENT_IS_ACTIVE, 1)
            }
            db.update(TABLE_STUDENTS, cv, "$COL_STUDENT_ID = ?", arrayOf(existingId.toString()))
            return existingId
        }
        cursor.close()

        val cv = ContentValues().apply {
            put(COL_STUDENT_CLASS_ID, classId)
            put(COL_STUDENT_NAME, cleanName)
            put(COL_STUDENT_PHONE, cleanPhone)
            put(COL_STUDENT_IS_ACTIVE, 1)
        }
        return db.insert(TABLE_STUDENTS, null, cv)
    }

    @Synchronized
    fun getStudentsByClass(classId: Long): List<Student> {
        val list = mutableListOf<Student>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_STUDENTS,
            arrayOf(COL_STUDENT_ID, COL_STUDENT_CLASS_ID, COL_STUDENT_NAME, COL_STUDENT_PHONE, COL_STUDENT_IS_ACTIVE),
            "$COL_STUDENT_CLASS_ID = ? AND $COL_STUDENT_IS_ACTIVE = 1",
            arrayOf(classId.toString()),
            null, null,
            "$COL_STUDENT_NAME ASC"
        )
        while (cursor.moveToNext()) {
            list.add(
                Student(
                    id = cursor.getLong(0),
                    classId = cursor.getLong(1),
                    name = cursor.getString(2),
                    phone = cursor.getString(3),
                    isActive = cursor.getInt(4) == 1
                )
            )
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun updateStudent(studentId: Long, name: String, phone: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_STUDENT_NAME, name.trim())
            put(COL_STUDENT_PHONE, phone.trim())
        }
        return db.update(TABLE_STUDENTS, cv, "$COL_STUDENT_ID = ?", arrayOf(studentId.toString())) > 0
    }

    @Synchronized
    fun deleteStudent(studentId: Long): Boolean {
        return moveToTrash(studentId)
    }

    @Synchronized
    fun moveToTrash(studentId: Long): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_STUDENT_IS_ACTIVE, 0)
        }
        return db.update(TABLE_STUDENTS, cv, "$COL_STUDENT_ID = ?", arrayOf(studentId.toString())) > 0
    }

    @Synchronized
    fun restoreFromTrash(studentId: Long): Boolean {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_STUDENT_IS_ACTIVE, 1)
        }
        return db.update(TABLE_STUDENTS, cv, "$COL_STUDENT_ID = ?", arrayOf(studentId.toString())) > 0
    }

    @Synchronized
    fun getTrashStudents(): List<Pair<Student, SchoolClass>> {
        val list = mutableListOf<Pair<Student, SchoolClass>>()
        val db = readableDatabase
        val sql = """
            SELECT s.$COL_STUDENT_ID, s.$COL_STUDENT_CLASS_ID, s.$COL_STUDENT_NAME, s.$COL_STUDENT_PHONE,
                   c.$COL_CLASS_STAGE, c.$COL_CLASS_GRADE, c.$COL_CLASS_SECTION
            FROM $TABLE_STUDENTS s
            JOIN $TABLE_CLASSES c ON s.$COL_STUDENT_CLASS_ID = c.$COL_CLASS_ID
            WHERE s.$COL_STUDENT_IS_ACTIVE = 0
            ORDER BY s.$COL_STUDENT_NAME ASC
        """.trimIndent()
        val cursor = db.rawQuery(sql, null)
        while (cursor.moveToNext()) {
            val student = Student(
                id = cursor.getLong(0),
                classId = cursor.getLong(1),
                name = cursor.getString(2),
                phone = cursor.getString(3),
                isActive = false
            )
            val schoolClass = SchoolClass(
                id = cursor.getLong(1),
                stage = cursor.getString(4) ?: "المرحلة الأساسية",
                grade = cursor.getString(5),
                section = cursor.getString(6)
            )
            list.add(Pair(student, schoolClass))
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun deleteStudentPermanently(studentId: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_STUDENTS, "$COL_STUDENT_ID = ?", arrayOf(studentId.toString())) > 0
    }

    @Synchronized
    fun emptyTrash(): Int {
        val db = writableDatabase
        return db.delete(TABLE_STUDENTS, "$COL_STUDENT_IS_ACTIVE = 0", null)
    }

    @Synchronized
    fun clearAllSchoolData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ATTENDANCE, null, null)
            db.delete(TABLE_SMS_LOGS, null, null)
            db.delete(TABLE_STUDENTS, null, null)
            db.delete(TABLE_CLASSES, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getAllStudents(): List<Student> {
        val list = mutableListOf<Student>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_STUDENTS,
            arrayOf(COL_STUDENT_ID, COL_STUDENT_CLASS_ID, COL_STUDENT_NAME, COL_STUDENT_PHONE, COL_STUDENT_IS_ACTIVE),
            "$COL_STUDENT_IS_ACTIVE = 1",
            null, null, null,
            "$COL_STUDENT_NAME ASC"
        )
        while (cursor.moveToNext()) {
            list.add(
                Student(
                    id = cursor.getLong(0),
                    classId = cursor.getLong(1),
                    name = cursor.getString(2),
                    phone = cursor.getString(3),
                    isActive = cursor.getInt(4) == 1
                )
            )
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun searchStudents(query: String): List<Pair<Student, SchoolClass>> {
        val list = mutableListOf<Pair<Student, SchoolClass>>()
        val db = readableDatabase
        val q = "%${query.trim()}%"
        val sql = """
            SELECT s.$COL_STUDENT_ID, s.$COL_STUDENT_CLASS_ID, s.$COL_STUDENT_NAME, s.$COL_STUDENT_PHONE,
                   c.$COL_CLASS_GRADE, c.$COL_CLASS_SECTION
            FROM $TABLE_STUDENTS s
            JOIN $TABLE_CLASSES c ON s.$COL_STUDENT_CLASS_ID = c.$COL_CLASS_ID
            WHERE (s.$COL_STUDENT_NAME LIKE ? OR s.$COL_STUDENT_PHONE LIKE ? OR c.$COL_CLASS_GRADE LIKE ?)
            ORDER BY c.$COL_CLASS_GRADE ASC, s.$COL_STUDENT_NAME ASC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(q, q, q))
        while (cursor.moveToNext()) {
            val student = Student(
                id = cursor.getLong(0),
                classId = cursor.getLong(1),
                name = cursor.getString(2),
                phone = cursor.getString(3)
            )
            val schoolClass = SchoolClass(
                id = cursor.getLong(1),
                grade = cursor.getString(4),
                section = cursor.getString(5)
            )
            list.add(Pair(student, schoolClass))
        }
        cursor.close()
        return list
    }

    // ==========================================
    // الحضور والغياب (Daily Attendance)
    // ==========================================

    @Synchronized
    fun saveAttendance(date: String, classId: Long, studentAttendance: List<Pair<Long, Boolean>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((studentId, isAbsent) in studentAttendance) {
                val cv = ContentValues().apply {
                    put(COL_ATT_DATE, date)
                    put(COL_ATT_STUDENT_ID, studentId)
                    put(COL_ATT_CLASS_ID, classId)
                    put(COL_ATT_IS_ABSENT, if (isAbsent) 1 else 0)
                }
                db.insertWithOnConflict(TABLE_ATTENDANCE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getAttendanceForClassAndDate(classId: Long, date: String): Map<Long, Boolean> {
        val map = mutableMapOf<Long, Boolean>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ATTENDANCE,
            arrayOf(COL_ATT_STUDENT_ID, COL_ATT_IS_ABSENT),
            "$COL_ATT_CLASS_ID = ? AND $COL_ATT_DATE = ?",
            arrayOf(classId.toString(), date),
            null, null, null
        )
        while (cursor.moveToNext()) {
            val studentId = cursor.getLong(0)
            val isAbsent = cursor.getInt(1) == 1
            map[studentId] = isAbsent
        }
        cursor.close()
        return map
    }

    @Synchronized
    fun getAllSchoolAbsentsForDate(date: String): List<Pair<Student, SchoolClass>> {
        val list = mutableListOf<Pair<Student, SchoolClass>>()
        val db = readableDatabase
        val sql = """
            SELECT s.$COL_STUDENT_ID, s.$COL_STUDENT_CLASS_ID, s.$COL_STUDENT_NAME, s.$COL_STUDENT_PHONE,
                   c.$COL_CLASS_GRADE, c.$COL_CLASS_SECTION
            FROM $TABLE_ATTENDANCE a
            JOIN $TABLE_STUDENTS s ON a.$COL_ATT_STUDENT_ID = s.$COL_STUDENT_ID
            JOIN $TABLE_CLASSES c ON a.$COL_ATT_CLASS_ID = c.$COL_CLASS_ID
            WHERE a.$COL_ATT_DATE = ? AND a.$COL_ATT_IS_ABSENT = 1
            ORDER BY c.$COL_CLASS_GRADE ASC, c.$COL_CLASS_SECTION ASC, s.$COL_STUDENT_NAME ASC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(date))
        while (cursor.moveToNext()) {
            val student = Student(
                id = cursor.getLong(0),
                classId = cursor.getLong(1),
                name = cursor.getString(2),
                phone = cursor.getString(3)
            )
            val schoolClass = SchoolClass(
                id = cursor.getLong(1),
                grade = cursor.getString(4),
                section = cursor.getString(5)
            )
            list.add(Pair(student, schoolClass))
        }
        cursor.close()
        return list
    }

    // ==========================================
    // سجل حركات الإرسال (SMS Logs & Search)
    // ==========================================

    @Synchronized
    fun insertSmsLog(entry: SmsLogEntry): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_LOG_STUDENT_NAME, entry.studentName)
            put(COL_LOG_PHONE, entry.phone)
            put(COL_LOG_MESSAGE, entry.message)
            put(COL_LOG_SENT_AT, entry.sentAt)
            put(COL_LOG_STATUS, entry.status)
            put(COL_LOG_SIM_SLOT, entry.simSlot)
            put(COL_LOG_CATEGORY, entry.category)
        }
        return db.insert(TABLE_SMS_LOGS, null, cv)
    }

    @Synchronized
    fun searchSmsLogs(query: String): List<SmsLogEntry> {
        val list = mutableListOf<SmsLogEntry>()
        val db = readableDatabase
        val q = "%${query.trim()}%"
        val cursor = db.query(
            TABLE_SMS_LOGS,
            null,
            "$COL_LOG_PHONE LIKE ? OR $COL_LOG_STUDENT_NAME LIKE ? OR $COL_LOG_MESSAGE LIKE ?",
            arrayOf(q, q, q),
            null, null,
            "$COL_LOG_ID DESC"
        )
        while (cursor.moveToNext()) {
            list.add(
                SmsLogEntry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LOG_ID)),
                    studentName = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_STUDENT_NAME)),
                    phone = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_PHONE)),
                    message = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_MESSAGE)),
                    sentAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_SENT_AT)),
                    status = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_STATUS)),
                    simSlot = cursor.getInt(cursor.getColumnIndexOrThrow(COL_LOG_SIM_SLOT)),
                    category = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOG_CATEGORY))
                )
            )
        }
        cursor.close()
        return list
    }

    // ==========================================
    // العطلات والإجازات الرسمية (Holidays)
    // ==========================================

    @Synchronized
    fun addHoliday(date: String, title: String): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_HOL_DATE, date.trim())
            put(COL_HOL_TITLE, title.trim())
        }
        return db.insertWithOnConflict(TABLE_HOLIDAYS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun getHolidays(): List<Holiday> {
        val list = mutableListOf<Holiday>()
        val db = readableDatabase
        val cursor = db.query(TABLE_HOLIDAYS, null, null, null, null, null, "$COL_HOL_DATE ASC")
        while (cursor.moveToNext()) {
            list.add(
                Holiday(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_HOL_ID)),
                    date = cursor.getString(cursor.getColumnIndexOrThrow(COL_HOL_DATE)),
                    title = cursor.getString(cursor.getColumnIndexOrThrow(COL_HOL_TITLE))
                )
            )
        }
        cursor.close()
        return list
    }

    @Synchronized
    fun deleteHoliday(id: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_HOLIDAYS, "$COL_HOL_ID = ?", arrayOf(id.toString())) > 0
    }

    @Synchronized
    fun getHolidayForDate(date: String): Holiday? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HOLIDAYS,
            null,
            "$COL_HOL_DATE = ?",
            arrayOf(date),
            null, null, null
        )
        var holiday: Holiday? = null
        if (cursor.moveToFirst()) {
            holiday = Holiday(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_HOL_ID)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COL_HOL_DATE)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(COL_HOL_TITLE))
            )
        }
        cursor.close()
        return holiday
    }

    // ==========================================
    // إعدادات المدرسة (Settings)
    // ==========================================

    @Synchronized
    fun getSetting(key: String, defaultVal: String): String {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SETTINGS,
            arrayOf(COL_SET_VAL),
            "$COL_SET_KEY = ?",
            arrayOf(key),
            null, null, null
        )
        var res = defaultVal
        if (cursor.moveToFirst()) {
            res = cursor.getString(0)
        }
        cursor.close()
        return res
    }

    @Synchronized
    fun setSetting(key: String, value: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_SET_KEY, key)
            put(COL_SET_VAL, value)
        }
        db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ==========================================
    // الإحصائيات والنسب التراكمية (Statistics)
    // ==========================================

    @Synchronized
    fun getStudentAttendanceStats(studentId: Long, termStartDate: String, termEndDate: String): Pair<Int, Int> {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT 
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 1 THEN 1 ELSE 0 END) as absent_count,
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 0 THEN 1 ELSE 0 END) as present_count
            FROM $TABLE_ATTENDANCE
            WHERE $COL_ATT_STUDENT_ID = ? AND $COL_ATT_DATE >= ? AND $COL_ATT_DATE <= ?
        """.trimIndent(), arrayOf(studentId.toString(), termStartDate, termEndDate))

        var absent = 0
        var present = 0
        if (cursor.moveToFirst()) {
            absent = cursor.getInt(0)
            present = cursor.getInt(1)
        }
        cursor.close()
        return Pair(present, absent)
    }

    @Synchronized
    fun getClassDailyStats(classId: Long, date: String): Pair<Int, Int> {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT 
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 0 THEN 1 ELSE 0 END) as present_today,
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 1 THEN 1 ELSE 0 END) as absent_today
            FROM $TABLE_ATTENDANCE
            WHERE $COL_ATT_CLASS_ID = ? AND $COL_ATT_DATE = ?
        """.trimIndent(), arrayOf(classId.toString(), date))

        var present = 0
        var absent = 0
        if (cursor.moveToFirst()) {
            present = cursor.getInt(0)
            absent = cursor.getInt(1)
        }
        cursor.close()
        return Pair(present, absent)
    }

    @Synchronized
    fun getClassCumulativeStats(classId: Long, termStartDate: String, termEndDate: String): Pair<Int, Int> {
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT 
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 0 THEN 1 ELSE 0 END) as total_present,
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 1 THEN 1 ELSE 0 END) as total_absent
            FROM $TABLE_ATTENDANCE
            WHERE $COL_ATT_CLASS_ID = ? AND $COL_ATT_DATE >= ? AND $COL_ATT_DATE <= ?
        """.trimIndent(), arrayOf(classId.toString(), termStartDate, termEndDate))

        var present = 0
        var absent = 0
        if (cursor.moveToFirst()) {
            present = cursor.getInt(0)
            absent = cursor.getInt(1)
        }
        cursor.close()
        return Pair(present, absent)
    }

    @Synchronized
    fun getSchoolOverview(date: String, termStartDate: String, termEndDate: String): SchoolOverviewStats {
        val schoolName = getSetting("school_name", "مدرسة الأمل الأساسية")
        val classes = getAllClasses()
        var totalStudents = 0
        var presentToday = 0
        var absentToday = 0

        var totalCumulativePresent = 0
        var totalCumulativeAbsent = 0

        val db = readableDatabase

        // إجمالي الطلاب
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_STUDENTS WHERE $COL_STUDENT_IS_ACTIVE = 1", null)
        if (countCursor.moveToFirst()) {
            totalStudents = countCursor.getInt(0)
        }
        countCursor.close()

        // إحصائيات اليوم لكامل المدرسة
        val todayCursor = db.rawQuery("""
            SELECT 
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 0 THEN 1 ELSE 0 END),
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 1 THEN 1 ELSE 0 END)
            FROM $TABLE_ATTENDANCE
            WHERE $COL_ATT_DATE = ?
        """.trimIndent(), arrayOf(date))
        if (todayCursor.moveToFirst()) {
            presentToday = todayCursor.getInt(0)
            absentToday = todayCursor.getInt(1)
        }
        todayCursor.close()

        // التراكمي لكامل المدرسة
        val cumCursor = db.rawQuery("""
            SELECT 
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 0 THEN 1 ELSE 0 END),
                SUM(CASE WHEN $COL_ATT_IS_ABSENT = 1 THEN 1 ELSE 0 END)
            FROM $TABLE_ATTENDANCE
            WHERE $COL_ATT_DATE >= ? AND $COL_ATT_DATE <= ?
        """.trimIndent(), arrayOf(termStartDate, termEndDate))
        if (cumCursor.moveToFirst()) {
            totalCumulativePresent = cumCursor.getInt(0)
            totalCumulativeAbsent = cumCursor.getInt(1)
        }
        cumCursor.close()

        val recordedToday = presentToday + absentToday
        val todayRate = if (recordedToday > 0) (presentToday.toDouble() / recordedToday) * 100.0 else 100.0

        val recordedCum = totalCumulativePresent + totalCumulativeAbsent
        val cumRate = if (recordedCum > 0) (totalCumulativePresent.toDouble() / recordedCum) * 100.0 else 100.0

        return SchoolOverviewStats(
            schoolName = schoolName,
            totalStudents = totalStudents,
            totalClasses = classes.size,
            presentToday = presentToday,
            absentToday = absentToday,
            todayAttendanceRate = todayRate,
            cumulativeAttendanceRate = cumRate
        )
    }
}

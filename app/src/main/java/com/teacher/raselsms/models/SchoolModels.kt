package com.teacher.raselsms.models

data class SchoolClass(
    val id: Long = 0,
    val stage: String = "المرحلة الأساسية",
    val grade: String,
    val section: String
) {
    val displayName: String
        get() = if (stage.isNotBlank()) "[$stage] $grade - $section" else if (section.isNotBlank()) "$grade - $section" else grade
}

data class Student(
    val id: Long = 0,
    val classId: Long,
    val name: String,
    val idNumber: String = "",
    val phone: String,
    val isActive: Boolean = true
)

data class StudentAttendanceItem(
    val student: Student,
    var isAbsent: Boolean = false,
    var recordId: Long? = null
)

data class StudentStats(
    val student: Student,
    val presentDays: Int,
    val absentDays: Int,
    val totalSchoolDays: Int,
    val attendanceRate: Double,
    val absenceRate: Double
)

data class ClassStats(
    val schoolClass: SchoolClass,
    val totalStudents: Int,
    val presentToday: Int,
    val absentToday: Int,
    val todayAttendanceRate: Double,
    val cumulativeAttendanceRate: Double
)

data class SchoolOverviewStats(
    val schoolName: String,
    val totalStudents: Int,
    val totalClasses: Int,
    val presentToday: Int,
    val absentToday: Int,
    val todayAttendanceRate: Double,
    val cumulativeAttendanceRate: Double
)

data class Holiday(
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val title: String
)

data class SmsLogEntry(
    val id: Long = 0,
    val studentName: String,
    val phone: String,
    val message: String,
    val sentAt: String, // YYYY-MM-DD HH:mm:ss
    val status: String, // "SENT" or "FAILED"
    val simSlot: Int = 0,
    val category: String = "ABSENCE" // "ABSENCE" or "GENERAL"
)

data class CalendarValidationResult(
    val isValidForAttendance: Boolean,
    val message: String,
    val isFriday: Boolean = false,
    val isOfficialHoliday: Boolean = false,
    val isFutureDate: Boolean = false,
    val isBeforeSchoolStart: Boolean = false,
    val holidayTitle: String = ""
)

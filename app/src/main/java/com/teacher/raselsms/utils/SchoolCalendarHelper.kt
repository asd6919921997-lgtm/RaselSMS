package com.teacher.raselsms.utils

import com.teacher.raselsms.database.SchoolDbHelper
import com.teacher.raselsms.models.CalendarValidationResult
import java.text.SimpleDateFormat
import java.util.*

object SchoolCalendarHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    fun getTodayDateString(): String {
        return dateFormat.format(Date())
    }

    fun formatDateForDisplay(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr) ?: return dateStr
            val displayFormat = SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar"))
            displayFormat.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    /**
     * التحقق الصارم والذكي من صلاحية اليوم للرصد:
     * 1. منع أيام الجمعة وإظهار (يوم جمعة - عطلة رسمية)
     * 2. منع الأيام المستقبلية وإظهار (لا يمكن رصد الغياب ليوم لم يأتِ بعد 😊⏳)
     * 3. منع الأيام السابقة لبدء الدوام وإظهار (يوم سابق لدوام الطلاب)
     * 4. فحص الإجازات الرسمية والعطل وإظهار (إجازة رسمية بمناسبة ...)
     */
    fun validateAttendanceDate(dateStr: String, dbHelper: SchoolDbHelper): CalendarValidationResult {
        return try {
            val selectedDate = dateFormat.parse(dateStr) ?: return CalendarValidationResult(
                isValidForAttendance = false,
                message = "تاريخ غير صالح"
            )

            val cal = Calendar.getInstance().apply { time = selectedDate }

            // 1. فحص هل هو يوم جمعة
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                return CalendarValidationResult(
                    isValidForAttendance = false,
                    message = "يوم جمعة - عطلة رسمية",
                    isFriday = true
                )
            }

            // 2. فحص الأيام المستقبلية
            val todayStr = getTodayDateString()
            val todayDate = dateFormat.parse(todayStr)!!
            if (selectedDate.after(todayDate)) {
                return CalendarValidationResult(
                    isValidForAttendance = false,
                    message = "لا يمكن رصد الغياب ليوم لم يأتِ بعد 😊⏳",
                    isFutureDate = true
                )
            }

            // 3. فحص البداية الرسمية لدوام الطلاب
            val term1StartStr = dbHelper.getSetting("term1_start", "2026-09-01")
            val term1StartDate = dateFormat.parse(term1StartStr)
            if (term1StartDate != null && selectedDate.before(term1StartDate)) {
                return CalendarValidationResult(
                    isValidForAttendance = false,
                    message = "يوم سابق لدوام الطلاب",
                    isBeforeSchoolStart = true
                )
            }

            // 4. فحص إجازة ما بين الفصلين
            val vacStartStr = dbHelper.getSetting("vacation_start", "")
            val vacEndStr = dbHelper.getSetting("vacation_end", "")
            if (vacStartStr.isNotBlank() && vacEndStr.isNotBlank()) {
                val vacStart = dateFormat.parse(vacStartStr)
                val vacEnd = dateFormat.parse(vacEndStr)
                if (vacStart != null && vacEnd != null && !selectedDate.before(vacStart) && !selectedDate.after(vacEnd)) {
                    return CalendarValidationResult(
                        isValidForAttendance = false,
                        message = "إجازة رسمية (إجازة ما بين الفصلين الدراسيين)",
                        isOfficialHoliday = true,
                        holidayTitle = "إجازة ما بين الفصلين"
                    )
                }
            }

            // 5. فحص الإجازات والعطلات المسجلة في قاعدة البيانات
            val holiday = dbHelper.getHolidayForDate(dateStr)
            if (holiday != null) {
                return CalendarValidationResult(
                    isValidForAttendance = false,
                    message = "إجازة رسمية بمناسبة (${holiday.title})",
                    isOfficialHoliday = true,
                    holidayTitle = holiday.title
                )
            }

            // اليوم سليم وجاهز للرصد
            CalendarValidationResult(
                isValidForAttendance = true,
                message = "يوم دوام رسمي صالح للرصد"
            )
        } catch (e: Exception) {
            CalendarValidationResult(
                isValidForAttendance = true,
                message = "صالح للرصد"
            )
        }
    }

    /**
     * حساب عدد أيام الدوام الفعلي بين تاريخين (باستثناء أيام الجمعة والإجازات المسجلة)
     */
    fun countActualSchoolDays(startDateStr: String, endDateStr: String, holidays: Set<String>): Int {
        return try {
            val start = dateFormat.parse(startDateStr) ?: return 1
            val end = dateFormat.parse(endDateStr) ?: return 1
            if (start.after(end)) return 0

            val cal = Calendar.getInstance().apply { time = start }
            val endCal = Calendar.getInstance().apply { time = end }

            var count = 0
            while (!cal.after(endCal)) {
                val currentStr = dateFormat.format(cal.time)
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

                // استثناء الجمعة والعطل
                if (dayOfWeek != Calendar.FRIDAY && !holidays.contains(currentStr)) {
                    count++
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            if (count == 0) 1 else count
        } catch (e: Exception) {
            1
        }
    }
}

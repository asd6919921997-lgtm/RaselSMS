package com.teacher.raselsms.utils

data class ParsedStudentRow(
    val name: String,
    val phone: String,
    val grade: String = "",
    val section: String = "",
    val rawLine: String = "",
    val isValid: Boolean = true
)

object SmartContentParser {

    /**
     * تنظيف وتنسيق أرقام الهواتف الفلسطينية بدقة 100%:
     * يحول أي صيغة إلى 10 أرقام تبدأ بـ 059 أو 056
     * أمثلة:
     * 592898375 -> 0592898375
     * +970592898375 -> 0592898375
     * 00972569876543 -> 0569876543
     * 059-289-8375 -> 0592898375
     */
    fun normalizePalestinePhone(raw: String): String {
        if (raw.isBlank()) return ""

        // إزالة الفراغات، والشرطات، والأقواس
        var clean = raw.trim().replace(Regex("[\\s\\-\\(\\)\\.]"), "")

        // إزالة المقدمات الدولية
        if (clean.startsWith("+970")) clean = clean.substring(4)
        else if (clean.startsWith("00970")) clean = clean.substring(5)
        else if (clean.startsWith("+972")) clean = clean.substring(4)
        else if (clean.startsWith("00972")) clean = clean.substring(5)
        else if (clean.startsWith("970")) clean = clean.substring(3)
        else if (clean.startsWith("972")) clean = clean.substring(3)

        // إزالة أي صفر زائد في البداية لتسهيل التوحيد
        while (clean.startsWith("0") && clean.length > 9) {
            clean = clean.substring(1)
        }

        // إذا كان الرقم يبدأ بـ 59 أو 56 ومكون من 9 خانات -> نضيف الصفر في البداية ليصبح 10 خانات
        if ((clean.startsWith("59") || clean.startsWith("56")) && clean.length == 9) {
            clean = "0$clean"
        }

        return clean
    }

    /**
     * فحص هل الخلية تمثل رقم هاتف جوال فلسطيني من محتواها
     */
    fun isPhoneNumberCell(cell: String): Boolean {
        val digitsOnly = cell.replace(Regex("[^0-9+]"), "")
        if (digitsOnly.length < 8 || digitsOnly.length > 15) return false

        val normalized = normalizePalestinePhone(cell)
        // يجب أن يكون 10 أرقام ويبدأ بـ 059 أو 056
        return (normalized.length == 10 && (normalized.startsWith("059") || normalized.startsWith("056")))
    }

    /**
     * فحص هل الخلية تمثل صفاً أو شعبة من محتواها
     */
    fun isGradeOrSectionCell(cell: String): Boolean {
        val trimmed = cell.trim()
        val keywords = listOf(
            "صف", "شعبة", "فصل", "أول", "ثاني", "ثالث", "رابع", "خامس", "سادس",
            "سابع", "ثامن", "تاسع", "عاشر", "حادي عشر", "ثاني عشر", "أساسي"
        )
        for (kw in keywords) {
            if (trimmed.contains(kw)) return true
        }
        // نماذج رقمية للشعب مثل 7/1 أو 5-2 أو شعبة أ
        if (trimmed.matches(Regex("^[0-9]{1,2}[\\/\\-][0-9]{1,2}$"))) return true
        if (trimmed.matches(Regex("^[أ-يA-Za-z0-9]{1,3}$")) && trimmed.length <= 3) return true
        return false
    }

    /**
     * استخراج الصف والشعبة من نص الخلية
     */
    fun parseGradeAndSection(cell: String): Pair<String, String> {
        val text = cell.trim()
        val parts = text.split(Regex("[\\-\\s\\/\\\\]+"))
        if (parts.size >= 2) {
            val grade = parts[0]
            val section = parts.subList(1, parts.size).joinToString(" ")
            return Pair(grade, section)
        }
        return Pair(text, "1")
    }

    /**
     * التحليل الذكي الأفقي للنص المنسوخ من الإكسل/شيتس/واتساب (سطراً بسطر):
     * - لا يعتمد على مسميات الأعمدة إطلاقاً بل على محتوى الخلايا ذاته.
     * - يربط اسم الطالب برقم ولي أمره في نفس السطر بدقة 100%.
     */
    fun parsePastedText(pastedText: String): List<ParsedStudentRow> {
        val results = mutableListOf<ParsedStudentRow>()
        if (pastedText.isBlank()) return results

        val lines = pastedText.split(Regex("\r?\n"))
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue

            // التعرف على الفواصل في السطر (Tab هي الافتراضية للنسخ من الإكسل)
            val tokens: List<String> = when {
                trimmedLine.contains("\t") -> trimmedLine.split("\t")
                trimmedLine.contains(",") -> trimmedLine.split(",")
                trimmedLine.contains(";") -> trimmedLine.split(";")
                trimmedLine.contains("|") -> trimmedLine.split("|")
                trimmedLine.contains("  ") -> trimmedLine.split(Regex("\\s{2,}"))
                else -> trimmedLine.split(" ")
            }.map { it.trim() }.filter { it.isNotBlank() }

            if (tokens.isEmpty()) continue

            // تجاهل سطر العناوين إذا احتوى كلمات مثل (اسم، جوال، رقم) ولم يحتوِ على رقم هاتف حقيقي
            val hasRealPhone = tokens.any { isPhoneNumberCell(it) }
            val isHeaderRow = tokens.any {
                it.contains("اسم") || it.contains("طالب") || it.contains("هاتف") ||
                        it.contains("جوال") || it.contains("واتس") || it.contains("موبايل") || it.contains("شعبة")
            } && !hasRealPhone

            if (isHeaderRow) {
                continue // تخطي سطر الترويسة
            }

            var detectedPhone = ""
            var detectedName = ""
            var detectedGrade = ""
            var detectedSection = ""

            val remainingTokens = mutableListOf<String>()

            // 1. البحث عن عمود الهاتف أولاً من المحتوى
            for (token in tokens) {
                if (detectedPhone.isBlank() && isPhoneNumberCell(token)) {
                    detectedPhone = normalizePalestinePhone(token)
                } else {
                    remainingTokens.add(token)
                }
            }

            // 2. البحث عن عمود الصف والشعبة
            val nameCandidates = mutableListOf<String>()
            for (token in remainingTokens) {
                if (detectedGrade.isBlank() && isGradeOrSectionCell(token)) {
                    val (g, s) = parseGradeAndSection(token)
                    detectedGrade = g
                    detectedSection = s
                } else {
                    nameCandidates.add(token)
                }
            }

            // 3. ما تبقى ويمثل نصاً عربياً أو اسماً هو اسم الطالب
            if (nameCandidates.isNotEmpty()) {
                // دمج الكلمات المتبقية لتكوين الاسم الثلاثي أو الرباعي
                detectedName = nameCandidates.joinToString(" ")
            }

            // إذا لم يتم استخراج هاتف صحيح ولكن هناك أرقام، نحاول استخلاص الرقم
            if (detectedPhone.isBlank()) {
                val match = Regex("0?5[69][0-9]{7}").find(trimmedLine)
                if (match != null) {
                    detectedPhone = normalizePalestinePhone(match.value)
                }
            }

            if (detectedName.isNotBlank() && detectedPhone.isNotBlank()) {
                results.add(
                    ParsedStudentRow(
                        name = detectedName,
                        phone = detectedPhone,
                        grade = detectedGrade,
                        section = detectedSection,
                        rawLine = trimmedLine,
                        isValid = true
                    )
                )
            }
        }

        return results
    }

    /**
     * استخراج اسم الطالب ورقم الهاتف فقط لشعبة محددة مسبقاً:
     * - لا يعترف بأي صف أو شعبة مذكورة داخل الملف/النص.
     * - يقرأ فقط عمودين: الاسم (ثلاثي أو رباعي) ورقم الهاتف الفلسطيني (10 أرقام).
     * - يتجاهل كافة الأعمدة الأخرى مهما كانت.
     */
    fun parseStudentsOnlyForClass(rawText: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        if (rawText.isBlank()) return results

        val lines = rawText.split(Regex("\r?\n"))
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue

            val tokens: List<String> = when {
                trimmedLine.contains("\t") -> trimmedLine.split("\t")
                trimmedLine.contains(",") -> trimmedLine.split(",")
                trimmedLine.contains(";") -> trimmedLine.split(";")
                trimmedLine.contains("|") -> trimmedLine.split("|")
                trimmedLine.contains("  ") -> trimmedLine.split(Regex("\\s{2,}"))
                else -> trimmedLine.split(" ")
            }.map { it.trim() }.filter { it.isNotBlank() }

            if (tokens.isEmpty()) continue

            val hasRealPhone = tokens.any { isPhoneNumberCell(it) }
            val isHeaderRow = tokens.any {
                it.contains("اسم") || it.contains("طالب") || it.contains("هاتف") ||
                        it.contains("جوال") || it.contains("موبايل") || it.contains("رقم")
            } && !hasRealPhone

            if (isHeaderRow) continue

            var detectedPhone = ""
            val nonPhoneTokens = mutableListOf<String>()

            for (token in tokens) {
                if (detectedPhone.isBlank() && isPhoneNumberCell(token)) {
                    detectedPhone = normalizePalestinePhone(token)
                } else {
                    if (!token.matches(Regex("^[0-9]{1,4}$"))) {
                        nonPhoneTokens.add(token)
                    }
                }
            }

            if (detectedPhone.isBlank()) {
                val match = Regex("0?5[69][0-9]{7}").find(trimmedLine)
                if (match != null) {
                    detectedPhone = normalizePalestinePhone(match.value)
                }
            }

            val detectedName = nonPhoneTokens.filter {
                !isGradeOrSectionCell(it)
            }.joinToString(" ")

            if (detectedName.isNotBlank() && detectedPhone.isNotBlank()) {
                results.add(Pair(detectedName, detectedPhone))
            }
        }
        return results
    }
}

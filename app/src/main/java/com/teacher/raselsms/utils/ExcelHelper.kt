package com.teacher.raselsms.utils

import android.content.Context
import android.net.Uri
import com.teacher.raselsms.models.Recipient
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object ExcelHelper {

    /**
     * القارئ الشامل لكافة أنواع وامتدادات الإكسل:
     * 1. ملفات .xlsx الحديثة (OpenXML).
     * 2. ملفات .csv و .txt (مفصولة بفواصل أو Tab).
     * 3. ملفات .xls الصادرة من المنظومات المدرسية (سواء كـ HTML Table أو CSV أو نص).
     */
    fun parseFile(context: Context, uri: Uri): List<Recipient> {
        val fileName = getFileName(context, uri).lowercase()
        val allRows = mutableListOf<List<String>>()

        // فحص هل الملف هو أرشيف Zip (ملف .xlsx حقيقي)
        val isZipXlsx = isZipFile(context, uri)

        if (isZipXlsx) {
            try {
                allRows.addAll(parseXlsx(context, uri))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // إذا لم يكن .xlsx أو فشل، نقرأه كملف نصي / HTML Table / CSV
        if (allRows.isEmpty()) {
            try {
                val textContent = readTextFromUri(context, uri)
                if (textContent.contains("<table", ignoreCase = true) || textContent.contains("<tr", ignoreCase = true)) {
                    // معالجة ملفات .xls التي تصدرها المنظومات كجدول HTML
                    allRows.addAll(parseHtmlTable(textContent))
                } else {
                    // معالجة ملفات CSV والنصوص
                    allRows.addAll(parseDelimitedText(textContent))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // تحويل الصفوف الأفقية بدقة متناهية لكل صف بمفرده لمنع أي تداخل أو خربطة
        return processRowsSafely(allRows)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result = ""
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = it.getString(index) ?: ""
                }
            }
        }
        return if (result.isNotBlank()) result else (uri.lastPathSegment ?: "file.xlsx")
    }

    private fun isZipFile(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(4)
                val read = stream.read(buffer)
                // صيغة ملفات Zip تبدأ بـ PK (0x50, 0x4B, 0x03, 0x04)
                read == 4 && buffer[0] == 0x50.toByte() && buffer[1] == 0x4B.toByte()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun readTextFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            // محاولة القراءة بترميز UTF-8 أولاً
            var str = String(bytes, Charset.forName("UTF-8"))
            // إذا ظهرت رموز غريبة، نحاول بترميز Windows-1256 العربي
            if (str.contains("")) {
                try {
                    str = String(bytes, Charset.forName("windows-1256"))
                } catch (e: Exception) {
                    // البقاء على UTF-8
                }
            }
            str
        } ?: ""
    }

    /**
     * قراءة جداول HTML المدمجة في ملفات .xls الصادرة من المنظومات المدرسية
     */
    private fun parseHtmlTable(html: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL or RegexOption.IGNORE_CASE)
        val cellRegex = Regex("<t[dh][^>]*>(.*?)</t[dh]>", RegexOption.DOT_MATCHES_ALL or RegexOption.IGNORE_CASE)

        val rowMatches = rowRegex.findAll(html)
        for (rowMatch in rowMatches) {
            val rowContent = rowMatch.groupValues[1]
            val cellMatches = cellRegex.findAll(rowContent)
            val rowCells = mutableListOf<String>()
            for (cellMatch in cellMatches) {
                // تنظيف نصوص الخلايا من وسوم HTML ورموز الفراغات
                val cellText = cellMatch.groupValues[1]
                    .replace(Regex("<[^>]*>"), "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .trim()
                rowCells.add(cellText)
            }
            if (rowCells.isNotEmpty()) {
                rows.add(rowCells)
            }
        }
        return rows
    }

    private fun parseDelimitedText(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val reader = BufferedReader(StringReader(text))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.let { rawLine ->
                if (rawLine.isNotBlank()) {
                    val delimiter = if (rawLine.contains("\t")) "\t" else if (rawLine.contains(";")) ";" else ","
                    val tokens = rawLine.split(delimiter).map { it.trim().removeSurrounding("\"") }
                    rows.add(tokens)
                }
            }
        }
        return rows
    }

    /**
     * قراءة ملفات .xlsx الأصلية
     */
    private fun parseXlsx(context: Context, uri: Uri): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<List<String>>()

        // قراءة النصوص المشتركة
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.equals("xl/sharedStrings.xml", ignoreCase = true)) {
                    readSharedStrings(zip, sharedStrings)
                    break
                }
                entry = zip.nextEntry
            }
        }

        // قراءة أول ورقة عمل
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml", RegexOption.IGNORE_CASE))) {
                    readSheetXml(zip, sharedStrings, sheetRows)
                    break
                }
                entry = zip.nextEntry
            }
        }

        return sheetRows
    }

    private fun readSharedStrings(inputStream: InputStream, list: MutableList<String>) {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var inTextTag = false
        var currentText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("t", ignoreCase = true)) {
                        inTextTag = true
                        currentText.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTextTag) currentText.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("t", ignoreCase = true)) {
                        inTextTag = false
                        list.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun readSheetXml(
        inputStream: InputStream,
        sharedStrings: List<String>,
        rows: MutableList<List<String>>
    ) {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentRow = mutableMapOf<Int, String>()
        var currentCellRef = ""
        var cellType = ""
        var inValueTag = false
        var cellValue = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    if (tagName.equals("row", ignoreCase = true)) {
                        currentRow = mutableMapOf()
                    } else if (tagName.equals("c", ignoreCase = true)) {
                        currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellValue.setLength(0)
                    } else if (tagName.equals("v", ignoreCase = true) || tagName.equals("t", ignoreCase = true)) {
                        inValueTag = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValueTag) cellValue.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    val tagName = parser.name
                    if (tagName.equals("v", ignoreCase = true) || tagName.equals("t", ignoreCase = true)) {
                        inValueTag = false
                        val colIndex = columnRefToIndex(currentCellRef)
                        var value = cellValue.toString().trim()
                        if (cellType == "s") {
                            val stringIndex = value.toIntOrNull()
                            if (stringIndex != null && stringIndex in sharedStrings.indices) {
                                value = sharedStrings[stringIndex]
                            }
                        }
                        currentRow[colIndex] = value
                    } else if (tagName.equals("row", ignoreCase = true)) {
                        if (currentRow.isNotEmpty()) {
                            val maxCol = currentRow.keys.maxOrNull() ?: 0
                            val rowList = ArrayList<String>()
                            for (c in 0..maxCol) {
                                rowList.add(currentRow[c] ?: "")
                            }
                            rows.add(rowList)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun columnRefToIndex(cellRef: String): Int {
        val letters = cellRef.filter { it.isLetter() }.uppercase()
        var index = 0
        for (ch in letters) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return (index - 1).coerceAtLeast(0)
    }

    /**
     * المعالجة الأفقية الدقيقة (صف بصف):
     * يفحص كل صف أفقي بمفرده تماماً.
     * يستخرج الاسم ورقم الهاتف من نفس الصف بدون أي احتمالية للتداخل أو إزاحة الأرقام.
     */
    private fun processRowsSafely(rows: List<List<String>>): List<Recipient> {
        if (rows.isEmpty()) return emptyList()

        val recipients = mutableListOf<Recipient>()
        var idCounter = 1

        for (rowIndex in rows.indices) {
            val row = rows[rowIndex]
            if (row.isEmpty()) continue

            // البحث داخل نفس الصف الأفقي عن الخلية التي تمثل الهاتف والخلية التي تمثل الاسم
            var detectedPhone = ""
            var detectedName = ""

            for (cell in row) {
                val cleanVal = cell.trim()
                if (cleanVal.isBlank()) continue

                // فحص هل الخلية تحمل صفات رقم هاتف
                if (detectedPhone.isBlank() && isPhoneCandidate(cleanVal)) {
                    detectedPhone = cleanVal
                } else if (detectedName.isBlank() && hasArabicOrLetters(cleanVal) && !isHeaderWord(cleanVal)) {
                    detectedName = cleanVal
                }
            }

            // إذا كان الصف عبارة عن عناوين (مثل "اسم الطالب"، "رقم الهاتف")، نتجاهله
            if (isHeaderRow(row)) continue

            // إذا وجدنا رقماً أو اسماً في هذا الصف
            if (detectedPhone.isNotBlank() || detectedName.isNotBlank()) {
                val finalName = if (detectedName.isNotBlank()) detectedName else "طالب $idCounter"
                val cleanPhone = normalizePhoneNumber(detectedPhone)
                val isValid = isValidPhone(cleanPhone)

                recipients.add(
                    Recipient(
                        id = idCounter++,
                        name = finalName,
                        rawPhone = detectedPhone,
                        cleanPhone = cleanPhone,
                        isValid = isValid
                    )
                )
            }
        }

        return recipients
    }

    /**
     * تنظيف وضبط أرقام الهواتف بأعلى ذكاء:
     * - تحويل الأرقام العربية ٠-٩ إلى 0-9.
     * - تحويل +970 و +972 و 00970 و 00972 إلى أرقام محلية قياسية (059xxxxxxx أو 056xxxxxxx).
     * - إكمال الصفر إذا كان مفقوداً في البداية (مثل 592898375 تصبح 0592898375).
     * - تنظيف علامات الاتجاه والمسافات والرموز المخفية.
     */
    fun normalizePhoneNumber(phone: String): String {
        var p = phone.trim()

        // إزالة الحروف المخفية واتجاه النص في يونيكود
        p = p.replace("\u200E", "").replace("\u200F", "").replace("\uFEFF", "")

        // تحويل الأرقام العربية الهندية ٠-٩ إلى 0-9
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in arabicDigits.indices) {
            p = p.replace(arabicDigits[i], ('0' + i))
        }

        // الإبقاء على الأرقام وعلامة + فقط
        var cleaned = p.filter { it.isDigit() || it == '+' }

        // تحويل 00 الدولية إلى +
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2)
        }

        // تحويل المقدمات الدولية (+970 و +972) إلى الصفر المحلي القياسي
        if (cleaned.startsWith("+970")) {
            cleaned = "0" + cleaned.substring(4)
        } else if (cleaned.startsWith("+972")) {
            cleaned = "0" + cleaned.substring(4)
        } else if (cleaned.startsWith("970") && cleaned.length >= 11) {
            cleaned = "0" + cleaned.substring(3)
        } else if (cleaned.startsWith("972") && cleaned.length >= 11) {
            cleaned = "0" + cleaned.substring(3)
        }

        // إذا كان الرقم 9 أرقام ويبدأ بـ 5 (مثل 592898375 أو 56xxxxxxx)، يُكمل الصفر تلقائياً ليصبح 0592898375!
        if (cleaned.startsWith("5") && cleaned.length == 9) {
            cleaned = "0$cleaned"
        }

        return cleaned
    }

    private fun isPhoneCandidate(valStr: String): Boolean {
        // تحويل الأرقام العربية أولاً للفحص
        var s = valStr.trim()
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in arabicDigits.indices) {
            s = s.replace(arabicDigits[i], ('0' + i))
        }

        val digits = s.filter { it.isDigit() }
        // رقم هاتف محتمل: يبدأ بـ 05 أو 5 أو 970 أو 972 وطوله مناسب
        if (digits.length in 8..15) {
            if (digits.startsWith("05") || digits.startsWith("5") ||
                digits.startsWith("970") || digits.startsWith("972") ||
                s.startsWith("+970") || s.startsWith("+972")) {
                return true
            }
            return digits.length >= 9
        }
        return false
    }

    private fun hasArabicOrLetters(valStr: String): Boolean {
        return valStr.any { it.isLetter() }
    }

    private fun isHeaderWord(valStr: String): Boolean {
        val s = valStr.lowercase()
        return s.contains("اسم") || s.contains("طالب") || s.contains("هاتف") ||
               s.contains("جوال") || s.contains("ولي") || s.contains("name") || s.contains("phone")
    }

    private fun isHeaderRow(row: List<String>): Boolean {
        var headerScore = 0
        for (cell in row) {
            if (isHeaderWord(cell)) headerScore++
        }
        return headerScore >= 1 && row.none { isPhoneCandidate(it) && it.filter { c -> c.isDigit() }.length >= 9 }
    }

    fun isValidPhone(phone: String): Boolean {
        val pureDigits = phone.filter { it.isDigit() }
        return pureDigits.length in 9..15
    }
}

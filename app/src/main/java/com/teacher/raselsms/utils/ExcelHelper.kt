package com.teacher.raselsms.utils

import android.content.Context
import android.net.Uri
import com.teacher.raselsms.models.Recipient
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object ExcelHelper {

    /**
     * استيراد البيانات سواء كان الملف إكسل (.xlsx) أو ملف نصي (.csv)
     */
    fun parseFile(context: Context, uri: Uri): List<Recipient> {
        val fileName = getFileName(context, uri).lowercase()
        return if (fileName.endsWith(".csv") || fileName.endsWith(".txt")) {
            parseCsv(context, uri)
        } else {
            // المعالجة الافتراضية كملف إكسل حديث .xlsx
            try {
                parseXlsx(context, uri)
            } catch (e: Exception) {
                // محاولة القراءة كـ CSV كبديل في حال تم حفظه بصيغة أخرى
                parseCsv(context, uri)
            }
        }
    }

    /**
     * استخراج اسم الملف من الـ URI
     */
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

    /**
     * قراءة ملفات CSV مع دعم الترميز العربي (UTF-8 و Windows-1256)
     */
    private fun parseCsv(context: Context, uri: Uri): List<Recipient> {
        val rows = mutableListOf<List<String>>()
        
        // قراءة الملف وفحص الفواصل الشائعة (فاصلة عادية ، أو منقوطة ؛ أو Tab)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8")))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { rawLine ->
                    if (rawLine.isNotBlank()) {
                        val delimiter = if (rawLine.contains(";")) ";" else if (rawLine.contains("\t")) "\t" else ","
                        val tokens = rawLine.split(delimiter).map { it.trim().removeSurrounding("\"") }
                        rows.add(tokens)
                    }
                }
            }
        }

        return convertRowsToRecipients(rows)
    }

    /**
     * قراءة ملفات Excel (.xlsx) عبر معالج OpenXML الداخلي في أندرويد
     * خفيف جداً، فائق السرعة، ولا يتطلب أي مكتبات خارجية ضخمة.
     */
    private fun parseXlsx(context: Context, uri: Uri): List<Recipient> {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<List<String>>()

        // الخطوة 1: استخراج النصوص المشتركة sharedStrings.xml
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

        // الخطوة 2: قراءة بيانات الورقة الأولى sheet1.xml
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

        return convertRowsToRecipients(sheetRows)
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
                    if (inTextTag) {
                        currentText.append(parser.text)
                    }
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
                    if (inValueTag) {
                        cellValue.append(parser.text)
                    }
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
     * الكشف الذكي عن عمود الاسم ورقم الجوال وتحويل الأسطر إلى قائمة Recipient
     */
    private fun convertRowsToRecipients(rows: List<List<String>>): List<Recipient> {
        if (rows.isEmpty()) return emptyList()

        var nameCol = -1
        var phoneCol = -1
        var startRow = 0

        // فحص صف العناوين في أول 3 أسطر
        for (r in 0 until minOf(3, rows.size)) {
            val row = rows[r]
            for (c in row.indices) {
                val header = row[c].trim().lowercase()
                if (nameCol == -1 && (header.contains("اسم") || header.contains("طالب") || header.contains("name") || header.contains("student"))) {
                    nameCol = c
                }
                if (phoneCol == -1 && (header.contains("جوال") || header.contains("هاتف") || header.contains("موبايل") ||
                            header.contains("phone") || header.contains("mobile") || header.contains("ولي") || header.contains("رقم"))) {
                    phoneCol = c
                }
            }
            if (nameCol != -1 && phoneCol != -1) {
                startRow = r + 1
                break
            }
        }

        // إذا لم نجد العناوين بوضوح، نفترض أن العمود الأول هو الاسم والعمود الثاني هو الهاتف
        if (nameCol == -1 || phoneCol == -1) {
            nameCol = 0
            phoneCol = 1
            // إذا كان السطر الأول نصياً نعتبره عنواناً
            if (rows.isNotEmpty() && rows[0].size > 1 && !looksLikePhone(rows[0][1])) {
                startRow = 1
            }
        }

        val recipients = mutableListOf<Recipient>()
        var idCounter = 1

        for (i in startRow until rows.size) {
            val row = rows[i]
            val name = if (nameCol in row.indices) row[nameCol].trim() else "طالب $idCounter"
            val rawPhone = if (phoneCol in row.indices) row[phoneCol].trim() else ""

            if (name.isBlank() && rawPhone.isBlank()) continue

            val cleanPhone = normalizePhoneNumber(rawPhone)
            val isValid = isValidPhone(cleanPhone)

            recipients.add(
                Recipient(
                    id = idCounter++,
                    name = if (name.isNotBlank()) name else "طالب بدون اسم",
                    rawPhone = rawPhone,
                    cleanPhone = cleanPhone,
                    isValid = isValid
                )
            )
        }

        return recipients
    }

    /**
     * تنظيف الأرقام، تحويل الأرقام العربية إلى إنجليزية، وإزالة الرموز
     */
    fun normalizePhoneNumber(phone: String): String {
        var p = phone
        // تحويل الأرقام العربية الهندية ٠-٩ إلى 0-9
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        for (i in arabicDigits.indices) {
            p = p.replace(arabicDigits[i], ('0' + i))
        }

        // إبقاء الأرقام وعلامة + فقط
        val digits = p.filter { it.isDigit() || it == '+' }
        
        // إزالة الأصفار الدولية 00 في البداية واستبدالها بـ +
        var cleaned = digits
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2)
        }
        
        // الأرقام في السعودية تبدأ غالباً بـ 05
        // إذا كان الرقم 9 أرقام بدون الصفر (5xxxxxxxx)، نضيف الصفر ليصبح 05xxxxxxxx
        if (cleaned.startsWith("5") && cleaned.length == 9) {
            cleaned = "0$cleaned"
        }

        return cleaned
    }

    /**
     * فحص هل الرقم صالح للإرسال
     */
    fun isValidPhone(phone: String): Boolean {
        val pureDigits = phone.filter { it.isDigit() }
        // رقم الهاتف عادة لا يقل عن 9 أرقام ولا يزيد عن 15 رقماً
        return pureDigits.length in 9..15
    }

    private fun looksLikePhone(value: String): Boolean {
        val digits = value.filter { it.isDigit() }
        return digits.length >= 7
    }
}

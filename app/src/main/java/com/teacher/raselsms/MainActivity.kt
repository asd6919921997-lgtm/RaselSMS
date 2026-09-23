package com.teacher.raselsms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.webkit.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.teacher.raselsms.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // مستقبِل اختيار ملفات الإكسل (Excel)
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, intent)
            filePathCallback?.onReceiveValue(uris)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    // مستقبِل اختيار جهات الاتصال من الهاتف
    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { handleContactPicked(it) }
    }

    // مستقبِل الصلاحيات الرسمية
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] == true
        if (smsGranted) {
            Toast.makeText(this, "✓ تم تفعيل صلاحيات إرسال الرسائل SMS بنجاح", Toast.LENGTH_SHORT).show()
            passSimsToWeb()
        } else {
            Toast.makeText(this, "⚠️ تنبيه: صلاحية إرسال الرسائل SMS مطلوبة لإرسال الإشعارات لأولياء الأمور", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // معالجة زر الرجوع الذكي
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("نظام راسل المدرسي")
                        .setMessage("هل ترغب في الخروج من التطبيق؟")
                        .setPositiveButton("خروج") { _, _ -> finish() }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            }
        })

        // فحص الصلاحيات عند الانطلاق
        checkAndRequestPermissions()

        // تهيئة المتصفح الداخلي المدمج (Native Hybrid WebView)
        setupWebView()
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_CONTACTS)
        }

        if (needed.isNotEmpty()) {
            requestPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // ربط الجسر البرمجي مع كود الجافاسكريبت
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        // معالج متقدم للـ ChromeClient لدعم النوافذ واختيار الملفات
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                    filePickerLauncher.launch(intent)
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    return false
                }
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("نظام راسل المدرسي")
                    .setMessage(message ?: "")
                    .setPositiveButton("موافق") { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("تأكيد العملية")
                    .setMessage(message ?: "")
                    .setPositiveButton("موافق") { _, _ -> result?.confirm() }
                    .setNegativeButton("إلغاء") { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                val input = EditText(this@MainActivity)
                input.setText(defaultValue ?: "")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("إدخال بيانات")
                    .setMessage(message ?: "")
                    .setView(input)
                    .setPositiveButton("موافق") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("إلغاء") { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }
        }

        // معالج الروابط والتنقل
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // تمرير شرائح الاتصال للواجهة
                passSimsToWeb()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val urlStr = uri.toString()

                if (urlStr.startsWith("whatsapp:") || urlStr.contains("api.whatsapp.com")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "تطبيق واتساب غير مثبت على الهاتف", Toast.LENGTH_SHORT).show()
                        return true
                    }
                } else if (urlStr.startsWith("tel:")) {
                    try {
                        val intent = Intent(Intent.ACTION_DIAL, uri)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return true
                    }
                }
                return false
            }
        }

        // تحميل ملف المعاينة الكامل 100% محلياً وأوفلاين
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun passSimsToWeb() {
        val simsArray = JSONArray()
        val detected = getActiveSimCards()
        if (detected.isNotEmpty()) {
            detected.forEachIndexed { idx, sim ->
                val obj = JSONObject()
                obj.put("slot", idx)
                obj.put("name", sim.displayName ?: "شريحة ${idx + 1}")
                simsArray.put(obj)
            }
        } else {
            val s1 = JSONObject()
            s1.put("slot", 0)
            s1.put("name", "SIM 1 (Jawwal)")
            simsArray.put(s1)

            val s2 = JSONObject()
            s2.put("slot", 1)
            s2.put("name", "SIM 2 (Ooredoo)")
            simsArray.put(s2)
        }

        val jsonStr = simsArray.toString()
        binding.webView.post {
            binding.webView.evaluateJavascript(
                "if (typeof window.onNativeSimsLoaded === 'function') { window.onNativeSimsLoaded('$jsonStr'); }",
                null
            )
        }
    }

    private fun getActiveSimCards(): List<SubscriptionInfo> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val subManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager ?: return emptyList()
        return try {
            subManager.activeSubscriptionInfoList ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun handleContactPicked(uri: Uri) {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)

                    val contactId = cursor.getString(idIndex)
                    val contactName = if (nameIndex != -1) cursor.getString(nameIndex) else "جهة اتصال"
                    val hasPhone = if (hasPhoneIndex != -1) cursor.getInt(hasPhoneIndex) else 0

                    if (hasPhone > 0) {
                        contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(contactId),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numberIndex != -1) {
                                    val phoneNumber = phoneCursor.getString(numberIndex)
                                    val safeName = contactName.replace("'", "\\'")
                                    val safePhone = phoneNumber.replace("'", "\\'")
                                    binding.webView.post {
                                        binding.webView.evaluateJavascript(
                                            "if (typeof window.onNativeContactPicked === 'function') { window.onNativeContactPicked('$safeName', '$safePhone'); }",
                                            null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر قراءة جهة الاتصال: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // جسر الأندرويد البرمجي لربط الجافاسكريبت بمكونات الهاتف الأصلية
    inner class AndroidBridge {

        @JavascriptInterface
        fun sendSms(phone: String, message: String, simSlot: Int): Boolean {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread {
                    checkAndRequestPermissions()
                    Toast.makeText(this@MainActivity, "يرجى منح إذن إرسال الرسائل SMS أولاً", Toast.LENGTH_SHORT).show()
                }
                return false
            }

            return try {
                val activeSims = getActiveSimCards()
                val smsManager = if (activeSims.isNotEmpty() && simSlot in activeSims.indices) {
                    val subId = activeSims[simSlot].subscriptionId
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(subId)
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                }

                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        @JavascriptInterface
        fun pickContact() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionsLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    return@runOnUiThread
                }
                pickContactLauncher.launch(null)
            }
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

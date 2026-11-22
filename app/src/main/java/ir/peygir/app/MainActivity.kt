package ir.peygir.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var phoneInput: EditText
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var telephony: TelephonyManager? = null
    private var active = false
    private var sawOffhook = false
    private var callLaunched = false
    private var attempts = 0
    private var sessionStartedAt = 0L
    private val maxAttempts = 30
    private val retryDelayMs = 2000L
    private val retryRunnable = Runnable { inspectLastCallAndContinue() }

    private val phoneListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            if (!active || !callLaunched) return
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> sawOffhook = true
                TelephonyManager.CALL_STATE_IDLE -> if (sawOffhook) {
                    callLaunched = false
                    sawOffhook = false
                    status.text = "تماس پایان یافت؛ بررسی نتیجه..."
                    handler.removeCallbacks(retryRunnable)
                    handler.postDelayed(retryRunnable, retryDelayMs)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(Color.rgb(248, 250, 253))
        }
        root.addView(TextView(this).apply {
            text = "⟲"
            textSize = 54f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(205, 45, 55))
        })
        root.addView(TextView(this).apply {
            text = "پیگیر"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 65, 120))
        })
        root.addView(TextView(this).apply {
            text = "شماره را وارد کنید؛ حداکثر ۳۰ بار و با فاصله ۲ ثانیه دوباره تماس می‌گیرد."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 18, 0, 20)
        })
        phoneInput = EditText(this).apply {
            hint = "شماره تلفن"
            inputType = 3
            textSize = 20f
            gravity = Gravity.CENTER
            setSingleLine(true)
        }
        root.addView(phoneInput, LinearLayout.LayoutParams(-1, -2))
        startButton = Button(this).apply {
            text = "شروع تماس پیگیر"
            setOnClickListener { beginOrRequestPermissions() }
        }
        root.addView(startButton, buttonParams())
        stopButton = Button(this).apply {
            text = "توقف"
            isEnabled = false
            setOnClickListener { stopRedial("عملیات توسط شما متوقف شد.") }
        }
        root.addView(stopButton, buttonParams())
        status = TextView(this).apply {
            text = "آماده"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 24, 0, 0)
        }
        root.addView(status, buttonParams())
        root.addView(TextView(this).apply {
            text = "تشخیص پاسخ‌گویی از گزارش تماس گوشی انجام می‌شود و دسترسی گزارش تماس لازم است."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, 24, 0, 0)
        })
        setContentView(root)
    }

    private fun buttonParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }

    private fun beginOrRequestPermissions() {
        val required = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 71)
            return
        }
        startRedial()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 71) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startRedial()
            else status.text = "برای کارکرد برنامه، همه مجوزهای درخواست‌شده لازم است."
        }
    }

    private fun startRedial() {
        val number = phoneInput.text.toString().trim()
        if (number.isBlank()) {
            phoneInput.error = "شماره را وارد کنید"
            return
        }
        active = true
        attempts = 0
        sessionStartedAt = System.currentTimeMillis()
        startButton.isEnabled = false
        phoneInput.isEnabled = false
        stopButton.isEnabled = true
        telephony?.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE)
        makeCall(number)
    }

    private fun makeCall(number: String) {
        if (!active) return
        if (attempts >= maxAttempts) {
            stopRedial("حداکثر ۳۰ تلاش انجام شد.")
            return
        }
        attempts++
        callLaunched = true
        sawOffhook = false
        status.text = "تلاش شماره $attempts از $maxAttempts"
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
        } catch (e: Exception) {
            callLaunched = false
            stopRedial("شروع تماس ممکن نشد؛ مجوز تماس را بررسی کنید.")
        }
    }

    private fun inspectLastCallAndContinue() {
        if (!active) return
        try {
            val projection = arrayOf(CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE)
            val selection = "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.DATE}>=?"
            val args = arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), (sessionStartedAt - 5000L).toString())
            val result = contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, args, "${CallLog.Calls.DATE} DESC")
            if (result == null) {
                stopRedial("خواندن گزارش تماس ممکن نشد؛ دسترسی را بررسی کنید.")
                return
            }
            result.use { cursor ->
                if (!cursor.moveToFirst()) {
                    stopRedial("نتیجه تماس در گزارش گوشی پیدا نشد؛ برای جلوگیری از تماس ناخواسته متوقف شد.")
                    return
                }
                val index = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val duration = if (index >= 0) cursor.getLong(index) else 0L
                if (duration > 0L) {
                    stopRedial("تماس پاسخ داده شد؛ پیگیری متوقف شد.")
                    return
                }
            }
        } catch (e: SecurityException) {
            stopRedial("دسترسی گزارش تماس وجود ندارد؛ عملیات متوقف شد.")
            return
        }
        if (active) {
            status.text = "تماس پاسخ داده نشد؛ تلاش بعدی..."
            makeCall(phoneInput.text.toString().trim())
        }
    }

    private fun stopRedial(message: String) {
        active = false
        callLaunched = false
        sawOffhook = false
        handler.removeCallbacksAndMessages(null)
        telephony?.listen(phoneListener, PhoneStateListener.LISTEN_NONE)
        if (::startButton.isInitialized) startButton.isEnabled = true
        if (::phoneInput.isInitialized) phoneInput.isEnabled = true
        if (::stopButton.isInitialized) stopButton.isEnabled = false
        if (::status.isInitialized) status.text = message
    }

    override fun onDestroy() {
        stopRedial("برنامه بسته شد.")
        super.onDestroy()
    }
}

package ir.peygir.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.PhoneNumberUtils
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var phoneInput: EditText
    private lateinit var status: TextView
    private lateinit var attemptText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var telephony: TelephonyManager? = null

    @Volatile private var active = false
    private var sawOffhook = false
    private var callLaunched = false
    private var attempts = 0
    private var targetNumber = ""
    private var attemptStartedAt = 0L
    private var lookupRetries = 0

    private val maxAttempts = 30
    private val retryDelayMs = 2_000L
    private val lookupRetryDelayMs = 500L
    private val maxLookupRetries = 6
    private val callWatchdogMs = 120_000L

    private val retryRunnable = Runnable { inspectLastCallAndContinue() }
    private val watchdogRunnable = Runnable {
        if (active && callLaunched) {
            stopRedial("وضعیت تماس در زمان مجاز مشخص نشد؛ برای جلوگیری از تماس ناخواسته متوقف شد.")
        }
    }

    private val phoneListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            if (!active || !callLaunched) return

            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> sawOffhook = true
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (sawOffhook) {
                        callLaunched = false
                        sawOffhook = false
                        handler.removeCallbacks(watchdogRunnable)
                        lookupRetries = 0
                        status.text = "تماس پایان یافت؛ نتیجه در حال بررسی است."
                        handler.removeCallbacks(retryRunnable)
                        handler.postDelayed(retryRunnable, retryDelayMs)
                    }
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
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        root.addView(PeygirLogoView(this), LinearLayout.LayoutParams(dp(92), dp(92)))
        root.addView(TextView(this).apply {
            text = "پیگیر"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 65, 120))
        }, buttonParams())

        root.addView(TextView(this).apply {
            text = "پس از پایان تماس ناموفق، تا ۳۰ بار با فاصله ۲ ثانیه تماس را تکرار می‌کند."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(12), 0, dp(16))
        }, buttonParams())

        phoneInput = EditText(this).apply {
            hint = "شماره تلفن"
            inputType = 3
            textSize = 20f
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(phoneInput, buttonParams())

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

        attemptText = TextView(this).apply {
            text = "تعداد تلاش: ۰ از ۳۰"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(attemptText, buttonParams())

        status = TextView(this).apply {
            text = "آماده"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(55, 65, 81))
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(status, buttonParams())

        root.addView(TextView(this).apply {
            text = "برای تشخیص نتیجه تماس، مجوز گزارش تماس لازم است. تماس‌های پاسخ‌داده‌شده با مدت ثبت‌شده بیشتر از صفر متوقف می‌شوند."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, dp(22), 0, 0)
        }, buttonParams())

        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buttonParams() = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(8)
    }

    private fun beginOrRequestPermissions() {
        val required = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        val missing = required.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 71)
        } else {
            startRedial()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 71) {
            if (grantResults.isNotEmpty() && grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }) {
                startRedial()
            } else {
                status.text = "مجوزهای تماس، وضعیت تلفن و گزارش تماس باید تأیید شوند."
            }
        }
    }

    private fun startRedial() {
        if (active) return

        val rawNumber = phoneInput.text.toString().trim()
        val cleanedNumber = PhoneNumberUtils.stripSeparators(rawNumber)
        if (!cleanedNumber.matches(Regex("^\\+?[0-9]{5,20}$"))) {
            phoneInput.error = "شماره معتبر وارد کنید"
            return
        }

        targetNumber = cleanedNumber
        active = true
        attempts = 0
        startButton.isEnabled = false
        phoneInput.isEnabled = false
        stopButton.isEnabled = true
        attemptText.text = "تعداد تلاش: ۰ از $maxAttempts"

        try {
            telephony?.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE)
            makeCall()
        } catch (e: SecurityException) {
            stopRedial("دسترسی وضعیت تلفن داده نشده است.")
        }
    }

    private fun makeCall() {
        if (!active) return
        if (attempts >= maxAttempts) {
            stopRedial("حداکثر $maxAttempts تلاش انجام شد.")
            return
        }

        attempts++
        attemptText.text = "تعداد تلاش: $attempts از $maxAttempts"
        attemptStartedAt = System.currentTimeMillis()
        lookupRetries = 0
        sawOffhook = false
        callLaunched = true
        status.text = "در حال برقراری تماس؛ تلاش $attempts"

        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, callWatchdogMs)

        try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", targetNumber, null))
            startActivity(callIntent)
        } catch (e: Exception) {
            callLaunched = false
            handler.removeCallbacks(watchdogRunnable)
            stopRedial("شروع تماس ممکن نشد؛ مجوز تماس و سیم‌کارت را بررسی کنید.")
        }
    }

    private fun inspectLastCallAndContinue() {
        if (!active) return

        var foundMatchingCall = false
        var matchedDuration = 0L

        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            )
            val selection = "${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.DATE}>=?"
            val args = arrayOf(
                CallLog.Calls.OUTGOING_TYPE.toString(),
                (attemptStartedAt - 500L).toString()
            )

            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                args,
                "${CallLog.Calls.DATE} DESC"
            )

            if (cursor == null) {
                stopRedial("گزارش تماس قابل خواندن نیست؛ عملیات متوقف شد.")
                return
            }

            cursor.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                while (it.moveToNext()) {
                    val loggedNumber = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                    if (PhoneNumberUtils.compare(this, loggedNumber, targetNumber)) {
                        foundMatchingCall = true
                        matchedDuration = if (durationIndex >= 0) it.getLong(durationIndex) else 0L
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            stopRedial("مجوز گزارش تماس در دسترس نیست؛ عملیات متوقف شد.")
            return
        } catch (e: Exception) {
            stopRedial("خطا در بررسی گزارش تماس؛ عملیات متوقف شد.")
            return
        }

        if (!active) return

        if (!foundMatchingCall) {
            retryCallLogLookupOrStop()
            return
        }

        if (matchedDuration > 0L) {
            stopRedial("تماس پاسخ داده شد؛ پیگیری متوقف شد.")
            return
        }

        status.text = "تماس پاسخ داده نشد؛ تلاش بعدی آغاز می‌شود."
        makeCall()
    }

    private fun retryCallLogLookupOrStop() {
        if (lookupRetries < maxLookupRetries) {
            lookupRetries++
            status.text = "در انتظار ثبت نتیجه تماس در گزارش گوشی..."
            handler.postDelayed(retryRunnable, lookupRetryDelayMs)
        } else {
            stopRedial("نتیجه تماس در گزارش گوشی ثبت نشد؛ برای جلوگیری از تماس ناخواسته متوقف شد.")
        }
    }

    private fun stopRedial(message: String) {
        active = false
        callLaunched = false
        sawOffhook = false
        handler.removeCallbacksAndMessages(null)

        try {
            telephony?.listen(phoneListener, PhoneStateListener.LISTEN_NONE)
        } catch (_: SecurityException) {
        }

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

private class PeygirLogoView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = width * 0.30f
        val stroke = width * 0.085f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND

        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.color = Color.rgb(220, 48, 58)
        canvas.drawArc(bounds, 205f, 125f, false, paint)
        paint.color = Color.rgb(35, 160, 92)
        canvas.drawArc(bounds, 25f, 125f, false, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(220, 48, 58)
        canvas.drawCircle(cx - radius * 0.83f, cy - radius * 0.56f, stroke * 0.72f, paint)
        paint.color = Color.rgb(35, 160, 92)
        canvas.drawCircle(cx + radius * 0.83f, cy + radius * 0.56f, stroke * 0.72f, paint)
        paint.color = Color.rgb(35, 105, 190)
        canvas.drawCircle(cx, cy, stroke * 0.78f, paint)
    }
}

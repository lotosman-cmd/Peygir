package ir.peygir.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class FloatingButtonService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        createNotification()
    }

    private fun createNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel("peygir_overlay", "دکمه شناور پیگیر", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "peygir_overlay")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("پیگیر فعال است")
            .setContentText("دکمه شناور را روی محل دلخواه بکشید؛ برای شروع لمس کنید.")
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        startForeground(7, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) showBubble()
        return START_STICKY
    }

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(
            dp(62), dp(62), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = dp(14)
            y = 0
        }

        val button = FloatingLogoView()
        button.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var moved = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                        if (moved) {
                            params.x = (startX - dx).toInt()
                            params.y = (startY + dy).toInt()
                            try { windowManager.updateViewLayout(button, params) } catch (_: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) openPeygir()
                        return true
                    }
                }
                return true
            }
        })
        bubble = button
        windowManager.addView(button, params)
    }

    private fun openPeygir() {
        val number = getSharedPreferences("peygir", MODE_PRIVATE).getString("phone_number", "").orEmpty()
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("AUTO_START", number.isNotBlank())
            putExtra("PHONE_NUMBER", number)
        }
        startActivity(launch)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bubble?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class FloatingLogoView : View(this@FloatingButtonService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = width * 0.29f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width * 0.085f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.rgb(220, 48, 58)
            canvas.drawArc(cx-r, cy-r, cx+r, cy+r, 205f, 125f, false, paint)
            paint.color = Color.rgb(35, 160, 92)
            canvas.drawArc(cx-r, cy-r, cx+r, cy+r, 25f, 125f, false, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(220, 48, 58)
            canvas.drawCircle(cx-r*0.83f, cy-r*0.56f, width*0.06f, paint)
            paint.color = Color.rgb(35, 160, 92)
            canvas.drawCircle(cx+r*0.83f, cy+r*0.56f, width*0.06f, paint)
            paint.color = Color.rgb(35, 105, 190)
            canvas.drawCircle(cx, cy, width*0.065f, paint)
        }
    }
}

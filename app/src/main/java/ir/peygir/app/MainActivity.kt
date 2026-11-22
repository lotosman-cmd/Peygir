package ir.peygir.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = "پیگیر"
            textSize = 32f
            setTextColor(Color.rgb(30, 70, 130))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "نسخه آزمایشی ساخت اندروید\nقابلیت تماس مجدد هنوز فعال نیست."
            textSize = 17f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        })
        setContentView(root)
    }
}

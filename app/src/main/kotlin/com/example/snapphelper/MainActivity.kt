package com.example.snapphelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQ_AUDIO = 501
        private const val REQ_NOTIFICATIONS = 502
    }

    private lateinit var persianTts: PersianTts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the screen FIRST. TTS must not be allowed to prevent the
        // activity UI from appearing if the native TTS/model has a problem.
        val bg = Color.parseColor("#0B1233")
        val textPrimary = Color.parseColor("#FFFFFF")
        val textSecondary = Color.parseColor("#AFB8E8")

        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(32)
                bottomMargin = dp(16)
            }
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimary)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val subtitle = TextView(this).apply {
            text = "دستیار صوتی هوشمند برای پیک‌های اسنپ‌باکس"
            textSize = 14f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }

        val info = TextView(this).apply {
            text = """
                ۱) دسترسی میکروفون را بده.
                ۲) سرویس دسترس‌پذیری را برای این برنامه روشن کن.
                ۳) دکمه «شروع دستیار صوتی» را وقتی همین صفحه باز است بزن.
                ۴) بعد اسنپ‌باکس را باز کن.

                امنیت:
                • رنگ به تنهایی هیچ سفارشی را قبول نمی‌کند.
                • فقط فرمان‌های صوتی مشخص اجازه قبول دارند.
                • قبل از کلیک، دکمه «قبول/پذیرش» دوباره پیدا و بررسی می‌شود.
                • سفارش بعد از ۲۰ ثانیه منقضی می‌شود.
            """.trimIndent()
            textSize = 15f
            setLineSpacing(dp(4).toFloat(), 1f)
            setTextColor(textSecondary)
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val permissionButton = actionButton(
            text = "دادن مجوزها",
            background = R.drawable.bg_button_outline,
            textColor = textPrimary
        ) { requestPermissionsIfNeeded() }

        val accessibilityButton = actionButton(
            text = "باز کردن تنظیمات دسترس‌پذیری",
            background = R.drawable.bg_button_outline,
            textColor = textPrimary
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val startButton = actionButton(
            text = "شروع دستیار صوتی",
            background = R.drawable.bg_button_primary,
            textColor = Color.parseColor("#0B1233"),
            bold = true
        ) { startVoiceServiceFromVisibleActivity() }

        val stopButton = actionButton(
            text = "پایان دستیار صوتی",
            background = R.drawable.bg_button_stop,
            textColor = Color.parseColor("#0B1233"),
            bold = true
        ) { stopVoiceService() }

        val buttonSpacing = dp(14)
        listOf(permissionButton, accessibilityButton, startButton, stopButton).forEach { button ->
            (button.layoutParams as LinearLayout.LayoutParams).topMargin = buttonSpacing
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(24), dp(8), dp(24), dp(40))
            addView(icon)
            addView(title)
            addView(subtitle)
            addView(info)
            addView(permissionButton)
            addView(accessibilityButton)
            addView(startButton)
            addView(stopButton)
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
            addView(layout)
        }

        // IMPORTANT: show the UI before touching Sherpa-ONNX.
        setContentView(scrollView)

        // Do NOT initialize Sherpa-ONNX while opening the app.
        // The Persian TTS engine is initialized lazily only when speak()
        // is actually called. This prevents a TTS/model problem from
        // closing the main screen a few seconds after startup.
        persianTts = PersianTts(this)

        requestPermissionsIfNeeded()
    }

    private fun actionButton(
        text: String,
        background: Int,
        textColor: Int,
        bold: Boolean = false,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        this.text = text
        setTextColor(textColor)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setBackgroundResource(background)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        isAllCaps = false
        elevation = 0f
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun requestPermissionsIfNeeded() {
        val audioGranted = Build.VERSION.SDK_INT < 23 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!audioGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQ_AUDIO
            )
            return
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_AUDIO) {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun startVoiceServiceFromVisibleActivity() {
        if (
            Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionsIfNeeded()
            return
        }

        val intent = Intent(this, VoiceCommandService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }

        val message = "دستیار صوتی فعال شد"
        speak(message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun stopVoiceService() {
        stopService(Intent(this, VoiceCommandService::class.java))

        val message = "دستیار صوتی غیرفعال شد"
        speak(message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun speak(message: String) {
        if (!::persianTts.isInitialized) return

        try {
            persianTts.speak(message)
        } catch (e: Throwable) {
            // TTS must never be able to close the main application.
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        if (::persianTts.isInitialized) {
            try {
                persianTts.shutdown()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }
}

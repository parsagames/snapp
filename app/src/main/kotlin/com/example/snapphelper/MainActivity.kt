package com.example.snapphelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQ_AUDIO = 501
        private const val REQ_NOTIFICATIONS = 502
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "SnappBox Voice Helper"
            textSize = 24f
            setPadding(32, 40, 32, 20)
        }

        val info = TextView(this).apply {
            text = """
                1) دسترسی میکروفون را بده.
                2) سرویس دسترس‌پذیری را برای این برنامه روشن کن.
                3) دکمه «شروع دستیار صوتی» را وقتی همین صفحه باز است بزن.
                4) بعد SnappBox را باز کن.

                امنیت:
                - رنگ به تنهایی هیچ سفارشی را قبول نمی‌کند.
                - فقط فرمان‌های صوتی مشخص اجازه قبول دارند.
                - قبل از کلیک، دکمه «قبول/پذیرش» دوباره پیدا و بررسی می‌شود.
                - سفارش بعد از ۲۰ ثانیه منقضی می‌شود.
            """.trimIndent()
            textSize = 16f
            setPadding(32, 10, 32, 20)
        }

        val permissionButton = Button(this).apply {
            text = "دادن مجوزها"
            setOnClickListener { requestPermissionsIfNeeded() }
        }

        val accessibilityButton = Button(this).apply {
            text = "باز کردن تنظیمات دسترس‌پذیری"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val startButton = Button(this).apply {
            text = "شروع دستیار صوتی"
            setOnClickListener { startVoiceServiceFromVisibleActivity() }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(info)
            addView(permissionButton)
            addView(accessibilityButton)
            addView(startButton)
        }

        setContentView(layout)
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val audioGranted = Build.VERSION.SDK_INT < 23 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
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
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun startVoiceServiceFromVisibleActivity() {
        if (Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
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
    }
}

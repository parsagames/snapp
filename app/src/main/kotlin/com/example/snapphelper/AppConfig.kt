package com.example.snapphelper

object AppConfig {
    // اگر پکیج نسخهٔ نصب‌شدهٔ اسنپ‌باکس شما متفاوت است، این مقدار را تغییر دهید.
    const val SNAPP_BOX_PACKAGE = "com.snappbox.bikerapp"

    const val ORDER_VALID_MS = 20_000L
    const val ACCEPT_COOLDOWN_MS = 5_000L
    const val EVENT_DEBOUNCE_MS = 700L
}

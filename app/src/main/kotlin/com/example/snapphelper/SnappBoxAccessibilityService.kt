package com.example.snapphelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.concurrent.Executors

class SnappBoxAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: SnappBoxAccessibilityService? = null
            private set
        private const val INSPECT_TOKEN = "inspect"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastFingerprint: String? = null
    private var lastEventAt = 0L
    @Volatile private var currentOrderValidUntil = 0L
    @Volatile private var lastAcceptedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = false
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != AppConfig.SNAPP_BOX_PACKAGE) return
        val now = System.currentTimeMillis()
        if (now - lastEventAt < AppConfig.EVENT_DEBOUNCE_MS) return
        lastEventAt = now
        mainHandler.removeCallbacksAndMessages(INSPECT_TOKEN)
        mainHandler.postAtTime({ inspectForNewOrder() }, INSPECT_TOKEN, android.os.SystemClock.uptimeMillis() + 180)
    }

    private fun inspectForNewOrder() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != AppConfig.SNAPP_BOX_PACKAGE) return

        val accept = findAcceptButton(root) ?: return
        val texts = collectTexts(root)
        val amount = extractAmount(texts)
        val pickupAddress = extractPickupAddress(texts)
        val fingerprint = buildFingerprint(texts, accept)
        val now = System.currentTimeMillis()

        if (fingerprint == lastFingerprint && now < currentOrderValidUntil) return
        lastFingerprint = fingerprint
        currentOrderValidUntil = now + AppConfig.ORDER_VALID_MS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            detectColorAndSpeak(accept.node, amount, pickupAddress, texts)
        } else {
            speakOrder(amount, pickupAddress, OrderColor.UNKNOWN, texts)
        }
    }

    private fun detectColorAndSpeak(
        accept: AccessibilityNodeInfo,
        amount: String?,
        pickupAddress: String?,
        texts: List<String>
    ) {
        val bounds = Rect()
        accept.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            speakOrder(amount, pickupAddress, OrderColor.UNKNOWN, texts)
            return
        }

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            screenshotExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    var raw: Bitmap? = null
                    var bitmap: Bitmap? = null
                    try {
                        raw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        bitmap = raw?.copy(Bitmap.Config.ARGB_8888, false)
                        val color = bitmap?.let { ColorDetector.detect(it, bounds) } ?: OrderColor.UNKNOWN
                        mainHandler.post { speakOrder(amount, pickupAddress, color, texts) }
                    } catch (_: Throwable) {
                        mainHandler.post { speakOrder(amount, pickupAddress, OrderColor.UNKNOWN, texts) }
                    } finally {
                        // The copied bitmap is software-backed; the screenshot buffer is owned by ScreenshotResult.
                        bitmap?.recycle()
                        if (raw != null && raw !== bitmap) raw?.recycle()
                        try { screenshot.hardwareBuffer.close() } catch (_: Throwable) { }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    mainHandler.post { speakOrder(amount, pickupAddress, OrderColor.UNKNOWN, texts) }
                }
            }
        )
    }

    private fun speakOrder(amount: String?, pickupAddress: String?, color: OrderColor, texts: List<String>) {
        val safeAmount = amount ?: "نامشخص"
        val addressPart = pickupAddress?.let { "، مبدا $it" } ?: ""
        val message = "سفارش جدید. رنگ دکمه ${color.persian}. مبلغ $safeAmount ریال$addressPart. اگر می‌خواهی قبول شود بگو قبول کن."

        VoiceCommandService.pauseRecognitionForTts(3500)
        if (ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "order")
    }

    fun acceptCurrentOrder(): Boolean {
        val now = System.currentTimeMillis()
        if (now > currentOrderValidUntil) return false
        if (now - lastAcceptedAt < AppConfig.ACCEPT_COOLDOWN_MS) return false

        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != AppConfig.SNAPP_BOX_PACKAGE) return false

        val candidate = findAcceptButton(root) ?: return false
        val button = candidate.node
        if (!button.isVisibleToUser || !button.isClickable) return false
        if (!containsAcceptLabel(button)) return false

        val ok = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (ok) {
            lastAcceptedAt = now
            currentOrderValidUntil = 0L
            lastFingerprint = null
        }
        return ok
    }

    private data class ButtonCandidate(val node: AccessibilityNodeInfo)

    private fun findAcceptButton(root: AccessibilityNodeInfo): ButtonCandidate? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && hasAcceptLabel(node)) {
                if (node.isClickable) return ButtonCandidate(node)
                var parent = node.parent
                var depth = 0
                while (parent != null && depth++ < 4) {
                    if (parent.isVisibleToUser && parent.isClickable && containsAcceptLabel(parent)) {
                        return ButtonCandidate(parent)
                    }
                    parent = parent.parent
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun hasAcceptLabel(node: AccessibilityNodeInfo): Boolean {
        val text = normalizedText(node.text?.toString())
        val desc = normalizedText(node.contentDescription?.toString())
        return text.contains("قبول") || text.contains("پذیرش") || desc.contains("قبول") || desc.contains("پذیرش")
    }

    private fun containsAcceptLabel(node: AccessibilityNodeInfo): Boolean {
        if (hasAcceptLabel(node)) return true
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 30) {
            val child = queue.removeFirst()
            if (hasAcceptLabel(child)) return true
            for (i in 0 until child.childCount) child.getChild(i)?.let(queue::add)
        }
        return false
    }

    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val result = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(result::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() && it != node.text?.toString() }?.let(result::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return result.distinct()
    }

    private fun extractAmount(texts: List<String>): String? {
        // Prefer values with 4+ digits, since small route/count numbers are common in the UI.
        val candidates = texts.mapNotNull { text ->
            val normalized = text.replace(",", "").replace("٬", "")
            Regex("(?<![\\d۰-۹])([\\d۰-۹]{4,})(?![\\d۰-۹])").find(normalized)?.groupValues?.get(1)?.let { it to text }
        }
        return candidates.firstOrNull { (_, source) ->
            normalizedText(source).contains("ریال") || normalizedText(source).contains("تومان")
        }?.first ?: candidates.firstOrNull()?.first
    }

    private fun extractPickupAddress(texts: List<String>): String? {
        // The pickup ("circle" marker) address is normally the first long, non-amount,
        // non-button text block in the order card, ahead of the drop-off ("square") line.
        return texts.firstOrNull { text ->
            val normalized = normalizedText(text)
            text.length >= 10 &&
                !normalized.contains("قبول") &&
                !normalized.contains("پذیر") &&
                !text.contains("ریال") &&
                !text.contains("تومان")
        }
    }

    private fun buildFingerprint(texts: List<String>, accept: ButtonCandidate): String {
        val bounds = Rect()
        accept.node.getBoundsInScreen(bounds)
        val key = texts.filter { it.isNotBlank() }.take(80).joinToString("|")
        return "$key|${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
    }

    private fun normalizedText(value: String?): String = value.orEmpty()
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("\u200c", "")
        .replace(" ", "")
        .lowercase(Locale.ROOT)

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        mainHandler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        screenshotExecutor.shutdownNow()
        super.onDestroy()
    }

}

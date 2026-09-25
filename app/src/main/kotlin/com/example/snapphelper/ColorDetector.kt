package com.example.snapphelper

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/** Samples the accept-button area only. It deliberately returns UNKNOWN when the
 * pixels are too neutral/dark to make a reasonably safe classification. */
object ColorDetector {
    fun detect(bitmap: Bitmap, screenBounds: Rect): OrderColor {
        if (bitmap.width <= 1 || bitmap.height <= 1) return OrderColor.UNKNOWN

        val left = max(0, screenBounds.left)
        val top = max(0, screenBounds.top)
        val right = min(bitmap.width - 1, screenBounds.right - 1)
        val bottom = min(bitmap.height - 1, screenBounds.bottom - 1)
        if (left >= right || top >= bottom) return OrderColor.UNKNOWN

        // Ignore edges where rounded corners/shadows/text are common.
        val insetX = max(2, (right - left) / 8)
        val insetY = max(2, (bottom - top) / 5)
        val l = min(right - 1, left + insetX)
        val t = min(bottom - 1, top + insetY)
        val r = max(l + 1, right - insetX)
        val b = max(t + 1, bottom - insetY)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        val stepX = max(1, (r - l) / 12)
        val stepY = max(1, (b - t) / 5)

        for (y in t..b step stepY) {
            for (x in l..r step stepX) {
                val c = bitmap.getPixel(x, y)
                val rr = Color.red(c)
                val gg = Color.green(c)
                val bb = Color.blue(c)
                val hi = max(rr, max(gg, bb))
                val lo = min(rr, min(gg, bb))
                if (hi - lo < 20 && hi > 110) continue
                sumR += rr; sumG += gg; sumB += bb; count++
            }
        }
        if (count < 4) return OrderColor.UNKNOWN

        val rAvg = (sumR / count).toInt()
        val gAvg = (sumG / count).toInt()
        val bAvg = (sumB / count).toInt()
        val hsv = FloatArray(3)
        Color.RGBToHSV(rAvg, gAvg, bAvg, hsv)
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]
        if (s < 0.25f || v < 0.28f) return OrderColor.UNKNOWN

        return when {
            h >= 70f && h < 165f -> OrderColor.GREEN
            h >= 165f && h < 205f -> OrderColor.CYAN
            h >= 205f && h < 275f -> OrderColor.BLUE
            h >= 275f || h < 20f -> OrderColor.PINK
            else -> OrderColor.UNKNOWN
        }
    }
}

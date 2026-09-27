package com.eliel.asistente

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class UberFilterAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var lastSignature = ""
    private var lastShownAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (!pkg.contains("uber", true) && pkg != "com.ubercab.driver") {
            hideOverlay()
            return
        }

        val prefs = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
        val sundayOnly = prefs.getBoolean(UberFilterActivity.KEY_SUNDAY_ONLY, true)
        if (sundayOnly && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return

        handler.removeCallbacksAndMessages("parse")
        handler.postAtTime({ analyzeScreen() }, "parse", android.os.SystemClock.uptimeMillis() + 180)
    }

    private fun analyzeScreen() {
        val root = rootInActiveWindow ?: return
        val values = mutableListOf<String>()
        collect(root, values)
        val text = values.distinct().joinToString(" ")
        val result = parseOffer(text) ?: return

        val signature = String.format(Locale.US, "%.2f-%.2f-%.2f", result.payout, result.totalKm, result.rate)
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastShownAt < 1200) return

        lastSignature = signature
        lastShownAt = now
        showOverlay(result)
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || !node.isVisibleToUser) return
        val t = node.text?.toString()?.trim().orEmpty()
        val d = node.contentDescription?.toString()?.trim().orEmpty()

        if (node.childCount == 0) {
            if (t.isNotBlank()) out += t
            if (d.isNotBlank() && d != t) out += d
        } else {
            for (i in 0 until node.childCount) collect(node.getChild(i), out)
        }
    }

    private data class Result(
        val payout: Double,
        val totalKm: Double,
        val rate: Double,
        val good: Boolean,
        val distanceCount: Int
    )

    private fun parseOffer(raw: String): Result? {
        val normalized = normalize(raw)

        val moneyRegex = Regex("""(?:b\s*/?\.?\s*|usd\s*|\$\s*)(\d{1,3}(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val money = moneyRegex.findAll(normalized)
            .mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }
            .filter { it in 1.0..500.0 }
            .toList()
        if (money.isEmpty()) return null
        val payout = money.maxOrNull() ?: return null

        val distanceRegex = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s*(km|kilometros?|kilómetros?|mi|millas?)\b""", RegexOption.IGNORE_CASE)
        val rawDistances = distanceRegex.findAll(normalized).mapNotNull { m ->
            val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val unit = m.groupValues[2].lowercase(Locale.ROOT)
            val km = if (unit == "mi" || unit.startsWith("milla")) value * 1.60934 else value
            km.takeIf { it in 0.05..250.0 }
        }.toList()
        if (rawDistances.isEmpty()) return null

        val distances = mutableListOf<Double>()
        for (d in rawDistances) if (distances.none { abs(it - d) < 0.01 }) distances += d
        val selected = distances.take(2)
        val totalKm = selected.sum()
        if (totalKm <= 0.0) return null

        val threshold = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
            .getFloat(UberFilterActivity.KEY_THRESHOLD, 0.50f).toDouble()
        val rate = payout / totalKm

        return Result(payout, totalKm, rate, rate >= threshold, selected.size)
    }

    private fun showOverlay(result: Result) {
        hideOverlay()

        val view = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 21f
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            text = buildString {
                append(if (result.good) "✅ ACEPTAR" else "❌ RECHAZAR")
                append("\nB/. ")
                append(String.format(Locale.US, "%.2f", result.rate))
                append(" por km")
                append("\nPago B/. ")
                append(String.format(Locale.US, "%.2f", result.payout))
                append(" · ")
                append(String.format(Locale.US, "%.1f", result.totalKm))
                append(" km")
                if (result.distanceCount == 1) append("\n⚠ Solo 1 distancia visible")
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (result.good) 0xE62E7D32.toInt() else 0xE6C62828.toInt())
            }
            elevation = dp(10).toFloat()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(46)
        }

        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.addView(view, params)
            overlay = view
            handler.postDelayed({ hideOverlay() }, 8000)
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun hideOverlay() {
        val current = overlay ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(current)
        } catch (_: Exception) {}
        overlay = null
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onInterrupt() = hideOverlay()

    override fun onDestroy() {
        hideOverlay()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

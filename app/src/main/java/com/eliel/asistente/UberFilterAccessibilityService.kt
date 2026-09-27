package com.eliel.asistente

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private var bubbleManager: BubbleOverlayManager? = null

    private var decisionLocked = false
    private var currentRate: Double? = null
    private var offerStartedAt = 0L

    private val recentUberText = ArrayDeque<String>()
    private var lastUberEventAt = 0L

    companion object {
        private const val MAX_DECISION_VISIBLE_MS = 15_000L
        private const val EVENT_CACHE_MS = 3_000L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        bubbleManager = BubbleOverlayManager(this).also {
            it.show()
            it.setState(BubbleOverlayManager.State.IDLE)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
        val sundayOnly = prefs.getBoolean(UberFilterActivity.KEY_SUNDAY_ONLY, true)
        if (sundayOnly && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return

        val pkg = event?.packageName?.toString().orEmpty()
        val eventLooksUber = isUberPackage(pkg)
        val uberWindowPresent = findUberRoots().isNotEmpty()

        if (!eventLooksUber && !uberWindowPresent) return

        if (eventLooksUber && event != null) rememberUberEvent(event)

        bubbleManager?.setState(BubbleOverlayManager.State.READING)

        handler.removeCallbacksAndMessages("scan")
        handler.postAtTime({ scanCurrentOffer() }, "scan", SystemClock.uptimeMillis() + 80)
    }

    private fun isUberPackage(pkg: String): Boolean =
        pkg.equals("com.ubercab.driver", true) || pkg.contains("uber", true)

    private fun rememberUberEvent(event: AccessibilityEvent) {
        val pieces = mutableListOf<String>()

        event.text?.forEach { cs ->
            cs?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { pieces += it }
        }

        event.contentDescription?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { pieces += it }

        event.beforeText?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { pieces += it }

        event.source?.let { source ->
            val sourceText = mutableListOf<String>()
            collect(source, sourceText, requireVisible = false)
            pieces += sourceText
        }

        if (pieces.isNotEmpty()) {
            lastUberEventAt = System.currentTimeMillis()
            for (piece in pieces) {
                val clean = piece.trim()
                if (clean.isBlank()) continue
                recentUberText.remove(clean)
                recentUberText.addLast(clean)
                while (recentUberText.size > 120) recentUberText.removeFirst()
            }
        }
    }

    private fun findUberRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        rootInActiveWindow?.let { root ->
            if (isUberPackage(root.packageName?.toString().orEmpty())) roots += root
        }

        for (window in windows) {
            val root = window.root ?: continue
            if (isUberPackage(root.packageName?.toString().orEmpty())) {
                if (roots.none { it == root }) roots += root
            }
        }

        return roots
    }

    private fun scanCurrentOffer() {
        val now = System.currentTimeMillis()
        val roots = findUberRoots()

        val candidates = mutableListOf<String>()

        for (root in roots) {
            collect(root, candidates, requireVisible = false)
        }

        if (now - lastUberEventAt <= EVENT_CACHE_MS) {
            candidates += recentUberText
        } else {
            recentUberText.clear()
        }

        val rate = extractRateFromOffer(candidates)

        if (rate == null) {
            if (decisionLocked) clearDecision()
            else bubbleManager?.setState(BubbleOverlayManager.State.READING)
            return
        }

        val threshold = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
            .getFloat(UberFilterActivity.KEY_THRESHOLD, 0.50f)
            .toDouble()

        val sameOffer = decisionLocked && currentRate != null && abs(currentRate!! - rate) < 0.0001

        if (!sameOffer) {
            decisionLocked = true
            currentRate = rate
            offerStartedAt = now

            val good = rate >= threshold
            bubbleManager?.setState(
                if (good) BubbleOverlayManager.State.ACCEPT else BubbleOverlayManager.State.REJECT,
                rate
            )
            showDecision(rate, good)
        } else if (now - offerStartedAt >= MAX_DECISION_VISIBLE_MS) {
            hideOverlay()
            bubbleManager?.setState(BubbleOverlayManager.State.IDLE)
        }

        scheduleOfferWatch()
    }

    private fun extractRateFromOffer(parts: List<String>): Double? {
        if (parts.isEmpty()) return null

        val normalizedParts = parts
            .map { normalize(it) }
            .filter { it.isNotBlank() }

        val combined = normalize(normalizedParts.joinToString(" "))

        // Confirmar contexto de popup activo.
        val hasActiveOfferContext =
            combined.contains("viaje:") ||
            combined.contains("viaje ") ||
            combined.contains("aceptar") ||
            combined.contains("me interesa") ||
            Regex("""\ba\s+\d{1,2}\s+min\b""").containsMatchIn(combined)

        if (!hasActiveOfferContext) return null

        // 1) Formato estándar observado en tus capturas:
        //    USD0.74/km (estimado)
        val standard = Regex(
            """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]\s*(?:km|kilometros?|kilómetros?)\s*\(?\s*estimado\s*\)?""",
            RegexOption.IGNORE_CASE
        )

        // 2) Algunas capas de accesibilidad eliminan el slash:
        //    USD0.74 km (estimado)
        val withoutSlash = Regex(
            """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*(?:km|kilometros?|kilómetros?)\s*\(?\s*estimado\s*\)?""",
            RegexOption.IGNORE_CASE
        )

        // 3) Si Uber divide "/km" y "(estimado)" en nodos distintos,
        //    buscar primero una cifra con /km y validar que "estimado" exista cerca.
        val rateOnly = Regex(
            """(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]?\s*(?:km|kilometros?|kilómetros?)""",
            RegexOption.IGNORE_CASE
        )

        fun parseValue(match: MatchResult?): Double? {
            val v = match?.groupValues?.getOrNull(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?: return null
            return v.takeIf { it in 0.05..20.0 }
        }

        // Probar por fragmentos individuales.
        for (part in normalizedParts) {
            parseValue(standard.find(part))?.let { return it }
            parseValue(withoutSlash.find(part))?.let { return it }
        }

        // Probar texto combinado del popup actual.
        parseValue(standard.find(combined))?.let { return it }
        parseValue(withoutSlash.find(combined))?.let { return it }

        // Fallback controlado: debe existir "estimado" en el popup actual.
        if (combined.contains("estimado")) {
            parseValue(rateOnly.find(combined))?.let { return it }
        }

        return null
    }

    private fun scheduleOfferWatch() {
        handler.removeCallbacksAndMessages("watch")
        handler.postAtTime({ scanCurrentOffer() }, "watch", SystemClock.uptimeMillis() + 300)
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        requireVisible: Boolean
    ) {
        if (node == null) return
        if (requireVisible && !node.isVisibleToUser) return

        node.text?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { out += it }

        node.contentDescription?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { out += it }

        node.hintText?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { out += it }

        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out, requireVisible)
        }
    }

    private fun showDecision(rate: Double, good: Boolean) {
        hideOverlay()

        val message = buildString {
            append(if (good) "✅ ACEPTAR" else "❌ RECHAZAR")
            append("\nUSD ")
            append(String.format(Locale.US, "%.2f", rate))
            append("/km")
            append("\n(estimado de Uber)")
        }

        val view = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 21f
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            text = message
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (good) 0xE62E7D32.toInt() else 0xE6C62828.toInt())
            }
            elevation = dp(10).toFloat()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(46)
        }

        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.addView(view, params)
            overlay = view
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun clearDecision() {
        decisionLocked = false
        currentRate = null
        offerStartedAt = 0L
        handler.removeCallbacksAndMessages("watch")
        hideOverlay()
        bubbleManager?.setState(BubbleOverlayManager.State.IDLE)
    }

    private fun hideOverlay() {
        val current = overlay ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(current)
        } catch (_: Exception) {
        }
        overlay = null
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onInterrupt() {
        clearDecision()
    }

    override fun onDestroy() {
        recentUberText.clear()
        clearDecision()
        bubbleManager?.hide()
        bubbleManager = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

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
    private var lastSignature = ""
    private var lastShownAt = 0L
    private var lastUberSeenAt = 0L

    // Una vez que una solicitud fue evaluada, no volver a leerla.
    // El bloqueo se libera únicamente cuando la tarjeta de solicitud desaparece.
    private var decisionLocked = false
    private var lockedSignature = ""
    private var offerMissingSince = 0L

    // Texto reciente emitido por eventos de accesibilidad de Uber.
    // Algunas versiones de Uber no exponen todos los textos en rootInActiveWindow,
    // pero sí los entregan en AccessibilityEvent.
    private val recentEventText = ArrayDeque<String>()
    private var recentEventAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
        val sundayOnly = prefs.getBoolean(UberFilterActivity.KEY_SUNDAY_ONLY, true)
        if (sundayOnly && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return

        val eventPackage = event?.packageName?.toString().orEmpty()
        val eventLooksUber = isUberPackage(eventPackage)
        val uberWindowPresent = findUberRoots().isNotEmpty()

        if (eventLooksUber && event != null) {
            rememberEventText(event)
        }

        if (!eventLooksUber && !uberWindowPresent) {
            return
        }

        lastUberSeenAt = System.currentTimeMillis()

        if (decisionLocked) {
            handler.removeCallbacksAndMessages("unlock-check")
            handler.postAtTime({ checkIfOfferClosed() }, "unlock-check", SystemClock.uptimeMillis() + 120)
            return
        }

        handler.removeCallbacksAndMessages("parse")
        handler.postAtTime({ analyzeScreen() }, "parse", SystemClock.uptimeMillis() + 180)
    }

    private fun isUberPackage(pkg: String): Boolean =
        pkg.equals("com.ubercab.driver", true) || pkg.contains("uber", true)

    private fun findUberRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        rootInActiveWindow?.let { root ->
            if (isUberPackage(root.packageName?.toString().orEmpty())) {
                roots += root
            }
        }

        for (window in windows) {
            val root = window.root ?: continue
            if (isUberPackage(root.packageName?.toString().orEmpty())) {
                if (roots.none { it == root }) roots += root
            }
        }
        return roots
    }

    private fun analyzeScreen() {
        val roots = findUberRoots()
        if (roots.isEmpty()) return

        val values = mutableListOf<String>()
        roots.forEach { collect(it, values) }

        val cleanValues = values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val candidates = mutableListOf<String>()
        candidates.addAll(cleanValues)

        if (System.currentTimeMillis() - recentEventAt <= 4000L) {
            candidates.addAll(recentEventText)
        }

        val visibleText = candidates.distinct().joinToString(" ")
        val result = parseOffer(visibleText, candidates)

        getSharedPreferences("uber_filter_debug", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_seen", System.currentTimeMillis())
            .putString("last_text", cleanValues.take(80).joinToString(" | "))
            .putBoolean("last_parsed", result != null)
            .apply()

        if (result == null) {
            // No mostrar avisos repetitivos. Esperar al próximo evento de Uber
            // hasta contar con suficiente información para decidir.
            return
        }

        val signature = String.format(Locale.US, "%.2f-%.2f-%.2f", result.payout, result.totalKm, result.rate)
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastShownAt < 1200) return

        lastSignature = signature
        lastShownAt = now

        // Congela esta decisión. Mientras la tarjeta siga visible,
        // ningún evento de Uber vuelve a analizar la misma solicitud.
        decisionLocked = true
        lockedSignature = signature
        offerMissingSince = 0L

        showResult(result)
    }

    private fun checkIfOfferClosed() {
        if (!decisionLocked) return

        val roots = findUberRoots()
        if (roots.isEmpty()) {
            unlockDecision()
            return
        }

        val values = mutableListOf<String>()
        roots.forEach { collect(it, values) }
        val visibleText = normalize(values.distinct().joinToString(" "))

        if (isOfferCardVisible(visibleText)) {
            // La solicitud que ya evaluamos sigue en pantalla: no hacer nada.
            offerMissingSince = 0L
            return
        }

        val now = System.currentTimeMillis()
        if (offerMissingSince == 0L) {
            offerMissingSince = now
            handler.removeCallbacksAndMessages("unlock-check")
            handler.postAtTime({ checkIfOfferClosed() }, "unlock-check", SystemClock.uptimeMillis() + 450)
            return
        }

        if (now - offerMissingSince >= 400L) {
            unlockDecision()
        }
    }

    private fun isOfferCardVisible(text: String): Boolean {
        if (text.isBlank()) return false

        val hasAccept = text.contains("aceptar")
        val hasRate = Regex("""\d{1,2}(?:[.,]\d{1,3})?\s*[/／]\s*km""").containsMatchIn(text)
        val hasTrip = text.contains("viaje:") || text.contains("viaje ")
        val hasUberX = text.contains("uberx") || text.contains("uber x")

        return hasAccept && (hasRate || hasTrip || hasUberX)
    }

    private fun unlockDecision() {
        decisionLocked = false
        lockedSignature = ""
        offerMissingSince = 0L
        lastSignature = ""
        handler.removeCallbacksAndMessages("unlock-check")
        hideOverlay()
    }

    private fun rememberEventText(event: AccessibilityEvent) {
        val pieces = mutableListOf<String>()

        event.text?.forEach { value ->
            value?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { pieces += it }
        }

        event.contentDescription?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { pieces += it }

        event.beforeText?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { pieces += it }

        event.source?.let { source ->
            val sourceValues = mutableListOf<String>()
            collect(source, sourceValues)
            pieces += sourceValues
        }

        if (pieces.isEmpty()) return

        recentEventAt = System.currentTimeMillis()
        for (piece in pieces) {
            val clean = piece.trim()
            if (clean.isBlank()) continue
            recentEventText.remove(clean)
            recentEventText.addLast(clean)
            while (recentEventText.size > 120) {
                recentEventText.removeFirst()
            }
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || !node.isVisibleToUser) return

        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val hint = node.hintText?.toString()?.trim().orEmpty()

        if (text.isNotBlank()) out += text
        if (desc.isNotBlank() && desc != text) out += desc
        if (hint.isNotBlank() && hint != text && hint != desc) out += hint

        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out)
        }
    }

    private data class Result(
        val payout: Double,
        val totalKm: Double,
        val rate: Double,
        val good: Boolean,
        val distanceCount: Int
    )

    private fun parseOffer(raw: String, candidates: List<String> = emptyList()): Result? {
        val normalized = normalize(raw)

        val directRatePatterns = listOf(
            Regex("""(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*[/／]\s*(?:km|kilometros?|kilómetros?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:usd|b\s*/?\.?|\$)?\s*(\d{1,2}(?:[.,]\d{1,3})?)\s*(?:por|x)\s*(?:km|kilometros?|kilómetros?)""", RegexOption.IGNORE_CASE)
        )

        fun findRate(text: String): Double? {
            val normalizedText = normalize(text)
            return directRatePatterns
                .asSequence()
                .flatMap { it.findAll(normalizedText).asSequence() }
                .mapNotNull { it.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() }
                .firstOrNull { it in 0.05..20.0 }
        }

        // Primero buscar en cada fragmento individual. Esto cubre formatos reales de Uber
        // como "USD0.57/km (estimado)" aunque el árbol de accesibilidad venga fragmentado.
        val directRate = candidates.asSequence()
            .mapNotNull { findRate(it) }
            .firstOrNull()
            ?: findRate(normalized)

        val threshold = getSharedPreferences(UberFilterActivity.PREFS, Context.MODE_PRIVATE)
            .getFloat(UberFilterActivity.KEY_THRESHOLD, 0.50f)
            .toDouble()

        if (directRate != null) {
            val payoutRegex = Regex("""(?:usd|b\s*/?\.?|\$)\s*(\d{1,3}(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
            val payout = payoutRegex.findAll(normalized)
                .mapNotNull { it.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() }
                .filter { it >= 1.0 }
                .firstOrNull() ?: 0.0

            return Result(
                payout = payout,
                totalKm = 0.0,
                rate = directRate,
                good = directRate >= threshold,
                distanceCount = 0
            )
        }

        val moneyPatterns = listOf(
            Regex("""(?:b\s*/?\.?\s*|usd\s*|\$\s*)(\d{1,3}(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:ganas?|ganancia|tarifa|pago|incluye|total)\s*(?:de\s*)?(?:b\s*/?\.?\s*|usd\s*|\$\s*)?(\d{1,3}(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        val money = moneyPatterns
            .flatMap { regex ->
                regex.findAll(normalized)
                    .mapNotNull { m -> m.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() }
                    .toList()
            }
            .filter { it in 1.0..500.0 }
            .distinct()

        if (money.isEmpty()) return null
        val payout = money.maxOrNull() ?: return null

        val distanceRegex = Regex(
            """(\d{1,3}(?:[.,]\d{1,2})?)\s*(km|kilometros?|kilómetros?|mi|millas?)\b""",
            RegexOption.IGNORE_CASE
        )

        val rawDistances = distanceRegex.findAll(normalized).mapNotNull { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                ?: return@mapNotNull null
            val unit = match.groupValues[2].lowercase(Locale.ROOT)
            val km = if (unit == "mi" || unit.startsWith("milla")) value * 1.60934 else value
            km.takeIf { it in 0.05..250.0 }
        }.toList()

        if (rawDistances.isEmpty()) return null

        val distances = mutableListOf<Double>()
        for (distance in rawDistances) {
            if (distances.none { abs(it - distance) < 0.01 }) distances += distance
        }

        val selected = distances.take(2)
        val totalKm = selected.sum()
        if (totalKm <= 0.0) return null

        val rate = payout / totalKm
        return Result(payout, totalKm, rate, rate >= threshold, selected.size)
    }

    private fun showResult(result: Result) {
        hideOverlay()

        val message = buildString {
            append(if (result.good) "✅ ACEPTAR" else "❌ RECHAZAR")
            append("\nB/. ")
            append(String.format(Locale.US, "%.2f", result.rate))
            append(" por km")
            if (result.payout > 0.0) {
                append("\nPago USD ")
                append(String.format(Locale.US, "%.2f", result.payout))
            }
            if (result.totalKm > 0.0) {
                append(" · ")
                append(String.format(Locale.US, "%.1f", result.totalKm))
                append(" km")
            }
            if (result.distanceCount == 1) append("\n⚠ Solo 1 distancia visible")
        }

        showDiagnostic(
            message,
            if (result.good) 0xE62E7D32.toInt() else 0xE6C62828.toInt(),
            8000
        )
    }

    private fun showDiagnostic(message: String, color: Int, durationMs: Long) {
        hideOverlay()

        val view = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            text = message
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(color)
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
            handler.postDelayed({ hideOverlay() }, durationMs)
        } catch (_: Exception) {
            overlay = null
        }
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onInterrupt() {
        decisionLocked = false
        lockedSignature = ""
        offerMissingSince = 0L
        hideOverlay()
    }

    override fun onDestroy() {
        decisionLocked = false
        lockedSignature = ""
        offerMissingSince = 0L
        recentEventText.clear()
        hideOverlay()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

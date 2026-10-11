package com.eliel.asistente

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/** NEXO neon character. All states share the same expressive face renderer. */
class NexoFaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode = "idle"
    private var phase = 0f
    private var listeningLevel = 0.35f
    private var transition = 1f
    private var previousMode = "idle"
    private val motion = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1300L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; transition = (transition + .045f).coerceAtMost(1f); invalidate() }
    }

    fun showMode(value: String) {
        if (mode != value) { previousMode = mode; transition = 0f }
        mode = value
        if (isAttachedToWindow && !motion.isStarted) motion.start()
        invalidate()
    }

    /** Optional real microphone amplitude, normalized 0..1. Animation continues without it. */
    fun setAudioLevel(level: Float) {
        listeningLevel = (listeningLevel * .65f + level.coerceIn(0f, 1f) * .35f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!motion.isStarted) motion.start()
    }

    override fun onDetachedFromWindow() {
        motion.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val save = canvas.save()
        val side = min(width, height).toFloat()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(side / 132f, side / 132f)
        val active = mode !in setOf("idle", "ready")
        val breathe = sin(phase * 2.0 * PI).toFloat()
        val box = RectF(22f, 19f + breathe * 1.2f, 110f, 107f + breathe * 1.2f)
        // Entry and completion animations affect the entire character, not a static image.
        val enter = if (mode == "activated") (.76f + .24f * transition) else 1f
        val finish = if (mode == "success") (1f - .06f * transition) else 1f
        canvas.scale(enter * finish, enter * finish, 66f, 65f)
        p.clearShadowLayer()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(18f, 9f, 112f, 119f,
            intArrayOf(Color.rgb(15, 46, 79), Color.rgb(3, 12, 27), Color.rgb(0, 4, 13)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(box, 22f, 22f, p)
        p.shader = null

        // Multiple concentric cyan strokes reproduce the prototype's neon rim.
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        for ((stroke, alpha, blur) in listOf(Triple(8f, 65, 13f), Triple(4f, 140, 6f), Triple(2.5f, 255, 0f))) {
            p.strokeWidth = stroke
            p.color = Color.argb(alpha, 38, 183, 255)
            if (blur > 0f) p.setShadowLayer(blur, 0f, 0f, Color.rgb(20, 148, 255)) else p.clearShadowLayer()
            canvas.drawRoundRect(box, 22f, 22f, p)
        }
        p.clearShadowLayer()
        p.strokeWidth = 1f
        p.color = Color.argb(170, 189, 237, 255)
        canvas.drawRoundRect(RectF(26f, 23f + breathe, 106f, 103f + breathe), 18f, 18f, p)

        if (mode == "listening" || mode == "speaking") drawEqualizer(canvas)
        if (mode == "processing" || mode == "thinking") drawThinkingParticles(canvas)
        drawFace(canvas, active)
        canvas.restoreToCount(save)
    }

    private fun drawEqualizer(c: Canvas) {
        val amps = floatArrayOf(7f, 15f, 27f, 17f, 9f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 2.5f
        p.color = Color.rgb(55, 210, 255)
        p.setShadowLayer(5f, 0f, 0f, Color.CYAN)
        for (side in 0..1) for (i in amps.indices) {
            val x = if (side == 0) 18f - i * 3.6f else 114f + i * 3.6f
            val wave = .14f + .86f * abs(sin(phase * 2f * PI + i * 1.15 + side * .55).toFloat())
            val h = amps[i] * wave * (if (mode == "listening") 1.15f + listeningLevel * .4f else .85f)
            c.drawLine(x, 65f - h / 2f, x, 65f + h / 2f, p)
        }
        p.clearShadowLayer()
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawFace(c: Canvas, active: Boolean) {
        val happy = mode == "success" || mode == "happy"
        val wink = mode == "wink" || mode == "executing"
        val idle = !active
        val thinking = mode == "processing" || mode == "thinking"
        val surprised = mode == "surprised"
        val blink = if (phase > 0.95f) 1f - abs(phase - 0.975f) / 0.025f else 0f

        for (i in 0..1) {
            val x = if (i == 0) 47f else 85f
            val y = if (thinking && i == 1) 57f else 54f + if (mode == "listening") sin(phase * 2f * PI).toFloat() * 1.1f else 0f
            val closed = idle || happy || (wink && i == 1) || blink > .5f
            p.clearShadowLayer()
            if (closed) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3.1f
                p.strokeCap = Paint.Cap.ROUND
                p.color = Color.rgb(171, 229, 255)
                c.drawArc(RectF(x - 11f, y - 5f, x + 11f, y + 7f), 10f, 160f, false, p)
            } else {
                p.style = Paint.Style.FILL
                p.color = Color.rgb(226, 247, 255)
                p.setShadowLayer(4f, 0f, 0f, Color.rgb(24, 139, 255))
                c.drawOval(RectF(x - 11f, y - 12f, x + 11f, y + 13f), p)
                p.clearShadowLayer()
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.1f
                p.color = Color.rgb(55, 181, 255)
                c.drawOval(RectF(x - 11f, y - 12f, x + 11f, y + 13f), p)
                p.style = Paint.Style.FILL
                p.clearShadowLayer()
                val glance = if (thinking) 4f else if (mode == "listening") sin(phase * 2f * PI).toFloat() * 2f else 0f
                p.color = Color.rgb(42, 169, 255)
                c.drawCircle(x + glance, y + 1f, 8.1f, p)
                p.color = Color.rgb(17, 101, 210)
                c.drawCircle(x + glance, y + 1f, 6.9f, p)
                p.color = Color.rgb(3, 17, 37)
                c.drawCircle(x + glance + 1f, y + 1f, 5.6f, p)
                p.color = Color.WHITE
                c.drawCircle(x + glance - 2f, y - 3f, 2.7f, p)
                c.drawCircle(x + glance + 3.6f, y + 3.5f, 1.1f, p)
            }
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 2.5f
            p.color = Color.rgb(61, 181, 251)
            val eyebrowY = if (surprised) y - 22f else if (thinking) y - 17f else y - 19f
            c.drawArc(RectF(x - 11f, eyebrowY - 3f, x + 11f, eyebrowY + 6f),
                if (thinking) 185f else 195f, 150f, false, p)
        }

        p.clearShadowLayer()
        p.color = Color.rgb(70, 211, 255)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3.8f
        val mouth = RectF(51f, 78f, 81f, 99f)
        when {
            surprised -> c.drawCircle(66f, 88f, 5.5f, p)
            thinking -> c.drawArc(RectF(61f, 82f, 72f, 92f), 30f, 125f, false, p)
            mode == "speaking" -> {
                p.style = Paint.Style.FILL
                p.color = Color.rgb(50, 174, 245)
                val open = 4f + abs(sin(phase * 6f * PI).toFloat()) * 7f
                c.drawOval(RectF(60f, 84f - open / 2f, 72f, 84f + open), p)
            }
            else -> c.drawArc(mouth, 12f, if (happy || wink) 156f else 148f, false, p)
        }
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawThinkingParticles(c: Canvas) {
        p.clearShadowLayer()
        p.style = Paint.Style.FILL
        for (i in 0..2) {
            val pulse = (phase + i / 3f) % 1f
            p.color = Color.argb((235f * (1f - pulse)).toInt().coerceIn(0, 255), 34, 187, 255)
            p.setShadowLayer(4f, 0f, 0f, Color.CYAN)
            c.drawCircle(51f + i * 15f, 12f - 5f * pulse, 2.4f + 1.2f * pulse, p)
        }
        p.clearShadowLayer()
    }
}

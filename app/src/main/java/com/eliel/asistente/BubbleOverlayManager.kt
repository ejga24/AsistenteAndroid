package com.eliel.asistente

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.roundToInt

class BubbleOverlayManager(private val context: Context) {

    enum class State { IDLE, READING, ACCEPT, REJECT }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubble: TextView? = null
    private var attached = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 24
        y = 220
    }

    fun show() {
        if (attached) return

        val tv = TextView(context).apply {
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            elevation = dp(8).toFloat()

            setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX - (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            if (attached) {
                                try { wm.updateViewLayout(this@apply, params) } catch (_: Exception) {}
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        bubble = tv
        try {
            wm.addView(tv, params)
            attached = true
            setState(State.IDLE)
        } catch (_: Exception) {
            attached = false
            bubble = null
        }
    }

    fun hide() {
        val b = bubble ?: return
        if (!attached) return
        try { wm.removeView(b) } catch (_: Exception) {}
        attached = false
        bubble = null
    }

    fun setState(state: State, rate: Double? = null) {
        val b = bubble ?: return

        when (state) {
            State.IDLE -> {
                b.text = "FU"
                b.background = bg(0xE6606060.toInt())
            }
            State.READING -> {
                b.text = "Leyendo"
                b.background = bg(0xE6D17A00.toInt())
            }
            State.ACCEPT -> {
                b.text = if (rate != null) "✅ %.2f".format(rate) else "✅"
                b.background = bg(0xE62E7D32.toInt())
            }
            State.REJECT -> {
                b.text = if (rate != null) "❌ %.2f".format(rate) else "❌"
                b.background = bg(0xE6C62828.toInt())
            }
        }
    }

    private fun bg(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(color)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}

package com.eliel.asistente

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.graphics.*
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.abs

class NexoFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode = "ready"
    private var phase = 0f
    private var talkPhase = 0f
    private val motion = ValueAnimator.ofFloat(0f,1f).apply {
        duration=4400L; repeatCount=ValueAnimator.INFINITE; repeatMode=ValueAnimator.RESTART
        interpolator=android.view.animation.LinearInterpolator()
        addUpdateListener { phase=it.animatedValue as Float; invalidate() }
    }
    fun showMode(value:String){ mode=value; if(!motion.isStarted) motion.start(); invalidate() }
    override fun onDetachedFromWindow(){ motion.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas:Canvas){
        val w=width.toFloat(); val h=height.toFloat(); val s=min(w,h)
        val box=RectF(w*.19f,h*.12f,w*.81f,h*.88f)
        paint.style=Paint.Style.FILL
        paint.shader=LinearGradient(0f,0f,w,h,Color.rgb(10,24,42),Color.rgb(1,5,12),Shader.TileMode.CLAMP)
        paint.clearShadowLayer()
        canvas.drawRoundRect(box,s*.11f,s*.11f,paint); paint.shader=null
        paint.style=Paint.Style.STROKE; paint.strokeWidth=s*.028f; paint.color=Color.rgb(45,184,255); canvas.drawRoundRect(box,s*.11f,s*.11f,paint)
        paint.strokeWidth=s*.009f; paint.color=Color.rgb(185,232,255); canvas.drawRoundRect(RectF(box.left+5,box.top+5,box.right-5,box.bottom-5),s*.09f,s*.09f,paint)
        // Side equalizer: tallest bars nearest the face, smaller toward the edges.
        if (mode == "listening" || mode == "speaking" || mode == "processing" || mode == "activated") {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = s * .013f
            paint.color = Color.rgb(67, 215, 255)
            val amplitude = floatArrayOf(.14f, .115f, .09f, .067f, .045f)
            for (side in 0..1) {
                for (i in amplitude.indices) {
                    val distance = s * (.035f + i * .028f)
                    val x = if (side == 0) box.left - distance else box.right + distance
                    val oscillation = .73f + .27f * abs(sin((phase * 12.56637f + i * .72f + side * .6f).toDouble())).toFloat()
                    val bar = s * amplitude[i] * oscillation
                    canvas.drawLine(x, h * .50f - bar / 2f, x, h * .50f + bar / 2f, paint)
                }
            }
            paint.strokeCap = Paint.Cap.BUTT
        }
        val y=h*.43f; val r=s*.095f
        if(mode=="ready" || mode=="idle"){ closedEye(canvas,w*.38f,y,r); closedEye(canvas,w*.62f,y,r) } else if(mode=="success"){ happyEye(canvas,w*.34f,y,r); happyEye(canvas,w*.66f,y,r) } else { eye(canvas,w*.34f,y,r); eye(canvas,w*.66f,y,r) }
        paint.style=Paint.Style.STROKE; paint.strokeCap=Paint.Cap.ROUND; paint.strokeWidth=s*.032f; paint.color=Color.rgb(75,204,255)
        val mouth=RectF(w*.39f,h*.61f,w*.61f,h*.76f)
        when(mode){
            "processing" -> { canvas.drawCircle(w*.5f,h*.69f,s*.035f,paint) }
            "executing" -> canvas.drawArc(mouth,15f,150f,false,paint)
            "speaking" -> { val open=s*.025f+s*.025f*phase; canvas.drawOval(RectF(w*.46f,h*.66f-open,w*.54f,h*.69f+open),paint) }
            "activated" -> canvas.drawArc(RectF(w*.34f,h*.56f,w*.66f,h*.80f),10f,160f,false,paint)
            "success" -> canvas.drawArc(RectF(w*.36f,h*.58f,w*.64f,h*.78f),15f,150f,false,paint)
            "surprised" -> canvas.drawCircle(w*.5f,h*.69f,s*.045f,paint)
            "wink" -> canvas.drawArc(mouth,15f,150f,false,paint)
            else -> canvas.drawArc(mouth,15f,150f,false,paint)
        }
        paint.strokeCap=Paint.Cap.BUTT
    }
    private fun closedEye(c:Canvas,cx:Float,cy:Float,r:Float){
        paint.style=Paint.Style.STROKE
        paint.strokeWidth=r*.22f
        paint.strokeCap=Paint.Cap.ROUND
        paint.color=Color.rgb(200,240,255)
        c.drawArc(RectF(cx-r,cy-r*.30f,cx+r,cy+r*.65f),15f,150f,false,paint)
        paint.strokeCap=Paint.Cap.BUTT
    }
    private fun happyEye(c:Canvas,cx:Float,cy:Float,r:Float){
        paint.style=Paint.Style.STROKE; paint.strokeWidth=r*.18f; paint.strokeCap=Paint.Cap.ROUND; paint.color=Color.rgb(210,244,255)
        c.drawArc(RectF(cx-r,cy-r*.25f,cx+r,cy+r*.8f),200f,140f,false,paint); paint.strokeCap=Paint.Cap.BUTT
    }
    private fun eye(c:Canvas,cx:Float,cy:Float,r:Float){
        val blink=if(phase>.965f) sin(((phase-.965f)/.035f * 3.14159f).toDouble()).toFloat().coerceAtLeast(0f) else 0f
        val winkClose = if(mode=="wink" && cx < width*.5f) .88f else 0f
        val sy=(1f-maxOf(blink*.82f,winkClose)).coerceAtLeast(.12f)
        c.save(); c.scale(1f,sy,cx,cy)
        paint.style=Paint.Style.FILL; paint.color=Color.rgb(225,247,255); c.drawCircle(cx,cy,r,paint)
        val glance=if(mode=="listening") sin((phase * 6.283185f).toDouble()).toFloat()*r*.32f else 0f
        paint.color=Color.rgb(36,166,255); c.drawCircle(cx+glance,cy,r*.64f,paint)
        paint.color=Color.rgb(2,18,32); c.drawCircle(cx+glance,cy,r*.39f,paint)
        paint.color=Color.WHITE; c.drawCircle(cx+glance-r*.14f,cy-r*.18f,r*.12f,paint); c.restore()
        paint.style=Paint.Style.STROKE; paint.strokeWidth=r*.11f; paint.color=Color.rgb(76,199,255)
        c.drawArc(RectF(cx-r*1.05f,cy-r*1.24f,cx+r*1.05f,cy+r*.18f),205f,130f,false,paint)
    }
}
package com.tgdesign.vault.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils

/**
 * Optional translucent touch indicator overlay for recording clean demo videos
 * (mirrors the circular finger indicator seen in showcase recordings).
 */
class TouchCursorOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    var isCursorEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                fadeAnimator?.cancel()
                dotAlpha = 0f
                invalidate()
            }
        }

    private var touchX = 0f
    private var touchY = 0f
    private var dotAlpha = 0f
    private var isPressedDown = false
    private var fadeAnimator: ValueAnimator? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    init {
        isClickable = false
        isFocusable = false
    }

    fun onHostTouchEvent(ev: MotionEvent) {
        if (!isCursorEnabled) return

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fadeAnimator?.cancel()
                touchX = ev.x
                touchY = ev.y
                isPressedDown = true
                dotAlpha = 1f
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                touchX = ev.x
                touchY = ev.y
                if (dotAlpha < 1f) {
                    fadeAnimator?.cancel()
                    dotAlpha = 1f
                }
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                forceFadeOut()
            }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            forceFadeOut()
        }
    }

    fun forceFadeOut() {
        if (!isPressedDown && dotAlpha <= 0.01f) return
        isPressedDown = false
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(dotAlpha, 0f).apply {
            duration = 160L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                dotAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isCursorEnabled || dotAlpha <= 0.01f) return

        val radius = dp(if (isPressedDown) 21f else 24f)
        fillPaint.color = ColorUtils.setAlphaComponent(Color.GRAY, (95 * dotAlpha).toInt())
        ringPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, (195 * dotAlpha).toInt())

        canvas.drawCircle(touchX, touchY, radius, fillPaint)
        canvas.drawCircle(touchX, touchY, radius, ringPaint)
    }
}

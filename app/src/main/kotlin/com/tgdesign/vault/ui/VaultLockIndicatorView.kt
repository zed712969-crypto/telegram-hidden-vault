package com.tgdesign.vault.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.tgdesign.vault.theme.TelegramPalette
import com.tgdesign.vault.theme.TelegramTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Custom Canvas indicator for Stage 2 of the pull-to-vault gesture:
 * displays a circular 68dp glass pill with a 3dp progress ring, an animated padlock shackle,
 * and a clear status label below.
 */
class VaultLockIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private var palette: TelegramPalette = TelegramTheme.Dark
    private var progress: Float = 0f
    private var isArmed: Boolean = false
    private var armedBounceScale: Float = 1f

    private val pillDiameter = dp(68f)
    private val ringStrokeWidth = dp(3f)
    private val topMargin = dp(8f)

    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStrokeWidth
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStrokeWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val lockBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val lockShacklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val keyholePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(13f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()
    private val lockBodyRect = RectF()
    private val labelBgRect = RectF()
    private val shacklePath = Path()

    private val bounceValue = FloatValueHolder(1f)
    private val bounceSpring = SpringAnimation(bounceValue).apply {
        spring = SpringForce(1f).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            stiffness = SpringForce.STIFFNESS_MEDIUM
        }
        addUpdateListener { _, value, _ ->
            armedBounceScale = value
            invalidate()
        }
    }

    init {
        applyPalette(TelegramTheme.Dark)
    }

    fun applyPalette(newPalette: TelegramPalette) {
        palette = newPalette
        invalidate()
    }

    /**
     * Updates visual progress (0f..1f) and armed lock state.
     */
    fun setPullState(progress: Float, isArmed: Boolean) {
        val clamped = min(1f, max(0f, progress))
        val justArmed = !this.isArmed && isArmed
        this.progress = clamped
        this.isArmed = isArmed

        if (justArmed) {
            bounceSpring.cancel()
            bounceValue.value = 1.16f
            bounceSpring.animateToFinalPosition(1f)
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = dp(120f).toInt()
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width * 0.5f
        val pillRadius = pillDiameter * 0.5f
        val cy = topMargin + pillRadius

        canvas.save()
        canvas.scale(armedBounceScale, armedBounceScale, cx, cy)

        // Frosted circular pill background
        val basePillColor = if (isArmed) {
            ColorUtils.blendARGB(palette.elevated, palette.accentStrong, 0.35f)
        } else {
            ColorUtils.setAlphaComponent(palette.elevated, if (palette.isDark) 235 else 242)
        }
        pillBgPaint.color = basePillColor
        canvas.drawCircle(cx, cy, pillRadius, pillBgPaint)

        // Subtle glass rim
        pillBorderPaint.color = if (isArmed) {
            ColorUtils.setAlphaComponent(palette.accent, 180)
        } else {
            ColorUtils.setAlphaComponent(palette.textPrimary, if (palette.isDark) 36 else 22)
        }
        canvas.drawCircle(cx, cy, pillRadius - dp(0.5f), pillBorderPaint)

        // Progress track and active ring
        val ringInset = ringStrokeWidth * 0.5f + dp(2f)
        arcRect.set(
            cx - pillRadius + ringInset,
            cy - pillRadius + ringInset,
            cx + pillRadius - ringInset,
            cy + pillRadius - ringInset
        )
        trackPaint.color = ColorUtils.setAlphaComponent(palette.textSecondary, 55)
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        if (progress > 0.005f) {
            arcPaint.color = if (isArmed) palette.accent else ColorUtils.blendARGB(palette.accentStrong, palette.accent, progress)
            canvas.drawArc(arcRect, -90f, 360f * progress, false, arcPaint)
        }

        // Custom padlock icon: shackle lifts smoothly as progress approaches 1f
        val iconColor = if (isArmed) palette.accent else palette.textPrimary
        lockBodyPaint.color = iconColor
        lockShacklePaint.color = iconColor
        keyholePaint.color = basePillColor

        val bodyWidth = dp(18f)
        val bodyHeight = dp(14f)
        val bodyTop = cy - dp(1f)
        lockBodyRect.set(
            cx - bodyWidth * 0.5f,
            bodyTop,
            cx + bodyWidth * 0.5f,
            bodyTop + bodyHeight
        )
        canvas.drawRoundRect(lockBodyRect, dp(3.5f), dp(3.5f), lockBodyPaint)

        // Keyhole dot + tiny stem
        canvas.drawCircle(cx, bodyTop + bodyHeight * 0.45f, dp(1.8f), keyholePaint)
        canvas.drawRect(
            cx - dp(0.8f),
            bodyTop + bodyHeight * 0.45f,
            cx + dp(0.8f),
            bodyTop + bodyHeight * 0.72f,
            keyholePaint
        )

        // Shackle lifts up to 4.5dp as user reaches threshold
        val shackleLift = dp(4.5f) * (if (isArmed) 1f else progress * 0.65f)
        val shackleRadius = dp(5.5f)
        val shackleBottom = bodyTop + dp(0.5f)
        val shackleArchTop = shackleBottom - dp(6.5f) - shackleLift

        shacklePath.reset()
        shacklePath.moveTo(cx - shackleRadius, shackleBottom)
        shacklePath.lineTo(cx - shackleRadius, shackleArchTop + shackleRadius)
        shacklePath.arcTo(
            cx - shackleRadius,
            shackleArchTop,
            cx + shackleRadius,
            shackleArchTop + shackleRadius * 2f,
            180f,
            180f,
            false
        )
        // When armed, right leg stays slightly lifted to depict an unlocking shackle
        val rightLegBottom = if (isArmed) shackleBottom - dp(3.5f) else shackleBottom
        shacklePath.lineTo(cx + shackleRadius, rightLegBottom)
        canvas.drawPath(shacklePath, lockShacklePaint)

        canvas.restore()

        // Status label capsule beneath the lock pill
        val label = if (isArmed) "Release to unlock Vault" else "Pull deeper for Vault"
        labelPaint.color = if (isArmed) palette.accent else palette.textPrimary
        val textWidth = labelPaint.measureText(label)
        val labelPaddingH = dp(12f)
        val labelPaddingV = dp(5f)
        val labelTop = topMargin + pillDiameter + dp(10f)
        val fm = labelPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val labelBottom = labelTop + textHeight + labelPaddingV * 2f

        labelBgRect.set(
            cx - textWidth * 0.5f - labelPaddingH,
            labelTop,
            cx + textWidth * 0.5f + labelPaddingH,
            labelBottom
        )
        labelBgPaint.color = ColorUtils.setAlphaComponent(palette.elevated, if (palette.isDark) 210 else 235)
        canvas.drawRoundRect(labelBgRect, dp(12f), dp(12f), labelBgPaint)

        val baseline = labelTop + labelPaddingV - fm.ascent
        canvas.drawText(label, cx, baseline, labelPaint)
    }
}

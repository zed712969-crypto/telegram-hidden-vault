package com.tgdesign.vault.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.tgdesign.vault.theme.TelegramPalette
import com.tgdesign.vault.theme.TelegramTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Telegram Android 13.3 pull-down-for-archive container:
 * pulling the chat list down reveals the Archived Chats row with spring physics,
 * vertical capsule track + flipping arrow + flood ripple. Long-pressing Archived Chats
 * opens the Hidden Vault ("second bottom").
 */
class TwoStageVaultLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    val archiveHeightPx: Float = dp(72f)
    private val stage1ThresholdPx: Float = dp(64f)
    private val maxVisualPullPx: Float = dp(104f)

    private val contentContainer = FrameLayout(context)
    private val pullOverlayView = ArchivePullHeaderView(context)

    private var archiveRowView: View? = null
    private var recyclerView: RecyclerView? = null

    private var palette: TelegramPalette = TelegramTheme.Dark
    private var isPullEnabled: Boolean = true
    private var isArchiveRowPinned: Boolean = false

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var isDragging = false
    private var rawPullPx = 0f
    private var currentOffsetPx = 0f

    private var stage1HapticFired = false

    var onArchiveRowVisibilityChanged: ((Boolean) -> Unit)? = null
    var onPullOffsetChanged: (() -> Unit)? = null

    private val springValue = FloatValueHolder(0f)
    private val springAnimation = SpringAnimation(springValue).apply {
        spring = SpringForce(0f).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            stiffness = SpringForce.STIFFNESS_MEDIUM
        }
        addUpdateListener { _, value, _ ->
            applyOffset(value)
        }
    }

    init {
        clipChildren = true
        clipToPadding = true

        addView(
            contentContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        applyPalette(TelegramTheme.Dark)
    }

    /**
     * Attaches the Archive row, pull-header overlay, and RecyclerView inside [contentContainer].
     */
    fun attachContent(archiveRow: View, list: RecyclerView) {
        contentContainer.removeAllViews()
        this.archiveRowView = archiveRow
        this.recyclerView = list

        archiveRow.translationY = -archiveHeightPx
        contentContainer.addView(
            archiveRow,
            LayoutParams(LayoutParams.MATCH_PARENT, archiveHeightPx.toInt(), Gravity.TOP)
        )
        contentContainer.addView(
            pullOverlayView,
            LayoutParams(LayoutParams.MATCH_PARENT, maxVisualPullPx.toInt(), Gravity.TOP)
        )
        contentContainer.addView(
            list,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        pullOverlayView.visibility = View.GONE
    }

    fun setPullEnabled(enabled: Boolean) {
        isPullEnabled = enabled
        if (!enabled) {
            collapseImmediately()
        }
    }

    fun applyPalette(newPalette: TelegramPalette) {
        palette = newPalette
        pullOverlayView.applyPalette(newPalette)
    }

    fun collapseImmediately() {
        springAnimation.cancel()
        isDragging = false
        isArchiveRowPinned = false
        rawPullPx = 0f
        stage1HapticFired = false
        applyOffset(0f)
        onArchiveRowVisibilityChanged?.invoke(false)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isPullEnabled) return false
        val list = recyclerView ?: return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                springAnimation.cancel()
                downX = ev.x
                downY = ev.y
                lastY = ev.y
                isDragging = false
                rawPullPx = visualToRaw(currentOffsetPx)
                stage1HapticFired = currentOffsetPx >= stage1ThresholdPx
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    val atTop = !list.canScrollVertically(-1)
                    if (atTop && dy > 0f && (!isArchiveRowPinned || currentOffsetPx < maxVisualPullPx)) {
                        isDragging = true
                        lastY = ev.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    if (atTop && dy < 0f && currentOffsetPx > 0f) {
                        isDragging = true
                        lastY = ev.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isPullEnabled) return super.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = ev.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.y - lastY
                lastY = ev.y
                rawPullPx = max(0f, rawPullPx + deltaY)
                val visualOffset = rawToVisual(rawPullPx)
                applyOffset(visualOffset)
                evaluateHaptics(visualOffset)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                finishDrag()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun rawToVisual(raw: Float): Float {
        if (raw <= 0f) return 0f
        if (raw <= stage1ThresholdPx) {
            return raw * 0.92f
        }
        val extra = raw - stage1ThresholdPx
        return min(maxVisualPullPx, stage1ThresholdPx * 0.92f + extra * 0.42f)
    }

    private fun visualToRaw(visual: Float): Float {
        if (visual <= 0f) return 0f
        val stage1Visual = stage1ThresholdPx * 0.92f
        if (visual <= stage1Visual) {
            return visual / 0.92f
        }
        val extraVisual = visual - stage1Visual
        return stage1ThresholdPx + extraVisual / 0.42f
    }

    private fun evaluateHaptics(offsetPx: Float) {
        if (offsetPx >= stage1ThresholdPx && !stage1HapticFired) {
            stage1HapticFired = true
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else if (offsetPx < stage1ThresholdPx * 0.8f) {
            stage1HapticFired = false
        }
    }

    private fun applyOffset(offsetPx: Float) {
        val clamped = max(0f, offsetPx)
        currentOffsetPx = clamped

        val archiveReveal = min(clamped, archiveHeightPx)
        archiveRowView?.let { row ->
            row.translationY = archiveReveal - archiveHeightPx
            row.alpha = if (isArchiveRowPinned && !isDragging) {
                1f
            } else {
                min(1f, archiveReveal / (archiveHeightPx * 0.55f))
            }
        }
        recyclerView?.translationY = clamped

        if (isDragging && !isArchiveRowPinned && clamped > dp(4f)) {
            pullOverlayView.visibility = View.VISIBLE
            pullOverlayView.updatePull(
                visibleHeight = clamped,
                stage1Fraction = min(1f, clamped / stage1ThresholdPx),
                isArmed = clamped >= stage1ThresholdPx
            )
        } else {
            pullOverlayView.visibility = View.GONE
        }
        onPullOffsetChanged?.invoke()
    }

    private fun finishDrag() {
        val settleThreshold = if (isArchiveRowPinned) stage1ThresholdPx * 0.7f else stage1ThresholdPx
        if (currentOffsetPx >= settleThreshold) {
            isArchiveRowPinned = true
            animateToOffset(archiveHeightPx)
            onArchiveRowVisibilityChanged?.invoke(true)
        } else {
            isArchiveRowPinned = false
            animateToOffset(0f)
            onArchiveRowVisibilityChanged?.invoke(false)
        }
    }

    private fun animateToOffset(targetPx: Float) {
        springAnimation.cancel()
        springValue.value = currentOffsetPx
        springAnimation.spring.finalPosition = targetPx
        springAnimation.start()
    }

    /**
     * Pull header replicating Telegram 13.3's vertical capsule track + flipping arrow + flood ripple.
     */
    private class ArchivePullHeaderView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private fun dp(v: Float): Float = v * density

        private var palette: TelegramPalette = TelegramTheme.Dark
        private var visibleHeight = 0f
        private var stage1Fraction = 0f
        private var isArmed = false
        private var accentExpandFraction = 0f

        private val bgPaint = Paint()
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(15f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        private val trackRect = RectF()
        private val arrowPath = Path()

        private val expandHolder = FloatValueHolder(0f)
        private val expandSpring = SpringAnimation(expandHolder).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
            addUpdateListener { _, value, _ ->
                accentExpandFraction = value
                invalidate()
            }
        }

        fun applyPalette(newPalette: TelegramPalette) {
            palette = newPalette
            invalidate()
        }

        fun updatePull(visibleHeight: Float, stage1Fraction: Float, isArmed: Boolean) {
            this.visibleHeight = visibleHeight
            this.stage1Fraction = stage1Fraction
            if (isArmed != this.isArmed) {
                this.isArmed = isArmed
                expandSpring.cancel()
                expandHolder.value = accentExpandFraction
                expandSpring.animateToFinalPosition(if (isArmed) 1f else 0f)
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (visibleHeight <= 1f) return

            canvas.save()
            canvas.clipRect(0f, 0f, width.toFloat(), visibleHeight)

            bgPaint.color = palette.archivePullBg
            canvas.drawRect(0f, 0f, width.toFloat(), visibleHeight, bgPaint)

            val cx = dp(36f)
            val circleR = dp(11.5f)
            val circleCy = max(circleR + dp(4f), visibleHeight - dp(16f))
            val trackTop = min(dp(10f), circleCy - circleR)

            if (accentExpandFraction > 0.01f) {
                val maxRadius = hypot(width.toDouble(), height.toDouble()).toFloat()
                accentPaint.color = palette.archivePullActiveBg
                canvas.drawCircle(cx, circleCy, dp(18f) + maxRadius * accentExpandFraction, accentPaint)
            }

            val trackAlpha = (min(1f, stage1Fraction * 1.3f) * 64).toInt()
            trackPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, trackAlpha)
            trackRect.set(
                cx - circleR,
                trackTop,
                cx + circleR,
                circleCy + circleR
            )
            canvas.drawRoundRect(trackRect, circleR, circleR, trackPaint)

            circlePaint.alpha = (min(1f, 0.45f + stage1Fraction * 0.55f) * 255).toInt()
            canvas.drawCircle(cx, circleCy, circleR, circlePaint)

            arrowPaint.color = ColorUtils.blendARGB(
                palette.archivePullBg,
                palette.archivePullActiveBg,
                accentExpandFraction.coerceIn(0f, 1f)
            )
            canvas.save()
            canvas.rotate(180f * accentExpandFraction.coerceIn(0f, 1f), cx, circleCy)
            val s = dp(4.4f)
            arrowPath.reset()
            arrowPath.moveTo(cx, circleCy - s)
            arrowPath.lineTo(cx, circleCy + s)
            arrowPath.moveTo(cx - s * 0.85f, circleCy + s * 0.25f)
            arrowPath.lineTo(cx, circleCy + s)
            arrowPath.lineTo(cx + s * 0.85f, circleCy + s * 0.25f)
            canvas.drawPath(arrowPath, arrowPaint)
            canvas.restore()

            val label = if (isArmed) "Release for archive" else "Pull down for archive"
            textPaint.alpha = (min(1f, stage1Fraction * 1.4f) * 255).toInt()
            val fm = textPaint.fontMetrics
            val baseline = visibleHeight * 0.5f - (fm.ascent + fm.descent) * 0.5f
            canvas.drawText(label, width * 0.5f, baseline, textPaint)

            canvas.restore()
        }
    }
}

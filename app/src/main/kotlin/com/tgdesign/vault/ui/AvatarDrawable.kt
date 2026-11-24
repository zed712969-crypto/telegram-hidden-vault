package com.tgdesign.vault.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * Rich avatar artwork styles so demo chat rows look varied like real Telegram chats.
 */
enum class AvatarGlyph {
    INITIALS,
    SAVED_STAR,
    TELEGRAM_PLANE,
    DESIGN_SPARKLE,
    DIAMOND,
    CAT,
    LOCK_VAULT,
    KEY,
    GIFT,
    CAMERA,
    CODE,
    MUSIC,
    PORTRAIT_WOMAN,
    PORTRAIT_MAN,
    GROUP_PEOPLE,
    PACKAGE
}

/**
 * Circular avatar drawable with a diagonal Telegram gradient, subtle top-left sheen,
 * and crisp custom-drawn vector artwork (or bold initials).
 */
class AvatarDrawable(
    private val topColor: Int,
    private val bottomColor: Int,
    private val initials: String,
    private val glyph: AvatarGlyph = AvatarGlyph.INITIALS
) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val glyphSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val glyphStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private val path = Path()
    private val rect = RectF()
    private var lastWidth = 0
    private var lastHeight = 0

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width()
        val h = b.height()
        if (w <= 0 || h <= 0) return

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val radius = minOf(w, h) * 0.5f

        if (w != lastWidth || h != lastHeight || bgPaint.shader == null) {
            lastWidth = w
            lastHeight = h
            bgPaint.shader = LinearGradient(
                b.left.toFloat(),
                b.top.toFloat(),
                b.right.toFloat(),
                b.bottom.toFloat(),
                topColor,
                bottomColor,
                Shader.TileMode.CLAMP
            )
            sheenPaint.shader = RadialGradient(
                cx - radius * 0.35f,
                cy - radius * 0.38f,
                radius * 1.15f,
                intArrayOf(Color.argb(56, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            textPaint.textSize = h * 0.39f
            glyphStrokePaint.strokeWidth = maxOf(1.5f, radius * 0.11f)
        }

        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, sheenPaint)

        when (glyph) {
            AvatarGlyph.INITIALS -> drawInitials(canvas, cx, cy)
            AvatarGlyph.SAVED_STAR -> drawBookmark(canvas, cx, cy, radius)
            AvatarGlyph.TELEGRAM_PLANE -> drawPlane(canvas, cx, cy, radius)
            AvatarGlyph.DESIGN_SPARKLE -> drawSparkles(canvas, cx, cy, radius)
            AvatarGlyph.DIAMOND -> drawDiamond(canvas, cx, cy, radius)
            AvatarGlyph.CAT -> drawCat(canvas, cx, cy, radius)
            AvatarGlyph.LOCK_VAULT -> drawLock(canvas, cx, cy, radius)
            AvatarGlyph.KEY -> drawKey(canvas, cx, cy, radius)
            AvatarGlyph.GIFT -> drawGift(canvas, cx, cy, radius)
            AvatarGlyph.CAMERA -> drawCamera(canvas, cx, cy, radius)
            AvatarGlyph.CODE -> drawCode(canvas, cx, cy, radius)
            AvatarGlyph.MUSIC -> drawMusic(canvas, cx, cy, radius)
            AvatarGlyph.PORTRAIT_WOMAN -> drawPortrait(canvas, cx, cy, radius, longHair = true)
            AvatarGlyph.PORTRAIT_MAN -> drawPortrait(canvas, cx, cy, radius, longHair = false)
            AvatarGlyph.GROUP_PEOPLE -> drawGroup(canvas, cx, cy, radius)
            AvatarGlyph.PACKAGE -> drawPackage(canvas, cx, cy, radius)
        }
    }

    private fun drawInitials(canvas: Canvas, cx: Float, cy: Float) {
        val fm = textPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) * 0.5f
        canvas.drawText(initials, cx, textY, textPaint)
    }

    private fun drawBookmark(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val w = r * 0.56f
        val h = r * 0.72f
        path.reset()
        path.moveTo(cx - w * 0.5f, cy - h * 0.5f)
        path.lineTo(cx + w * 0.5f, cy - h * 0.5f)
        path.lineTo(cx + w * 0.5f, cy + h * 0.5f)
        path.lineTo(cx, cy + h * 0.2f)
        path.lineTo(cx - w * 0.5f, cy + h * 0.5f)
        path.close()
        canvas.drawPath(path, glyphFillPaint)
    }

    private fun drawPlane(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val s = r * 0.56f
        path.reset()
        path.moveTo(cx + s * 0.92f, cy - s * 0.72f)
        path.lineTo(cx - s * 0.92f, cy - s * 0.04f)
        path.lineTo(cx - s * 0.25f, cy + s * 0.22f)
        path.lineTo(cx + s * 0.52f, cy - s * 0.36f)
        path.lineTo(cx - s * 0.02f, cy + s * 0.34f)
        path.lineTo(cx + s * 0.56f, cy + s * 0.76f)
        path.close()
        canvas.drawPath(path, glyphFillPaint)
    }

    private fun drawSparkles(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        drawFourPointStar(canvas, cx - r * 0.1f, cy - r * 0.04f, r * 0.44f, glyphFillPaint)
        drawFourPointStar(canvas, cx + r * 0.34f, cy - r * 0.34f, r * 0.18f, glyphSoftPaint)
        drawFourPointStar(canvas, cx + r * 0.28f, cy + r * 0.34f, r * 0.15f, glyphSoftPaint)
    }

    private fun drawFourPointStar(canvas: Canvas, x: Float, y: Float, size: Float, paint: Paint) {
        path.reset()
        path.moveTo(x, y - size)
        path.quadTo(x, y, x + size, y)
        path.quadTo(x, y, x, y + size)
        path.quadTo(x, y, x - size, y)
        path.quadTo(x, y, x, y - size)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val s = r * 0.54f
        path.reset()
        path.moveTo(cx - s * 0.58f, cy - s * 0.45f)
        path.lineTo(cx + s * 0.58f, cy - s * 0.45f)
        path.lineTo(cx + s * 0.94f, cy - s * 0.02f)
        path.lineTo(cx, cy + s * 0.76f)
        path.lineTo(cx - s * 0.94f, cy - s * 0.02f)
        path.close()
        canvas.drawPath(path, glyphFillPaint)
    }

    private fun drawCat(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val s = r * 0.52f
        path.reset()
        path.moveTo(cx - s * 0.8f, cy - s * 0.1f)
        path.lineTo(cx - s * 0.75f, cy - s * 0.88f)
        path.lineTo(cx - s * 0.22f, cy - s * 0.45f)
        path.lineTo(cx + s * 0.22f, cy - s * 0.45f)
        path.lineTo(cx + s * 0.75f, cy - s * 0.88f)
        path.lineTo(cx + s * 0.8f, cy - s * 0.1f)
        path.quadTo(cx + s * 0.85f, cy + s * 0.72f, cx, cy + s * 0.76f)
        path.quadTo(cx - s * 0.85f, cy + s * 0.72f, cx - s * 0.8f, cy - s * 0.1f)
        path.close()
        canvas.drawPath(path, glyphFillPaint)

        // Cute tiny eyes in background tint
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bottomColor }
        canvas.drawCircle(cx - s * 0.3f, cy + s * 0.05f, s * 0.12f, eyePaint)
        canvas.drawCircle(cx + s * 0.3f, cy + s * 0.05f, s * 0.12f, eyePaint)
    }

    private fun drawLock(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val bw = r * 0.72f
        val bh = r * 0.54f
        val top = cy - r * 0.04f
        rect.set(cx - bw * 0.5f, top, cx + bw * 0.5f, top + bh)
        canvas.drawRoundRect(rect, r * 0.12f, r * 0.12f, glyphFillPaint)

        val sr = r * 0.22f
        path.reset()
        path.moveTo(cx - sr, top + r * 0.02f)
        path.lineTo(cx - sr, top - r * 0.2f)
        path.arcTo(cx - sr, top - r * 0.42f, cx + sr, top - r * 0.02f, 180f, 180f, false)
        path.lineTo(cx + sr, top + r * 0.02f)
        canvas.drawPath(path, glyphStrokePaint)
    }

    private fun drawKey(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val ringR = r * 0.22f
        val ringX = cx - r * 0.24f
        canvas.drawCircle(ringX, cy, ringR, glyphStrokePaint)
        canvas.drawLine(ringX + ringR, cy, cx + r * 0.46f, cy, glyphStrokePaint)
        canvas.drawLine(cx + r * 0.26f, cy, cx + r * 0.26f, cy + r * 0.18f, glyphStrokePaint)
        canvas.drawLine(cx + r * 0.44f, cy, cx + r * 0.44f, cy + r * 0.22f, glyphStrokePaint)
    }

    private fun drawGift(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val s = r * 0.44f
        rect.set(cx - s * 0.85f, cy - s * 0.1f, cx + s * 0.85f, cy + s * 0.92f)
        canvas.drawRoundRect(rect, r * 0.08f, r * 0.08f, glyphSoftPaint)
        rect.set(cx - s * 0.98f, cy - s * 0.45f, cx + s * 0.98f, cy - s * 0.06f)
        canvas.drawRoundRect(rect, r * 0.08f, r * 0.08f, glyphFillPaint)
        // Ribbon stripe
        rect.set(cx - s * 0.14f, cy - s * 0.45f, cx + s * 0.14f, cy + s * 0.92f)
        canvas.drawRect(rect, glyphFillPaint)
        // Bow loops
        canvas.drawCircle(cx - s * 0.28f, cy - s * 0.62f, s * 0.2f, glyphStrokePaint)
        canvas.drawCircle(cx + s * 0.28f, cy - s * 0.62f, s * 0.2f, glyphStrokePaint)
    }

    private fun drawCamera(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val w = r * 0.96f
        val h = r * 0.66f
        rect.set(cx - w * 0.5f, cy - h * 0.4f, cx + w * 0.5f, cy + h * 0.5f)
        canvas.drawRoundRect(rect, r * 0.14f, r * 0.14f, glyphFillPaint)
        rect.set(cx - w * 0.2f, cy - h * 0.58f, cx + w * 0.2f, cy - h * 0.35f)
        canvas.drawRoundRect(rect, r * 0.08f, r * 0.08f, glyphFillPaint)
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bottomColor }
        canvas.drawCircle(cx, cy + h * 0.05f, r * 0.19f, lensPaint)
        canvas.drawCircle(cx, cy + h * 0.05f, r * 0.1f, glyphFillPaint)
    }

    private fun drawCode(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val d = r * 0.36f
        path.reset()
        path.moveTo(cx - d * 0.55f, cy - d * 0.65f)
        path.lineTo(cx - d * 1.15f, cy)
        path.lineTo(cx - d * 0.55f, cy + d * 0.65f)
        path.moveTo(cx + d * 0.55f, cy - d * 0.65f)
        path.lineTo(cx + d * 1.15f, cy)
        path.lineTo(cx + d * 0.55f, cy + d * 0.65f)
        path.moveTo(cx + d * 0.16f, cy - d * 0.78f)
        path.lineTo(cx - d * 0.16f, cy + d * 0.78f)
        canvas.drawPath(path, glyphStrokePaint)
    }

    private fun drawMusic(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val noteR = r * 0.16f
        canvas.drawCircle(cx - r * 0.2f, cy + r * 0.24f, noteR, glyphFillPaint)
        canvas.drawCircle(cx + r * 0.22f, cy + r * 0.14f, noteR, glyphFillPaint)
        path.reset()
        path.moveTo(cx - r * 0.08f, cy + r * 0.22f)
        path.lineTo(cx - r * 0.08f, cy - r * 0.34f)
        path.lineTo(cx + r * 0.34f, cy - r * 0.44f)
        path.lineTo(cx + r * 0.34f, cy + r * 0.12f)
        canvas.drawPath(path, glyphStrokePaint)
    }

    private fun drawPortrait(canvas: Canvas, cx: Float, cy: Float, r: Float, longHair: Boolean) {
        if (longHair) {
            // Soft hair silhouette behind head and shoulders
            rect.set(cx - r * 0.34f, cy - r * 0.44f, cx + r * 0.34f, cy + r * 0.16f)
            canvas.drawRoundRect(rect, r * 0.3f, r * 0.3f, glyphSoftPaint)
        }
        // Head
        canvas.drawCircle(cx, cy - r * 0.16f, r * 0.23f, glyphFillPaint)
        // Shoulders arc
        rect.set(cx - r * 0.44f, cy + r * 0.14f, cx + r * 0.44f, cy + r * 0.78f)
        canvas.drawArc(rect, 180f, 180f, true, glyphFillPaint)
    }

    private fun drawGroup(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // Side silhouettes
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.12f, r * 0.14f, glyphSoftPaint)
        rect.set(cx - r * 0.52f, cy + r * 0.08f, cx - r * 0.04f, cy + r * 0.56f)
        canvas.drawArc(rect, 180f, 180f, true, glyphSoftPaint)

        canvas.drawCircle(cx + r * 0.28f, cy - r * 0.12f, r * 0.14f, glyphSoftPaint)
        rect.set(cx + r * 0.04f, cy + r * 0.08f, cx + r * 0.52f, cy + r * 0.56f)
        canvas.drawArc(rect, 180f, 180f, true, glyphSoftPaint)

        // Center foreground person
        canvas.drawCircle(cx, cy - r * 0.18f, r * 0.18f, glyphFillPaint)
        rect.set(cx - r * 0.34f, cy + r * 0.06f, cx + r * 0.34f, cy + r * 0.66f)
        canvas.drawArc(rect, 180f, 180f, true, glyphFillPaint)
    }

    private fun drawPackage(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val s = r * 0.44f
        rect.set(cx - s, cy - s * 0.78f, cx + s, cy + s * 0.85f)
        canvas.drawRoundRect(rect, r * 0.12f, r * 0.12f, glyphFillPaint)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bottomColor
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1.5f, r * 0.09f)
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - s, cy - s * 0.2f, cx + s, cy - s * 0.2f, linePaint)
        canvas.drawLine(cx, cy - s * 0.78f, cx, cy - s * 0.2f, linePaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        sheenPaint.alpha = alpha
        glyphFillPaint.alpha = alpha
        glyphSoftPaint.alpha = alpha
        glyphStrokePaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        sheenPaint.colorFilter = colorFilter
        glyphFillPaint.colorFilter = colorFilter
        glyphSoftPaint.colorFilter = colorFilter
        glyphStrokePaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

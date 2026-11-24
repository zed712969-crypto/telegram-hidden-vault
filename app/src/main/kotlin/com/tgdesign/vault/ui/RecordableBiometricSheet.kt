package com.tgdesign.vault.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tgdesign.vault.R
import com.tgdesign.vault.theme.TelegramPalette

/**
 * Pixel / Material 3 style biometric prompt sheet rendered inside the app window so
 * screen recordings capture the full fingerprint unlock flow without FLAG_SECURE blackouts.
 */
object RecordableBiometricSheet {

    fun show(
        context: Context,
        palette: TelegramPalette,
        onAuthenticated: () -> Unit
    ): BottomSheetDialog {
        val density = context.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density).toInt()
        fun dpf(v: Float): Float = v * density

        val dialog = BottomSheetDialog(context)
        val sheetBgColor = if (palette.isDark) 0xFF1F1F1F.toInt() else Color.WHITE
        val textMain = if (palette.isDark) Color.WHITE else 0xFF1F1F1F.toInt()
        val textSub = if (palette.isDark) 0xFF9AA3AE.toInt() else 0xFF5F6368.toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f), dp(22f), dp(24f), dp(20f))
            background = GradientDrawable().apply {
                setColor(sheetBgColor)
                cornerRadii = floatArrayOf(
                    dpf(28f), dpf(28f), dpf(28f), dpf(28f),
                    0f, 0f, 0f, 0f
                )
            }
        }

        // Top app badge: Telegram paper plane circle + "Telegram"
        val appIcon = ImageView(context).apply {
            setImageDrawable(
                AvatarDrawable(
                    0xFF39A7E8.toInt(),
                    0xFF1E7BC4.toInt(),
                    "TG",
                    AvatarGlyph.TELEGRAM_PLANE
                )
            )
            layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f))
        }

        val appLabel = TextView(context).apply {
            text = "Telegram"
            textSize = 13f
            setTextColor(textSub)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6f)
            }
        }

        val titleView = TextView(context).apply {
            text = "Unlock Vault"
            textSize = 23f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(textMain)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10f)
                bottomMargin = dp(28f)
            }
        }

        val subtitleView = TextView(context).apply {
            text = "Touch the fingerprint sensor"
            textSize = 14f
            setTextColor(textSub)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18f)
                bottomMargin = dp(24f)
            }
        }

        var unlocked = false
        val sensorView = FingerprintSensorView(context, palette).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84f), dp(84f))
            setOnClickListener {
                if (unlocked) return@setOnClickListener
                unlocked = true
                val haptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
                performHapticFeedback(haptic)
                subtitleView.text = "Unlocked"
                subtitleView.setTextColor(palette.accent)
                playSuccessMorph {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                    onAuthenticated()
                }
            }
        }

        // Bottom-left Dismiss button matching Pixel Material 3 BiometricPrompt
        val footerRow = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val dismissButton = TextView(context).apply {
            text = "Dismiss"
            textSize = 14.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.accentStrong)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent, 36)),
                null,
                null
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            setOnClickListener { dialog.dismiss() }
        }
        footerRow.addView(dismissButton)

        root.addView(appIcon)
        root.addView(appLabel)
        root.addView(titleView)
        root.addView(sensorView)
        root.addView(subtitleView)
        root.addView(footerRow)

        dialog.setContentView(root)
        dialog.show()
        return dialog
    }

    /**
     * Circular 64dp fingerprint sensor with outer ripple ring and smooth morph to checkmark.
     */
    private class FingerprintSensorView(
        context: Context,
        private val palette: TelegramPalette
    ) : View(context) {

        private val density = resources.displayMetrics.density
        private fun dp(v: Float): Float = v * density

        private val circleRadius = dp(31f)
        private val baseColor = if (palette.isDark) 0xFF333333.toInt() else 0xFF474A4F.toInt()
        private val successColor = palette.accentStrong

        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f)
        }
        private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(3f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val fingerprintDrawable = ContextCompat.getDrawable(context, R.drawable.ic_fingerprint)?.mutate()
        private val checkPath = Path()
        private val checkDrawPath = Path()
        private val pathMeasure = PathMeasure()
        private var morphProgress = 0f

        init {
            isClickable = true
            isFocusable = true
        }

        fun playSuccessMorph(onFinished: () -> Unit) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 320L
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener {
                    morphProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            postDelayed(onFinished, 340L)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width * 0.5f
            val cy = height * 0.5f

            // Outer confirmation ring
            if (morphProgress > 0.01f) {
                val ringR = circleRadius + dp(10f) * morphProgress
                val fade = (1f - morphProgress * 0.85f).coerceIn(0f, 1f)
                ripplePaint.color = ColorUtils.setAlphaComponent(palette.accent, (180 * fade).toInt())
                canvas.drawCircle(cx, cy, ringR, ripplePaint)
            }

            // Inner circle morphs from neutral charcoal to Telegram blue
            circlePaint.color = ColorUtils.blendARGB(baseColor, successColor, morphProgress)
            val pulseScale = if (morphProgress < 0.4f) {
                1f + 0.08f * (morphProgress / 0.4f)
            } else {
                1.08f - 0.08f * ((morphProgress - 0.4f) / 0.6f)
            }
            canvas.save()
            canvas.scale(pulseScale, pulseScale, cx, cy)
            canvas.drawCircle(cx, cy, circleRadius, circlePaint)

            // Crossfade fingerprint out
            val fpAlpha = ((1f - morphProgress * 2.5f).coerceIn(0f, 1f) * 255).toInt()
            if (fpAlpha > 0) {
                val iconHalf = dp(18f).toInt()
                fingerprintDrawable?.let { d ->
                    d.setTint(Color.WHITE)
                    d.alpha = fpAlpha
                    d.setBounds(
                        cx.toInt() - iconHalf,
                        cy.toInt() - iconHalf,
                        cx.toInt() + iconHalf,
                        cy.toInt() + iconHalf
                    )
                    d.draw(canvas)
                }
            }

            // Draw animated checkmark in
            val checkFrac = ((morphProgress - 0.2f) / 0.75f).coerceIn(0f, 1f)
            if (checkFrac > 0f) {
                checkPath.reset()
                checkPath.moveTo(cx - dp(10f), cy + dp(0.5f))
                checkPath.lineTo(cx - dp(2.5f), cy + dp(8f))
                checkPath.lineTo(cx + dp(11f), cy - dp(7f))

                pathMeasure.setPath(checkPath, false)
                checkDrawPath.reset()
                pathMeasure.getSegment(0f, pathMeasure.length * checkFrac, checkDrawPath, true)
                canvas.drawPath(checkDrawPath, checkPaint)
            }

            canvas.restore()
        }
    }
}

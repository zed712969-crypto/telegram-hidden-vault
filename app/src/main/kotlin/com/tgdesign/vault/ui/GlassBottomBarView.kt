package com.tgdesign.vault.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.tgdesign.vault.R
import com.tgdesign.vault.theme.TelegramPalette
import com.tgdesign.vault.theme.TelegramTheme
import kotlin.math.abs

/**
 * Floating frosted Liquid Glass bottom navigation bar matching Telegram Android 13.3,
 * with live API 31+ RenderNode backdrop blur and sliding active lens pill.
 */
class GlassBottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class MainTab(val title: String) {
        CHATS("Chats"),
        CONTACTS("Contacts"),
        SETTINGS("Settings"),
        PROFILE("Profile")
    }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Int = (value * density).toInt()
    private fun dpf(value: Float): Float = value * density

    private var palette: TelegramPalette = TelegramTheme.Dark
    private var selectedTab: MainTab = MainTab.CHATS
    private var lensPosition: Float = 0f

    private val bgFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpf(1f)
    }
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillRect = RectF()
    private val lensRect = RectF()
    private val clipPath = Path()

    private val blurNode: RenderNode? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        RenderNode("GlassBarBackdrop").apply {
            setRenderEffect(RenderEffect.createBlurEffect(26f, 26f, Shader.TileMode.CLAMP))
        }
    } else {
        null
    }
    private var isDrawingBackdrop = false

    /** Sibling content view behind the floating bar to blur in real time. */
    var backdropSource: View? = null

    private val tabsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val iconViews = mutableMapOf<MainTab, ImageView>()
    private val labelViews = mutableMapOf<MainTab, TextView>()
    private var chatsBadgeView: TextView? = null

    var onTabSelected: ((MainTab) -> Unit)? = null

    private val lensSpringHolder = FloatValueHolder(0f)
    private val lensSpring = SpringAnimation(lensSpringHolder).apply {
        spring = SpringForce(0f).apply {
            dampingRatio = 0.78f
            stiffness = SpringForce.STIFFNESS_MEDIUM
        }
        addUpdateListener { _, value, _ ->
            lensPosition = value
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
        setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        elevation = dpf(10f)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = view.height * 0.5f
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        clipToOutline = true

        MainTab.entries.forEach { tab ->
            tabsRow.addView(createTabView(tab))
        }
        addView(tabsRow)
        applyPalette(TelegramTheme.Dark)
    }

    private fun createTabView(tab: MainTab): FrameLayout {
        val tabContainer = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener {
                selectTab(tab, animate = true)
                onTabSelected?.invoke(tab)
            }
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val iconBox = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44f), dp(26f))
        }

        val iconView = ImageView(context).apply {
            if (tab == MainTab.PROFILE) {
                setImageResource(R.drawable.avatar_09)
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                clipToOutline = true
                layoutParams = LayoutParams(dp(23f), dp(23f), Gravity.CENTER)
            } else {
                val res = when (tab) {
                    MainTab.CHATS -> R.drawable.ic_tab_chats
                    MainTab.CONTACTS -> R.drawable.ic_tab_contacts
                    MainTab.SETTINGS -> R.drawable.ic_tab_settings
                    MainTab.PROFILE -> R.drawable.ic_tab_contacts
                }
                setImageResource(res)
                layoutParams = LayoutParams(dp(23f), dp(23f), Gravity.CENTER)
            }
        }
        iconBox.addView(iconView)
        iconViews[tab] = iconView

        if (tab == MainTab.CHATS) {
            val badge = TextView(context).apply {
                textSize = 10.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minWidth = dp(16f)
                setPadding(dp(4f), 0, dp(4f), 0)
                visibility = View.GONE
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    dp(16f),
                    Gravity.TOP or Gravity.END
                )
            }
            iconBox.addView(badge)
            chatsBadgeView = badge
        }

        val label = TextView(context).apply {
            text = tab.title
            textSize = 11f
            maxLines = 1
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2f)
            }
        }
        labelViews[tab] = label

        inner.addView(iconBox)
        inner.addView(label)
        tabContainer.addView(inner)
        return tabContainer
    }

    fun selectTab(tab: MainTab, animate: Boolean = true) {
        selectedTab = tab
        val target = tab.ordinal.toFloat()
        if (!animate) {
            lensSpring.cancel()
            lensPosition = target
            invalidate()
        } else {
            lensSpring.cancel()
            lensSpringHolder.value = lensPosition
            lensSpring.animateToFinalPosition(target)
        }
        refreshTabColors()
    }

    @Suppress("UNUSED_PARAMETER")
    fun setUnreadCount(count: Int) {
        // Real Telegram 13.3 keeps the Chats tab twin-bubble icon clean without a corner badge
        chatsBadgeView?.visibility = View.GONE
    }

    fun applyPalette(newPalette: TelegramPalette) {
        palette = newPalette
        chatsBadgeView?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(8f)
            setColor(newPalette.accentStrong)
        }
        refreshTabColors()
        invalidate()
    }

    private fun refreshTabColors() {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val color = if (isSelected) palette.accent else palette.glassTabInactive
            if (tab != MainTab.PROFILE) {
                iconViews[tab]?.imageTintList = ColorStateList.valueOf(color)
            }
            labelViews[tab]?.setTextColor(color)
            labelViews[tab]?.typeface = Typeface.create(
                "sans-serif-medium",
                if (isSelected) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = h * 0.5f
        pillRect.set(0f, 0f, w, h)
        clipPath.reset()
        clipPath.addRoundRect(pillRect, radius, radius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clipPath)

        // Real blurred backdrop on API 31+
        var hasLiveBlur = false
        val src = backdropSource
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            blurNode != null &&
            src != null &&
            canvas.isHardwareAccelerated &&
            !isDrawingBackdrop
        ) {
            isDrawingBackdrop = true
            try {
                blurNode.setPosition(0, 0, width, height)
                val rec = blurNode.beginRecording()
                rec.translate(src.left - left.toFloat(), src.top - top.toFloat())
                src.draw(rec)
                blurNode.endRecording()
                canvas.drawRenderNode(blurNode)
                hasLiveBlur = true
            } catch (_: Throwable) {
                hasLiveBlur = false
            } finally {
                isDrawingBackdrop = false
            }
        }

        // Translucent frosted tint overlay
        bgFillPaint.color = if (hasLiveBlur) {
            if (palette.isDark) 0xB8202020.toInt() else 0xCCF7F8FA.toInt()
        } else {
            palette.glassSurface
        }
        canvas.drawRect(pillRect, bgFillPaint)

        // Soft top-edge glass sheen
        rimPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                if (palette.isDark) 0x36FFFFFF else 0x55FFFFFF,
                if (palette.isDark) 0x10FFFFFF else 0x16000000
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            dpf(0.5f), dpf(0.5f), w - dpf(0.5f), h - dpf(0.5f),
            radius, radius,
            rimPaint
        )

        // Sliding active lens highlight
        val count = MainTab.entries.size
        val contentWidth = (width - paddingLeft - paddingRight).toFloat()
        if (contentWidth > 0f && count > 0) {
            val tabWidth = contentWidth / count
            val lensLeft = paddingLeft + lensPosition * tabWidth
            val lensTop = paddingTop.toFloat()
            val lensRight = lensLeft + tabWidth
            val lensBottom = (height - paddingBottom).toFloat()
            val frac = abs(lensPosition - lensPosition.toInt())
            val stretch = (4f * frac * (1f - frac)) * dpf(4f)
            lensRect.set(lensLeft - stretch, lensTop, lensRight + stretch, lensBottom)
            lensPaint.color = if (palette.isDark) {
                ColorUtils.setAlphaComponent(0xFF467E9E.toInt(), 76)
            } else {
                ColorUtils.setAlphaComponent(palette.accent, 34)
            }
            val lensRadius = (lensBottom - lensTop) * 0.5f
            canvas.drawRoundRect(lensRect, lensRadius, lensRadius, lensPaint)
        }

        canvas.restore()
        super.onDraw(canvas)
    }
}

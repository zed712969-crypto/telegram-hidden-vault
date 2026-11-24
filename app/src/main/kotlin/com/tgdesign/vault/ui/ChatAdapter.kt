package com.tgdesign.vault.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.tgdesign.vault.R
import com.tgdesign.vault.model.ChatItem
import com.tgdesign.vault.theme.TelegramPalette
import com.tgdesign.vault.theme.TelegramTheme

/**
 * RecyclerView adapter matching Telegram Android 13.3 DialogCell metrics with
 * Verified checkmark, Telegram Premium star, status emoji, muted speaker, top-line pinned pill,
 * and animated green avatar selection checkmark badge.
 */
class ChatAdapter(
    private val onChatClick: (ChatItem) -> Unit,
    private val onChatLongClick: (ChatItem) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val items = mutableListOf<ChatItem>()
    private var selectedIds: Set<Long> = emptySet()
    private var palette: TelegramPalette = TelegramTheme.Dark

    fun submitList(newItems: List<ChatItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setSelectedIds(ids: Set<Long>) {
        selectedIds = ids.toSet()
        notifyDataSetChanged()
    }

    fun applyPalette(newPalette: TelegramPalette) {
        palette = newPalette
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val density = parent.resources.displayMetrics.density
        fun dp(v: Float): Int = (v * density).toInt()

        val root = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72f)
            )
            isClickable = true
            isFocusable = true
        }

        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(14f), dp(8f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val avatarFrame = FrameLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56f), dp(56f)).apply {
                marginEnd = dp(10f)
            }
        }

        val avatarView = ImageView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(54f), dp(54f), Gravity.CENTER)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }

        val checkBadge = SelectionCheckBadgeView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.BOTTOM or Gravity.END)
            visibility = View.GONE
        }

        avatarFrame.addView(avatarView)
        avatarFrame.addView(checkBadge)
        row.addView(avatarFrame)

        val textColumn = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val topLine = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleGroup = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(8f)
            }
        }

        val titleView = TextView(parent.context).apply {
            textSize = 16.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(185f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val verifiedView = ImageView(parent.context).apply {
            setImageResource(R.drawable.ic_verified)
            layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply {
                marginStart = dp(5f)
            }
        }

        val premiumView = ImageView(parent.context).apply {
            setImageResource(R.drawable.ic_premium_star)
            layoutParams = LinearLayout.LayoutParams(dp(15f), dp(15f)).apply {
                marginStart = dp(5f)
            }
        }

        val statusEmojiView = TextView(parent.context).apply {
            textSize = 14f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(4f)
            }
        }

        val mutedView = ImageView(parent.context).apply {
            setImageResource(R.drawable.ic_muted)
            layoutParams = LinearLayout.LayoutParams(dp(15f), dp(15f)).apply {
                marginStart = dp(5f)
            }
        }

        titleGroup.addView(titleView)
        titleGroup.addView(verifiedView)
        titleGroup.addView(premiumView)
        titleGroup.addView(statusEmojiView)
        titleGroup.addView(mutedView)

        val readCheckView = ImageView(parent.context).apply {
            setImageResource(R.drawable.ic_done_all)
            layoutParams = LinearLayout.LayoutParams(dp(17f), dp(17f)).apply {
                marginEnd = dp(4f)
            }
        }

        val pinView = ImageView(parent.context).apply {
            setImageResource(R.drawable.ic_pin)
            rotation = 45f
            layoutParams = LinearLayout.LayoutParams(dp(12f), dp(12f)).apply {
                marginEnd = dp(3f)
            }
        }

        val timeView = TextView(parent.context).apply {
            textSize = 12.5f
            maxLines = 1
        }

        val timePill = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pinView)
            addView(timeView)
        }

        topLine.addView(titleGroup)
        topLine.addView(readCheckView)
        topLine.addView(timePill)

        val bottomLine = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2f)
            }
        }

        val messageView = TextView(parent.context).apply {
            textSize = 15f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dp(8f)
            }
        }

        val badgeView = TextView(parent.context).apply {
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            minWidth = dp(23f)
            setPadding(dp(7f), 0, dp(7f), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(23f)
            )
        }

        bottomLine.addView(messageView)
        bottomLine.addView(badgeView)

        textColumn.addView(topLine)
        textColumn.addView(bottomLine)
        row.addView(textColumn)
        root.addView(row)

        return ChatViewHolder(
            root = root,
            avatarView = avatarView,
            checkBadge = checkBadge,
            titleView = titleView,
            verifiedView = verifiedView,
            premiumView = premiumView,
            statusEmojiView = statusEmojiView,
            mutedView = mutedView,
            readCheckView = readCheckView,
            timePill = timePill,
            pinView = pinView,
            timeView = timeView,
            messageView = messageView,
            badgeView = badgeView,
            density = density
        )
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, item.id in selectedIds, palette, onChatClick, onChatLongClick)
    }

    override fun getItemCount(): Int = items.size

    class ChatViewHolder(
        private val root: FrameLayout,
        private val avatarView: ImageView,
        private val checkBadge: SelectionCheckBadgeView,
        private val titleView: TextView,
        private val verifiedView: ImageView,
        private val premiumView: ImageView,
        private val statusEmojiView: TextView,
        private val mutedView: ImageView,
        private val readCheckView: ImageView,
        private val timePill: LinearLayout,
        private val pinView: ImageView,
        private val timeView: TextView,
        private val messageView: TextView,
        private val badgeView: TextView,
        private val density: Float
    ) : RecyclerView.ViewHolder(root) {

        private fun dp(v: Float): Int = (v * density).toInt()

        fun bind(
            item: ChatItem,
            isSelected: Boolean,
            palette: TelegramPalette,
            onClick: (ChatItem) -> Unit,
            onLongClick: (ChatItem) -> Unit
        ) {
            val rowBg = if (isSelected) {
                if (palette.isDark) 0xFF23262A.toInt() else 0xFFEAF3FC.toInt()
            } else {
                palette.surface
            }
            val rippleColor = ColorUtils.setAlphaComponent(palette.textPrimary, if (palette.isDark) 28 else 18)
            root.background = RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                GradientDrawable().apply { setColor(rowBg) },
                null
            )

            if (item.avatarResId != null) {
                avatarView.scaleType = ImageView.ScaleType.CENTER_CROP
                avatarView.setImageResource(item.avatarResId)
            } else {
                avatarView.scaleType = ImageView.ScaleType.FIT_CENTER
                avatarView.setImageDrawable(
                    AvatarDrawable(
                        item.avatarColors.first,
                        item.avatarColors.second,
                        item.initials,
                        item.avatarGlyph
                    )
                )
            }

            checkBadge.setBorderColor(rowBg)
            checkBadge.visibility = if (isSelected) View.VISIBLE else View.GONE

            titleView.text = item.title
            titleView.setTextColor(palette.textPrimary)

            verifiedView.visibility = if (item.isVerified) View.VISIBLE else View.GONE

            if (!item.statusEmoji.isNullOrEmpty()) {
                statusEmojiView.visibility = View.VISIBLE
                statusEmojiView.text = item.statusEmoji
                premiumView.visibility = View.GONE
            } else if (item.isPremium) {
                statusEmojiView.visibility = View.GONE
                premiumView.visibility = View.VISIBLE
                premiumView.imageTintList = ColorStateList.valueOf(palette.accent)
            } else {
                statusEmojiView.visibility = View.GONE
                premiumView.visibility = View.GONE
            }

            if (item.isMuted) {
                mutedView.visibility = View.VISIBLE
                mutedView.imageTintList = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(palette.textSecondary, 175)
                )
            } else {
                mutedView.visibility = View.GONE
            }

            timeView.text = item.time
            timeView.setTextColor(palette.textSecondary)

            if (item.isOutgoingRead && item.unreadCount == 0) {
                readCheckView.visibility = View.VISIBLE
                readCheckView.imageTintList = ColorStateList.valueOf(palette.checkGreen)
            } else {
                readCheckView.visibility = View.GONE
            }

            if (item.isPinned) {
                pinView.visibility = View.VISIBLE
                pinView.imageTintList = ColorStateList.valueOf(palette.textSecondary)
                timePill.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f * density
                    setColor(palette.pinnedPillBg)
                }
                timePill.setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
            } else {
                pinView.visibility = View.GONE
                timePill.background = null
                timePill.setPadding(0, 0, 0, 0)
            }

            if (!item.senderPrefix.isNullOrEmpty()) {
                val builder = SpannableStringBuilder()
                val prefix = "${item.senderPrefix}: "
                builder.append(prefix)
                builder.setSpan(
                    ForegroundColorSpan(palette.textPrimary),
                    0,
                    prefix.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.append(item.lastMessage)
                builder.setSpan(
                    ForegroundColorSpan(palette.textSecondary),
                    prefix.length,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                messageView.text = builder
            } else {
                messageView.text = item.lastMessage
                messageView.setTextColor(palette.textSecondary)
            }

            if (item.unreadCount > 0) {
                badgeView.visibility = View.VISIBLE
                badgeView.text = item.unreadCount.toString()
                badgeView.setTextColor(palette.badgeText)
                badgeView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 11.5f * density
                    setColor(if (item.isMuted) palette.badgeMutedBg else palette.badgeBg)
                }
            } else {
                badgeView.visibility = View.GONE
            }

            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    /**
     * Green circular selection checkmark badge on bottom-right corner of avatar (Telegram 13.3).
     */
    class SelectionCheckBadgeView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private fun dp(v: Float): Float = v * density

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF181818.toInt()
            style = Paint.Style.FILL
        }
        private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3CC854.toInt()
            style = Paint.Style.FILL
        }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val tickPath = Path()

        fun setBorderColor(color: Int) {
            borderPaint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width * 0.5f
            val cy = height * 0.5f
            val outerR = minOf(width, height) * 0.5f
            val innerR = outerR - dp(2f)

            canvas.drawCircle(cx, cy, outerR, borderPaint)
            canvas.drawCircle(cx, cy, innerR, greenPaint)

            tickPath.reset()
            tickPath.moveTo(cx - dp(3.6f), cy + dp(0.2f))
            tickPath.lineTo(cx - dp(1.0f), cy + dp(2.8f))
            tickPath.lineTo(cx + dp(3.8f), cy - dp(2.6f))
            canvas.drawPath(tickPath, tickPaint)
        }
    }
}

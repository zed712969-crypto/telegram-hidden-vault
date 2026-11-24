package com.tgdesign.vault.ui

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tgdesign.vault.R
import com.tgdesign.vault.model.ChatFolder
import com.tgdesign.vault.model.ChatItem
import com.tgdesign.vault.model.ChatLocation
import com.tgdesign.vault.model.DemoChats
import com.tgdesign.vault.theme.TelegramPalette
import com.tgdesign.vault.theme.TelegramTheme

/**
 * Telegram Android 13.3 showcase implementing the Hidden Vault ("Второе дно"):
 * - Pull-down reveals standard Archived Chats; long-pressing Archived Chats opens Biometric -> Vault.
 * - Long-pressing any chat enters Telegram 13.3 Selection Mode with avatar check badge,
 *   ActionMode top bar (Mute, Archive, Delete, More), and anchored 3-dot popup menu with Hide in Vault.
 * - Floating frosted Liquid Glass bottom bar with real RenderNode backdrop blur.
 */
class MainActivity : AppCompatActivity() {

    private enum class ScreenMode {
        MAIN,
        ARCHIVE,
        VAULT
    }

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Float): Int = (v * density).toInt()
    private fun dpf(v: Float): Float = v * density

    private val chats = DemoChats.createInitial()
    private val selectedChatIds = linkedSetOf<Long>()
    private var palette: TelegramPalette = TelegramTheme.Dark
    private var currentMode: ScreenMode = ScreenMode.MAIN
    private var selectedFolder: ChatFolder = ChatFolder.ALL
    private var isSearchActive: Boolean = false
    private var searchQuery: String = ""

    private lateinit var rootFrame: FrameLayout
    private lateinit var mainColumn: LinearLayout
    private lateinit var topBarContainer: LinearLayout
    private lateinit var topBarContent: LinearLayout

    // Normal header controls
    private lateinit var storiesStack: FrameLayout
    private val storyRings = mutableListOf<FrameLayout>()
    private lateinit var backButton: ImageButton
    private lateinit var vaultTitleIcon: ImageView
    private lateinit var titleGroup: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var lockActionButton: ImageButton
    private lateinit var themeButton: ImageButton

    // Selection ActionMode controls (Telegram 13.3)
    private lateinit var selectionCloseButton: ImageButton
    private lateinit var selectionCountView: TextView
    private lateinit var selectionSpacer: View
    private lateinit var selectionMuteButton: ImageButton
    private lateinit var selectionArchiveButton: ImageButton
    private lateinit var selectionDeleteButton: ImageButton
    private lateinit var selectionMoreButton: ImageButton

    private lateinit var searchBarWrapper: FrameLayout
    private lateinit var searchPill: LinearLayout
    private lateinit var searchPillIcon: ImageView
    private lateinit var searchInput: EditText
    private lateinit var searchCancelButton: TextView
    private lateinit var topBarDivider: View

    private lateinit var folderScroll: HorizontalScrollView
    private lateinit var folderTabsContainer: FrameLayout
    private lateinit var folderRow: LinearLayout
    private lateinit var folderIndicator: View
    private val folderTabViews = mutableMapOf<ChatFolder, Pair<LinearLayout, TextView>>()
    private val folderBadgeViews = mutableMapOf<ChatFolder, TextView>()

    private lateinit var vaultBanner: LinearLayout
    private lateinit var vaultBannerLabel: TextView
    private lateinit var vaultBannerAction: TextView

    private lateinit var vaultLayout: TwoStageVaultLayout
    private lateinit var archiveRow: LinearLayout
    private lateinit var archiveIconBg: ImageView
    private lateinit var archiveTitleView: TextView
    private lateinit var archiveSubtitleView: TextView
    private lateinit var archiveBadgeView: TextView

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateView: TextView
    private lateinit var listHostFrame: FrameLayout
    private lateinit var adapter: ChatAdapter

    private lateinit var glassBottomBar: GlassBottomBarView
    private lateinit var cameraFab: ImageButton
    private lateinit var composeFab: ImageButton
    private lateinit var touchCursorOverlay: TouchCursorOverlayView

    private var activeBottomSheet: BottomSheetDialog? = null
    private var selectionPopupWindow: PopupWindow? = null
    private var isBiometricInProgress = false
    private var indicatorAnimator: ValueAnimator? = null
    private var topBarColorAnimator: ValueAnimator? = null
    private var blurAnimator: ValueAnimator? = null

    private val isSelectionMode: Boolean
        get() = selectedChatIds.isNotEmpty()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::touchCursorOverlay.isInitialized) {
            touchCursorOverlay.onHostTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::touchCursorOverlay.isInitialized) {
            touchCursorOverlay.forceFadeOut()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        buildUi()
        setContentView(rootFrame)
        setupInsets()
        setupBackHandling()

        applyTheme(palette)
        refreshAll()
    }

    override fun onStop() {
        super.onStop()
        selectionPopupWindow?.dismiss()
        selectionPopupWindow = null
        activeBottomSheet?.dismiss()
        activeBottomSheet = null
        setBackgroundBlur(false, animate = false)

        if (currentMode == ScreenMode.VAULT && !isBiometricInProgress) {
            selectedChatIds.clear()
            currentMode = ScreenMode.MAIN
            vaultLayout.collapseImmediately()
            refreshAll()
        }
    }

    private fun buildUi() {
        rootFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top Bar
        topBarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        topBarContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), 0, dp(8f), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54f)
            )
        }

        // 3 overlapping story avatars in circular ring frames (Telegram 13.3 header)
        storiesStack = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(60f), dp(30f)).apply {
                marginEnd = dp(8f)
            }
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Stories demo · Pull down for Archive, hold Archive for Vault",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        storyRings.clear()
        val storyAvatars = listOf(R.drawable.avatar_01, R.drawable.avatar_02, R.drawable.avatar_03)
        storyAvatars.forEachIndexed { idx, resId ->
            val ring = FrameLayout(this).apply {
                setPadding(dp(1.5f), dp(1.5f), dp(1.5f), dp(1.5f))
                layoutParams = FrameLayout.LayoutParams(dp(30f), dp(30f), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                    marginStart = dp(idx * 15f)
                }
            }
            val avatar = ImageView(this).apply {
                setImageResource(resId)
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                clipToOutline = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            ring.addView(avatar)
            storyRings.add(ring)
            storiesStack.addView(ring)
        }

        backButton = createIconButton(R.drawable.ic_back, "Back") {
            handleBackNavigation()
        }.apply {
            visibility = View.GONE
        }

        vaultTitleIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_lock)
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                marginStart = dp(4f)
                marginEnd = dp(8f)
            }
            visibility = View.GONE
        }

        titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleView = TextView(this).apply {
            text = "Telegram"
            textSize = 21f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
        }
        titleGroup.addView(titleView)

        lockActionButton = createIconButton(R.drawable.ic_lock, "Lock Vault") {
            lockVault()
        }.apply {
            visibility = View.GONE
        }

        themeButton = createIconButton(R.drawable.ic_more_vert, "Menu / Toggle theme") {
            applyTheme(if (palette.isDark) TelegramTheme.Light else TelegramTheme.Dark)
        }.apply {
            setOnLongClickListener {
                touchCursorOverlay.isCursorEnabled = !touchCursorOverlay.isCursorEnabled
                val status = if (touchCursorOverlay.isCursorEnabled) "ON" else "OFF"
                Toast.makeText(
                    this@MainActivity,
                    "Touch cursor $status for video recording",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        }

        // Selection ActionMode views inside topBarContent
        selectionCloseButton = createIconButton(R.drawable.ic_close, "Clear selection") {
            clearSelection()
        }.apply {
            visibility = View.GONE
        }

        selectionCountView = TextView(this).apply {
            text = "1"
            textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(18f)
            }
            visibility = View.GONE
        }

        selectionSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            visibility = View.GONE
        }

        selectionMuteButton = createIconButton(R.drawable.ic_action_mute, "Mute / Unmute") {
            toggleMuteSelected()
        }.apply {
            visibility = View.GONE
        }

        selectionArchiveButton = createIconButton(R.drawable.ic_action_archive, "Archive / Unarchive") {
            toggleArchiveSelected()
        }.apply {
            visibility = View.GONE
        }

        selectionDeleteButton = createIconButton(R.drawable.ic_action_delete, "Delete") {
            deleteSelected()
        }.apply {
            visibility = View.GONE
        }

        selectionMoreButton = createIconButton(R.drawable.ic_more_vert, "More options") {
            showSelectionOverflowPopup(it)
        }.apply {
            visibility = View.GONE
        }

        topBarContent.addView(storiesStack)
        topBarContent.addView(backButton)
        topBarContent.addView(vaultTitleIcon)
        topBarContent.addView(titleGroup)
        topBarContent.addView(lockActionButton)
        topBarContent.addView(themeButton)

        topBarContent.addView(selectionCloseButton)
        topBarContent.addView(selectionCountView)
        topBarContent.addView(selectionSpacer)
        topBarContent.addView(selectionMuteButton)
        topBarContent.addView(selectionArchiveButton)
        topBarContent.addView(selectionDeleteButton)
        topBarContent.addView(selectionMoreButton)

        // Rounded Search Chats pill bar right below header
        searchBarWrapper = FrameLayout(this).apply {
            setPadding(dp(12f), dp(2f), dp(12f), dp(8f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        searchPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), 0, dp(14f), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40f)
            )
            setOnClickListener {
                activateSearchFocus()
            }
        }

        searchPillIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply {
                marginEnd = dp(10f)
            }
        }

        searchInput = EditText(this).apply {
            hint = "Search Chats"
            textSize = 15.5f
            background = null
            isSingleLine = true
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && !isSearchActive) {
                    isSearchActive = true
                    vaultLayout.collapseImmediately()
                    refreshAll()
                }
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    searchQuery = s?.toString().orEmpty()
                    if (searchQuery.isNotEmpty() && !isSearchActive) {
                        isSearchActive = true
                        refreshAll()
                    } else {
                        refreshChatList()
                    }
                }
            })
        }

        searchCancelButton = TextView(this).apply {
            text = "Cancel"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(8f), dp(4f), dp(4f), dp(4f))
            visibility = View.GONE
            setOnClickListener { closeSearch() }
        }

        searchPill.addView(searchPillIcon)
        searchPill.addView(searchInput)
        searchPill.addView(searchCancelButton)
        searchBarWrapper.addView(searchPill)

        topBarDivider = View(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxOf(1, dp(0.6f))
            )
        }

        topBarContainer.addView(topBarContent)
        topBarContainer.addView(searchBarWrapper)
        topBarContainer.addView(buildFolderStrip())
        topBarContainer.addView(topBarDivider)
        mainColumn.addView(topBarContainer)

        // Vault privacy status banner
        vaultBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(9f), dp(14f), dp(9f))
            visibility = View.GONE
        }
        vaultBannerLabel = TextView(this).apply {
            text = "Hidden chats · Invisible in search & forwarding"
            textSize = 12.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        vaultBannerAction = TextView(this).apply {
            text = "Lock"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            setOnClickListener { lockVault() }
        }
        vaultBanner.addView(vaultBannerLabel)
        vaultBanner.addView(vaultBannerAction)
        mainColumn.addView(vaultBanner)

        // Chat list host
        listHostFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        vaultLayout = TwoStageVaultLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onPullOffsetChanged = {
                if (::glassBottomBar.isInitialized) {
                    glassBottomBar.invalidate()
                }
            }
        }

        archiveRow = buildArchiveRow()

        adapter = ChatAdapter(
            onChatClick = { item ->
                if (isSelectionMode) {
                    toggleChatSelection(item)
                } else {
                    showChatPreviewSheet(item)
                }
            },
            onChatLongClick = { item ->
                performHapticFeedbackForRoot()
                toggleChatSelection(item)
            }
        )

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            clipToPadding = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (::glassBottomBar.isInitialized) {
                        glassBottomBar.invalidate()
                    }
                }
            })
        }

        vaultLayout.attachContent(archiveRow, recyclerView)
        listHostFrame.addView(vaultLayout)

        emptyStateView = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(32f), dp(32f), dp(32f), dp(32f))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        listHostFrame.addView(emptyStateView)
        mainColumn.addView(listHostFrame)
        rootFrame.addView(mainColumn)

        // Floating Liquid Glass bottom navigation bar with live backdrop blur
        val screenWidth = resources.displayMetrics.widthPixels
        val barWidth = minOf(dp(332f), screenWidth - dp(28f))
        glassBottomBar = GlassBottomBarView(this).apply {
            backdropSource = mainColumn
            layoutParams = FrameLayout.LayoutParams(
                barWidth,
                dp(62f),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(10f)
            }
            onTabSelected = { tab ->
                if (tab != GlassBottomBarView.MainTab.CHATS) {
                    Toast.makeText(
                        this@MainActivity,
                        "Demo tab \"${tab.title}\" · Hold Archived Chats for Vault",
                        Toast.LENGTH_SHORT
                    ).show()
                    postDelayed({
                        selectTab(GlassBottomBarView.MainTab.CHATS, animate = true)
                    }, 360L)
                }
            }
        }
        rootFrame.addView(glassBottomBar)

        // Story Camera mini-FAB above Compose FAB
        cameraFab = ImageButton(this).apply {
            setImageResource(R.drawable.ic_camera_story)
            contentDescription = "New story"
            elevation = dpf(6f)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = FrameLayout.LayoutParams(
                dp(42f),
                dp(42f),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                marginEnd = dp(23f)
                bottomMargin = dp(150f)
            }
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Stories demo · Hold Archived Chats for Vault",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        rootFrame.addView(cameraFab)

        // Floating Compose Chat FAB above the glass bar
        composeFab = ImageButton(this).apply {
            setImageResource(R.drawable.ic_new_chat)
            contentDescription = "New message"
            elevation = dpf(8f)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = FrameLayout.LayoutParams(
                dp(56f),
                dp(56f),
                Gravity.BOTTOM or Gravity.END
            ).apply {
                marginEnd = dp(16f)
                bottomMargin = dp(84f)
            }
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Long-press any chat -> ⋮ -> Hide in Vault",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        rootFrame.addView(composeFab)

        touchCursorOverlay = TouchCursorOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootFrame.addView(touchCursorOverlay)
    }

    private fun buildFolderStrip(): HorizontalScrollView {
        folderScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38f)
            ).apply {
                marginStart = dp(12f)
                marginEnd = dp(12f)
                bottomMargin = dp(6f)
            }
        }

        folderTabsContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38f)
            )
        }

        folderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), 0, dp(4f), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38f)
            )
        }

        ChatFolder.entries.forEach { folder ->
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(14f), 0, dp(14f), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(38f)
                )
                setOnClickListener {
                    if (selectedFolder != folder) {
                        selectedFolder = folder
                        refreshFolderTabs(animateIndicator = true)
                        refreshChatList()
                    }
                }
            }

            val label = TextView(this).apply {
                text = folder.title
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

            val badge = TextView(this).apply {
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                minWidth = dp(18f)
                setPadding(dp(5f), 0, dp(5f), 0)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(18f)
                ).apply {
                    marginStart = dp(6f)
                }
            }

            tab.addView(label)
            tab.addView(badge)
            folderRow.addView(tab)
            folderTabViews[folder] = tab to label
            folderBadgeViews[folder] = badge
        }

        folderIndicator = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(48f), dp(32f), Gravity.CENTER_VERTICAL or Gravity.START)
        }

        folderTabsContainer.addView(folderIndicator)
        folderTabsContainer.addView(folderRow)
        folderScroll.addView(folderTabsContainer)
        return folderScroll
    }

    private fun buildArchiveRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(8f), dp(14f), dp(8f))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (!isSelectionMode) {
                    openArchive()
                }
            }
            // Option 3: Long-pressing Archived Chats triggers the Hidden Vault ("second bottom")
            setOnLongClickListener {
                if (isSelectionMode) return@setOnLongClickListener false
                val haptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
                it.performHapticFeedback(haptic)
                requestVaultUnlock()
                true
            }
        }

        archiveIconBg = ImageView(this).apply {
            setImageResource(R.drawable.ic_archive)
            setPadding(dp(13f), dp(13f), dp(13f), dp(13f))
            layoutParams = LinearLayout.LayoutParams(dp(54f), dp(54f)).apply {
                marginStart = dp(1f)
                marginEnd = dp(11f)
            }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        archiveTitleView = TextView(this).apply {
            text = "Archived Chats"
            textSize = 16.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        archiveSubtitleView = TextView(this).apply {
            textSize = 14.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        textCol.addView(archiveTitleView)
        textCol.addView(archiveSubtitleView)

        archiveBadgeView = TextView(this).apply {
            textSize = 12.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            minWidth = dp(23f)
            setPadding(dp(7f), 0, dp(7f), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(23f)
            ).apply {
                marginStart = dp(8f)
            }
        }

        row.addView(archiveIconBg)
        row.addView(textCol)
        row.addView(archiveBadgeView)
        return row
    }

    private fun createIconButton(iconRes: Int, description: String, onClick: (View) -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconRes)
            contentDescription = description
            layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f))
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { onClick(it) }
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootFrame) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBarContainer.setPadding(0, bars.top, 0, 0)
            recyclerView.setPadding(0, 0, 0, bars.bottom + dp(96f))

            (glassBottomBar.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bars.bottom + dp(10f)
                glassBottomBar.layoutParams = this
            }
            (cameraFab.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bars.bottom + dp(150f)
                cameraFab.layoutParams = this
            }
            (composeFab.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bars.bottom + dp(84f)
                composeFab.layoutParams = this
            }
            insets
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!handleBackNavigation()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun handleBackNavigation(): Boolean {
        if (selectionPopupWindow?.isShowing == true) {
            selectionPopupWindow?.dismiss()
            return true
        }
        if (isSelectionMode) {
            clearSelection()
            return true
        }
        if (isSearchActive) {
            closeSearch()
            return true
        }
        if (currentMode == ScreenMode.VAULT) {
            lockVault()
            return true
        }
        if (currentMode == ScreenMode.ARCHIVE) {
            currentMode = ScreenMode.MAIN
            refreshAll()
            return true
        }
        return false
    }

    private fun performHapticFeedbackForRoot() {
        rootFrame.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun toggleChatSelection(item: ChatItem) {
        if (item.id in selectedChatIds) {
            selectedChatIds.remove(item.id)
        } else {
            selectedChatIds.add(item.id)
        }
        adapter.setSelectedIds(selectedChatIds)
        updateTopBar()
    }

    private fun clearSelection() {
        selectionPopupWindow?.dismiss()
        if (selectedChatIds.isEmpty()) return
        selectedChatIds.clear()
        adapter.setSelectedIds(selectedChatIds)
        updateTopBar()
    }

    private fun selectedItems(): List<ChatItem> {
        return chats.filter { it.id in selectedChatIds }
    }

    private fun toggleMuteSelected() {
        val selected = selectedItems()
        if (selected.isEmpty()) return
        val anyUnmuted = selected.any { !it.isMuted }
        selected.forEach { it.isMuted = anyUnmuted }
        clearSelection()
        refreshAll()
    }

    private fun toggleArchiveSelected() {
        val selected = selectedItems()
        if (selected.isEmpty()) return
        val moveToMain = currentMode == ScreenMode.ARCHIVE
        selected.forEach {
            it.location = if (moveToMain) ChatLocation.MAIN else ChatLocation.ARCHIVE
        }
        clearSelection()
        refreshAll()
        Toast.makeText(
            this,
            if (moveToMain) "Unarchived" else "Moved to Archived Chats",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteSelected() {
        val count = selectedChatIds.size
        if (count == 0) return
        chats.removeAll { it.id in selectedChatIds }
        clearSelection()
        refreshAll()
        Toast.makeText(this, "Deleted $count chat(s)", Toast.LENGTH_SHORT).show()
    }

    private fun showSelectionOverflowPopup(anchor: View) {
        touchCursorOverlay.forceFadeOut()
        selectionPopupWindow?.dismiss()

        val selected = selectedItems()
        if (selected.isEmpty()) return

        val popupCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6f), 0, dp(6f))
            elevation = dpf(14f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(12f)
                setColor(if (palette.isDark) 0xFF252525.toInt() else Color.WHITE)
                setStroke(dp(0.6f), palette.glassBorder)
            }
        }

        val popup = PopupWindow(
            popupCard,
            dp(224f),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dpf(14f)
            isOutsideTouchable = true
        }

        val inVault = currentMode == ScreenMode.VAULT
        val vaultLabel = if (inVault) "Remove from Vault" else "Hide in Vault"
        popupCard.addView(createPopupMenuItem(vaultLabel, R.drawable.ic_lock, palette.accent) {
            selected.forEach {
                it.location = if (inVault) ChatLocation.MAIN else ChatLocation.VAULT
            }
            popup.dismiss()
            clearSelection()
            refreshAll()
            Toast.makeText(
                this,
                if (inVault) "Moved back to Chats" else "Hidden in Vault 🔒",
                Toast.LENGTH_SHORT
            ).show()
        })

        val anyUnpinned = selected.any { !it.isPinned }
        popupCard.addView(
            createPopupMenuItem(
                if (anyUnpinned) "Pin" else "Unpin",
                R.drawable.ic_popup_pin,
                palette.textPrimary
            ) {
                selected.forEach { it.isPinned = anyUnpinned }
                popup.dismiss()
                clearSelection()
                refreshAll()
            }
        )

        val anyRead = selected.any { it.unreadCount == 0 }
        popupCard.addView(
            createPopupMenuItem(
                if (anyRead) "Mark as unread" else "Mark as read",
                R.drawable.ic_mark_unread,
                palette.textPrimary
            ) {
                selected.forEach {
                    it.unreadCount = if (anyRead) maxOf(1, it.unreadCount) else 0
                }
                popup.dismiss()
                clearSelection()
                refreshAll()
            }
        )

        popupCard.addView(
            createPopupMenuItem("Clear History", R.drawable.ic_clear_history, palette.textPrimary) {
                selected.forEach {
                    it.lastMessage = "History cleared"
                    it.unreadCount = 0
                }
                popup.dismiss()
                clearSelection()
                refreshAll()
            }
        )

        popupCard.addView(
            createPopupMenuItem("Block user", R.drawable.ic_block, palette.textPrimary) {
                chats.removeAll { it.id in selectedChatIds }
                popup.dismiss()
                clearSelection()
                refreshAll()
                Toast.makeText(this, "User blocked", Toast.LENGTH_SHORT).show()
            }
        )

        selectionPopupWindow = popup
        popup.showAsDropDown(anchor, -dp(176f), -dp(6f))
    }

    private fun createPopupMenuItem(
        label: String,
        iconRes: Int,
        tintColor: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val ripple = ColorUtils.setAlphaComponent(palette.textPrimary, 24)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            background = RippleDrawable(ColorStateList.valueOf(ripple), null, null)
            setOnClickListener { onClick() }

            addView(ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(tintColor)
                layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply {
                    marginEnd = dp(18f)
                }
            })

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 15.5f
                setTextColor(if (tintColor == palette.accent) palette.accent else palette.textPrimary)
            })
        }
    }

    private fun activateSearchFocus() {
        clearSelection()
        isSearchActive = true
        vaultLayout.collapseImmediately()
        refreshAll()
        searchInput.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        isSearchActive = false
        searchQuery = ""
        searchInput.setText("")
        searchInput.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        refreshAll()
    }

    private fun openArchive() {
        clearSelection()
        vaultLayout.collapseImmediately()
        currentMode = ScreenMode.ARCHIVE
        refreshAll()
    }

    private fun lockVault() {
        if (currentMode != ScreenMode.VAULT) return
        clearSelection()
        val fromColor = palette.vaultHeader
        val toColor = palette.topBar
        currentMode = ScreenMode.MAIN
        vaultLayout.collapseImmediately()
        refreshAll()
        animateTopBarColor(fromColor, toColor, 240L)

        recyclerView.alpha = 0.35f
        recyclerView.scaleX = 0.97f
        recyclerView.scaleY = 0.97f
        recyclerView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun setBackgroundBlur(enabled: Boolean, animate: Boolean = true) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        blurAnimator?.cancel()
        if (!animate) {
            mainColumn.setRenderEffect(
                if (enabled) RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP) else null
            )
            return
        }
        val start = if (enabled) 0.5f else 20f
        val end = if (enabled) 20f else 0f
        blurAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val r = anim.animatedValue as Float
                if (r < 0.5f) {
                    mainColumn.setRenderEffect(null)
                } else {
                    mainColumn.setRenderEffect(
                        RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
                    )
                }
            }
            start()
        }
    }

    private fun requestVaultUnlock() {
        touchCursorOverlay.forceFadeOut()
        val useSystemBiometricPrompt = false
        if (!useSystemBiometricPrompt) {
            activeBottomSheet?.dismiss()
            setBackgroundBlur(true)
            val sheet = RecordableBiometricSheet.show(this, palette) {
                setBackgroundBlur(false)
                enterVault()
            }
            sheet.setOnDismissListener {
                setBackgroundBlur(false)
            }
            activeBottomSheet = sheet
            return
        }

        val manager = BiometricManager.from(this)
        val supportsDeviceCredential = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val authenticators = if (supportsDeviceCredential) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        if (manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            isBiometricInProgress = true
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        isBiometricInProgress = false
                        enterVault()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        isBiometricInProgress = false
                    }

                    override fun onAuthenticationFailed() = Unit
                }
            )
            val infoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Authenticate to access hidden chats")
                .setAllowedAuthenticators(authenticators)
            if (!supportsDeviceCredential) {
                infoBuilder.setNegativeButtonText("Cancel")
            }
            prompt.authenticate(infoBuilder.build())
        }
    }

    private fun enterVault() {
        val fromColor = palette.topBar
        val toColor = palette.vaultHeader
        clearSelection()
        isSearchActive = false
        searchQuery = ""
        searchInput.setText("")
        searchInput.clearFocus()
        currentMode = ScreenMode.VAULT
        vaultLayout.collapseImmediately()

        glassBottomBar.animate().cancel()
        cameraFab.animate().cancel()
        composeFab.animate().cancel()
        glassBottomBar.alpha = 0f
        glassBottomBar.translationY = dpf(96f)
        glassBottomBar.visibility = View.GONE
        cameraFab.alpha = 0f
        cameraFab.translationY = dpf(96f)
        cameraFab.visibility = View.GONE
        composeFab.alpha = 0f
        composeFab.translationY = dpf(96f)
        composeFab.visibility = View.GONE

        refreshAll()
        animateTopBarColor(fromColor, toColor, 260L)

        vaultTitleIcon.scaleX = 0.6f
        vaultTitleIcon.scaleY = 0.6f
        vaultTitleIcon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        vaultBanner.translationY = -dpf(14f)
        vaultBanner.alpha = 0f
        vaultBanner.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        recyclerView.alpha = 0f
        recyclerView.scaleX = 0.95f
        recyclerView.scaleY = 0.95f
        recyclerView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()

        recyclerView.post {
            val count = recyclerView.childCount
            for (i in 0 until count) {
                val child = recyclerView.getChildAt(i)
                child.translationY = dpf(14f)
                child.animate()
                    .translationY(0f)
                    .setStartDelay(i * 25L)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator(1.4f))
                    .start()
            }
        }
    }

    private fun animateTopBarColor(fromColor: Int, toColor: Int, durationMs: Long) {
        topBarColorAnimator?.cancel()
        topBarColorAnimator = ValueAnimator.ofArgb(fromColor, toColor).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                topBarContainer.setBackgroundColor(it.animatedValue as Int)
            }
            start()
        }
    }

    private fun showChatPreviewSheet(item: ChatItem) {
        touchCursorOverlay.forceFadeOut()
        activeBottomSheet?.dismiss()
        val sheetBg = if (palette.isDark) 0xFF1F1F1F.toInt() else Color.WHITE
        val dialog = BottomSheetDialog(this)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(24f))
            background = GradientDrawable().apply {
                setColor(sheetBg)
                cornerRadii = floatArrayOf(
                    dpf(20f), dpf(20f), dpf(20f), dpf(20f),
                    0f, 0f, 0f, 0f
                )
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14f)
            }
        }

        val avatar = ImageView(this).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            if (item.avatarResId != null) {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(item.avatarResId)
            } else {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(
                    AvatarDrawable(
                        item.avatarColors.first,
                        item.avatarColors.second,
                        item.initials,
                        item.avatarGlyph
                    )
                )
            }
            layoutParams = LinearLayout.LayoutParams(dp(48f), dp(48f)).apply {
                marginEnd = dp(12f)
            }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = item.title
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(palette.textPrimary)
        }
        val locationText = when (item.location) {
            ChatLocation.MAIN -> "Main list"
            ChatLocation.ARCHIVE -> "Archived"
            ChatLocation.VAULT -> "Hidden Vault"
        }
        val subtitle = TextView(this).apply {
            text = "$locationText · ${item.time}"
            textSize = 13f
            setTextColor(palette.accent)
        }
        titleCol.addView(title)
        titleCol.addView(subtitle)
        header.addView(avatar)
        header.addView(titleCol)

        val previewText = if (!item.senderPrefix.isNullOrEmpty()) {
            "${item.senderPrefix}: ${item.lastMessage}"
        } else {
            item.lastMessage
        }
        val messageBox = TextView(this).apply {
            text = previewText
            textSize = 15f
            setTextColor(palette.textPrimary)
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = GradientDrawable().apply {
                setColor(palette.elevated)
                cornerRadius = dpf(14f)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16f)
            }
        }

        val primaryActionLabel = if (item.location == ChatLocation.VAULT) {
            "Remove from Vault"
        } else {
            "Hide in Vault"
        }
        val actionBtn = TextView(this).apply {
            text = primaryActionLabel
            textSize = 15.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(13f), 0, dp(13f))
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 55)),
                GradientDrawable().apply {
                    setColor(palette.accentStrong)
                    cornerRadius = dpf(12f)
                },
                null
            )
            setOnClickListener {
                item.location = if (item.location == ChatLocation.VAULT) {
                    ChatLocation.MAIN
                } else {
                    ChatLocation.VAULT
                }
                dialog.dismiss()
                refreshAll()
            }
        }

        sheet.addView(header)
        sheet.addView(messageBox)
        sheet.addView(actionBtn)

        dialog.setContentView(sheet)
        activeBottomSheet = dialog
        dialog.show()
    }

    private fun refreshAll() {
        updateTopBar()
        refreshFolderTabs(animateIndicator = false)
        refreshArchiveRow()
        refreshChatList()
        refreshBottomChrome()
        glassBottomBar.post { glassBottomBar.invalidate() }
    }

    private fun updateTopBar() {
        val isMain = currentMode == ScreenMode.MAIN
        val selecting = isSelectionMode
        vaultLayout.setPullEnabled(isMain && !isSearchActive && !selecting)
        topBarContainer.setBackgroundColor(
            if (currentMode == ScreenMode.VAULT) palette.vaultHeader else palette.topBar
        )

        if (selecting) {
            storiesStack.visibility = View.GONE
            backButton.visibility = View.GONE
            vaultTitleIcon.visibility = View.GONE
            titleGroup.visibility = View.GONE
            lockActionButton.visibility = View.GONE
            themeButton.visibility = View.GONE

            selectionCloseButton.visibility = View.VISIBLE
            selectionCountView.visibility = View.VISIBLE
            selectionCountView.text = selectedChatIds.size.toString()
            selectionSpacer.visibility = View.VISIBLE
            selectionMuteButton.visibility = View.VISIBLE
            selectionArchiveButton.visibility = View.VISIBLE
            selectionDeleteButton.visibility = View.VISIBLE
            selectionMoreButton.visibility = View.VISIBLE
        } else {
            selectionCloseButton.visibility = View.GONE
            selectionCountView.visibility = View.GONE
            selectionSpacer.visibility = View.GONE
            selectionMuteButton.visibility = View.GONE
            selectionArchiveButton.visibility = View.GONE
            selectionDeleteButton.visibility = View.GONE
            selectionMoreButton.visibility = View.GONE

            storiesStack.visibility = if (isMain && !isSearchActive) View.VISIBLE else View.GONE
            backButton.visibility = if (!isMain || isSearchActive) View.VISIBLE else View.GONE
            vaultTitleIcon.visibility = if (currentMode == ScreenMode.VAULT) View.VISIBLE else View.GONE
            titleGroup.visibility = View.VISIBLE
            lockActionButton.visibility = if (currentMode == ScreenMode.VAULT) View.VISIBLE else View.GONE
            themeButton.visibility = View.VISIBLE

            titleView.text = when (currentMode) {
                ScreenMode.MAIN -> "Telegram"
                ScreenMode.ARCHIVE -> "Archived Chats"
                ScreenMode.VAULT -> "Vault"
            }
        }

        searchBarWrapper.visibility = if (isMain && !selecting) View.VISIBLE else View.GONE
        searchCancelButton.visibility = if (isSearchActive) View.VISIBLE else View.GONE

        folderScroll.visibility = if (isMain && !isSearchActive) View.VISIBLE else View.GONE
        vaultBanner.visibility = if (currentMode == ScreenMode.VAULT) View.VISIBLE else View.GONE
    }

    private fun refreshBottomChrome() {
        val showBottomChrome = currentMode == ScreenMode.MAIN && !isSearchActive
        val totalMainUnread = chats
            .filter { it.location == ChatLocation.MAIN && !it.isMuted && it.unreadCount > 0 }
            .sumOf { it.unreadCount }
        glassBottomBar.setUnreadCount(totalMainUnread)

        glassBottomBar.animate().cancel()
        cameraFab.animate().cancel()
        composeFab.animate().cancel()
        if (showBottomChrome) {
            glassBottomBar.visibility = View.VISIBLE
            cameraFab.visibility = View.VISIBLE
            composeFab.visibility = View.VISIBLE
            glassBottomBar.animate().alpha(1f).translationY(0f).setDuration(200L).start()
            cameraFab.animate().alpha(1f).translationY(0f).setDuration(200L).start()
            composeFab.animate().alpha(1f).translationY(0f).setDuration(200L).start()
        } else if (currentMode == ScreenMode.VAULT) {
            glassBottomBar.alpha = 0f
            glassBottomBar.translationY = dpf(96f)
            glassBottomBar.visibility = View.GONE
            cameraFab.alpha = 0f
            cameraFab.translationY = dpf(96f)
            cameraFab.visibility = View.GONE
            composeFab.alpha = 0f
            composeFab.translationY = dpf(96f)
            composeFab.visibility = View.GONE
        } else {
            glassBottomBar.animate().alpha(0f).translationY(dpf(96f)).setDuration(180L).start()
            cameraFab.animate().alpha(0f).translationY(dpf(96f)).setDuration(180L).start()
            composeFab.animate().alpha(0f).translationY(dpf(96f)).setDuration(180L).start()
        }
        glassBottomBar.isEnabled = showBottomChrome
        cameraFab.isEnabled = showBottomChrome
        composeFab.isEnabled = showBottomChrome
    }

    private fun refreshFolderTabs(animateIndicator: Boolean) {
        val mainChats = chats.filter { it.location == ChatLocation.MAIN }
        ChatFolder.entries.forEach { folder ->
            val (tab, label) = folderTabViews[folder] ?: return@forEach
            val badge = folderBadgeViews[folder] ?: return@forEach
            val selected = folder == selectedFolder

            label.setTextColor(if (selected) palette.accent else palette.textPrimary)
            label.typeface = Typeface.create(
                "sans-serif-medium",
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )

            val unread = mainChats
                .filter { folder in it.folders && it.unreadCount > 0 }
                .sumOf { it.unreadCount }

            if (unread > 0 && folder != ChatFolder.ALL) {
                badge.visibility = View.VISIBLE
                badge.text = unread.toString()
                badge.setTextColor(if (palette.isDark) 0xFF181818.toInt() else Color.WHITE)
                badge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(9f)
                    setColor(if (palette.isDark) 0xFF9E9E9E.toInt() else 0xFF8D969E.toInt())
                }
            } else {
                badge.visibility = View.GONE
            }

            if (selected) {
                tab.post { updateFolderIndicator(tab, animateIndicator) }
            }
        }
    }

    private fun updateFolderIndicator(tab: View, animate: Boolean) {
        val targetWidth = maxOf(dp(36f), tab.width - dp(6f))
        val targetX = tab.left + (tab.width - targetWidth) * 0.5f

        val params = folderIndicator.layoutParams as FrameLayout.LayoutParams
        if (!animate || folderIndicator.width == 0) {
            params.width = targetWidth
            folderIndicator.layoutParams = params
            folderIndicator.translationX = targetX
            return
        }

        indicatorAnimator?.cancel()
        val startX = folderIndicator.translationX
        val startW = params.width
        indicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                folderIndicator.translationX = startX + (targetX - startX) * f
                params.width = (startW + (targetWidth - startW) * f).toInt()
                folderIndicator.layoutParams = params
            }
            start()
        }
    }

    private fun refreshArchiveRow() {
        val archived = chats.filter { it.location == ChatLocation.ARCHIVE }
        archiveSubtitleView.text = if (archived.isEmpty()) {
            "No archived chats"
        } else {
            archived.joinToString(", ") { it.title }
        }
        val unread = archived.sumOf { it.unreadCount }
        if (unread > 0) {
            archiveBadgeView.visibility = View.VISIBLE
            archiveBadgeView.text = unread.toString()
        } else {
            archiveBadgeView.visibility = View.GONE
        }
    }

    private fun refreshChatList() {
        val filtered = if (isSearchActive) {
            val q = searchQuery.trim().lowercase()
            // Privacy rule: Vault chats NEVER appear in search results
            chats.filter { it.location != ChatLocation.VAULT }
                .filter {
                    q.isEmpty() ||
                        it.title.lowercase().contains(q) ||
                        it.lastMessage.lowercase().contains(q)
                }
        } else {
            when (currentMode) {
                ScreenMode.MAIN -> chats.filter {
                    it.location == ChatLocation.MAIN && selectedFolder in it.folders
                }
                ScreenMode.ARCHIVE -> chats.filter { it.location == ChatLocation.ARCHIVE }
                ScreenMode.VAULT -> chats.filter { it.location == ChatLocation.VAULT }
            }
        }

        val sorted = filtered.sortedWith(
            compareByDescending<ChatItem> { it.isPinned }.thenBy { it.id }
        )
        adapter.submitList(sorted)

        if (sorted.isEmpty()) {
            emptyStateView.visibility = View.VISIBLE
            emptyStateView.text = when {
                isSearchActive -> "No results found"
                currentMode == ScreenMode.VAULT -> "Vault is empty.\nHold any chat -> ⋮ -> Hide in Vault."
                currentMode == ScreenMode.ARCHIVE -> "No archived chats"
                else -> "No chats in this folder"
            }
        } else {
            emptyStateView.visibility = View.GONE
        }
    }

    private fun applyTheme(newPalette: TelegramPalette) {
        palette = newPalette

        val barColor = if (currentMode == ScreenMode.VAULT) newPalette.vaultHeader else newPalette.topBar
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !newPalette.isDark
        controller.isAppearanceLightNavigationBars = !newPalette.isDark

        rootFrame.setBackgroundColor(newPalette.surface)
        mainColumn.setBackgroundColor(newPalette.surface)
        topBarContainer.setBackgroundColor(barColor)
        topBarDivider.setBackgroundColor(newPalette.divider)

        storyRings.forEach { ring ->
            ring.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(newPalette.topBar)
            }
        }

        titleView.setTextColor(newPalette.textPrimary)
        selectionCountView.setTextColor(newPalette.textPrimary)
        vaultTitleIcon.imageTintList = ColorStateList.valueOf(newPalette.accent)

        searchPill.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(20f)
            setColor(newPalette.elevated)
        }
        searchPillIcon.imageTintList = ColorStateList.valueOf(newPalette.textSecondary)
        searchInput.setTextColor(newPalette.textPrimary)
        searchInput.setHintTextColor(newPalette.textSecondary)
        searchCancelButton.setTextColor(newPalette.accent)

        val iconRipple = ColorUtils.setAlphaComponent(newPalette.textPrimary, 28)
        listOf(
            backButton,
            lockActionButton,
            themeButton,
            selectionCloseButton,
            selectionMuteButton,
            selectionArchiveButton,
            selectionDeleteButton,
            selectionMoreButton
        ).forEach { btn ->
            btn.imageTintList = ColorStateList.valueOf(
                if (btn == lockActionButton) newPalette.accent else newPalette.textPrimary
            )
            btn.background = RippleDrawable(ColorStateList.valueOf(iconRipple), null, null)
        }

        folderScroll.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(19f)
            setColor(newPalette.folderTrackBg)
        }
        folderIndicator.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(16f)
            setColor(newPalette.folderActivePillBg)
        }

        vaultBanner.setBackgroundColor(newPalette.vaultHeader)
        vaultBannerLabel.setTextColor(newPalette.textSecondary)
        vaultBannerAction.setTextColor(newPalette.accent)
        vaultBannerAction.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(newPalette.accent, 35)),
            null,
            null
        )

        val archiveRipple = ColorUtils.setAlphaComponent(newPalette.textPrimary, 22)
        archiveRow.background = RippleDrawable(
            ColorStateList.valueOf(archiveRipple),
            GradientDrawable().apply { setColor(newPalette.surface) },
            null
        )
        archiveIconBg.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(newPalette.accent)
        }
        archiveIconBg.imageTintList = ColorStateList.valueOf(Color.WHITE)
        archiveTitleView.setTextColor(newPalette.textPrimary)
        archiveSubtitleView.setTextColor(newPalette.textSecondary)
        archiveBadgeView.setTextColor(newPalette.badgeText)
        archiveBadgeView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(11.5f)
            setColor(newPalette.badgeMutedBg)
        }

        emptyStateView.setTextColor(newPalette.textSecondary)
        recyclerView.setBackgroundColor(newPalette.surface)

        cameraFab.imageTintList = ColorStateList.valueOf(newPalette.textPrimary)
        cameraFab.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(newPalette.textPrimary, 28)),
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(newPalette.fabSecondaryBg)
                setStroke(dp(1f), newPalette.glassBorder)
            },
            null
        )

        composeFab.imageTintList = ColorStateList.valueOf(Color.WHITE)
        composeFab.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 60)),
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(newPalette.accentStrong)
            },
            null
        )

        glassBottomBar.applyPalette(newPalette)
        vaultLayout.applyPalette(newPalette)
        adapter.applyPalette(newPalette)
        refreshFolderTabs(animateIndicator = false)
    }
}

package com.example.ioslauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.MotionEvent
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

data class AppEntry(
    val label: String,
    val pkg: String,
    val intent: Intent,
    val icon: Drawable,
    val category: Int,
    val glyph: GlyphKind? = null,
)

class MainActivity : Activity() {

    private val columns = 4
    private val rows = 6
    private val iconDp = 60
    private val dockCount = 4

    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var dockBox: LinearLayout
    private lateinit var homeRoot: LinearLayout
    private lateinit var panel: ControlPanel

    private var topInset = 0
    private var gestureX = 0f
    private var gestureY = 0f
    private var tracking = false

    private var homePages: List<List<AppEntry>> = emptyList()
    private var libraryApps: List<AppEntry> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reload()
        }
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        buildUi()
        reload()

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        panel.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::panel.isInitialized && panel.visible) hidePanel()
        // Кнопка Home: вернуться на первую страницу, как в iOS
        if (intent.action == Intent.ACTION_MAIN && ::pager.isInitialized) {
            pager.setCurrentItem(0, true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::panel.isInitialized && panel.visible) {
            hidePanel()
        } else if (::pager.isInitialized && pager.currentItem != 0) {
            pager.setCurrentItem(0, true)
        }
    }

    // ---------- панель управления ----------

    private fun showPanel() {
        panel.show()
        homeRoot.setRenderEffect(RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP))
    }

    private fun hidePanel() {
        panel.hide()
        homeRoot.setRenderEffect(null)
    }

    /** Свайп вниз из правого верхнего угла открывает панель (как «Пункт управления»). */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!::panel.isInitialized || panel.visible) return super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureX = ev.rawX
                gestureY = ev.rawY
                tracking = ev.rawX > resources.displayMetrics.widthPixels * 0.5f &&
                    ev.rawY > topInset && ev.rawY < topInset + dp(130)
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dy = ev.rawY - gestureY
                val dx = kotlin.math.abs(ev.rawX - gestureX)
                if (dy > dp(60) && dy > 2 * dx) {
                    tracking = false
                    val cancel = MotionEvent.obtain(ev)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    showPanel()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return super.dispatchTouchEvent(ev)
    }

    // ---------- UI ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        pager = ViewPager2(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = updateDots(position)
            })
        }

        dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, dp(24))
        }

        dockBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dpf(36)
                setColor(glass())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(12), dp(4), dp(12), dp(6))
            }
        }

        root.addView(pager)
        root.addView(dots)
        root.addView(dockBox)
        homeRoot = root
        panel = ControlPanel(this) { hidePanel() }
        val frame = FrameLayout(this)
        frame.addView(root)
        frame.addView(panel.view, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topInset = bars.top
            v.setPadding(0, bars.top + dp(8), 0, bars.bottom + dp(8))
            insets
        }
    }

    private fun updateDots(selected: Int) {
        dots.removeAllViews()
        val total = homePages.size + 1
        for (i in 0 until total) {
            val d = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                    setMargins(dp(5), 0, dp(5), 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == selected) Color.WHITE else Color.argb(110, 255, 255, 255))
                }
            }
            dots.addView(d)
        }
    }

    // ---------- data ----------

    private fun reload() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val base = pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { ri ->
                val comp = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                AppEntry(
                    label = ri.loadLabel(pm).toString(),
                    pkg = ri.activityInfo.packageName,
                    intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(comp).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    icon = ri.loadIcon(pm),
                    category = ri.activityInfo.applicationInfo.category,
                )
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }

        // Системные приложения получают собственные нарисованные иконки
        val glyphPkgs = HashMap<String, GlyphKind>()
        fun mark(kind: GlyphKind, i: Intent) {
            resolvePkg(i)?.let { glyphPkgs.putIfAbsent(it, kind) }
        }
        mark(GlyphKind.PHONE, Intent(Intent.ACTION_DIAL))
        mark(GlyphKind.MESSAGES, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))
        mark(GlyphKind.BROWSER, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER))
        mark(GlyphKind.CAMERA, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        mark(GlyphKind.SETTINGS, Intent(Settings.ACTION_SETTINGS))
        val all = base.map { a -> glyphPkgs[a.pkg]?.let { a.copy(glyph = it) } ?: a }

        libraryApps = all

        // Док: телефон, сообщения, браузер, камера
        val dockPkgs = listOfNotNull(
            resolvePkg(Intent(Intent.ACTION_DIAL)),
            resolvePkg(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)),
            resolvePkg(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)),
            resolvePkg(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinct()

        val dockApps = dockPkgs.mapNotNull { p -> all.firstOrNull { it.pkg == p } }
            .toMutableList()
        for (a in all) if (dockApps.size < dockCount && a !in dockApps) dockApps.add(a)

        buildDock(dockApps.take(dockCount))

        val perPage = columns * rows
        homePages = all.filter { it !in dockApps }.chunked(perPage)

        pager.adapter = PagerAdapter()
        updateDots(0)
    }

    private fun resolvePkg(i: Intent): String? {
        val pkg = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return if (pkg == null || pkg == "android") null else pkg
    }

    // ---------- dock ----------

    private fun buildDock(apps: List<AppEntry>) {
        dockBox.removeAllViews()
        for (app in apps) {
            val cell = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val icon = iconView(app, iconDp)
            cell.addView(icon, FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
            bindApp(icon, app)
            dockBox.addView(cell)
        }
    }

    // ---------- icons ----------

    private fun iconView(app: AppEntry, sizeDp: Int): ImageView {
        val size = dp(sizeDp)
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(
                app.glyph?.let { GlyphIcon(it, isDark()) } ?: themedIcon(app.icon, size)
            )
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    // Пропорция «скруглённого квадрата» iOS ≈ 22.37 % от стороны
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.2237f)
                }
            }
            elevation = dpf(1)
        }
    }

    private fun isDark() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Полупрозрачная «стеклянная» подложка: светлая днём, тёмная ночью. */
    private fun glass(): Int =
        if (isDark()) Color.argb(120, 18, 18, 22) else Color.argb(70, 255, 255, 255)

    /**
     * Светлая тема — обычная иконка. Тёмная тема — монохромная иконка Android 13+
     * на тёмной плитке; если у приложения её нет, иконка приглушается.
     */
    private fun themedIcon(icon: Drawable, size: Int): Drawable {
        if (!isDark()) return fullBleed(icon, size)
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome
        if (mono != null) {
            val tinted = mono.mutate().apply { setTint(0xFFE8E8ED.toInt()) }
            val tile = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2E2E31.toInt(), 0xFF161618.toInt())
            )
            val inset = -(size / 4)
            return LayerDrawable(arrayOf(tile, tinted)).apply {
                setLayerInset(1, inset, inset, inset, inset)
            }
        }
        val dim = ColorMatrix().apply { setScale(0.68f, 0.68f, 0.68f, 1f) }
        return fullBleed(icon, size).apply { colorFilter = ColorMatrixColorFilter(dim) }
    }

    /** Адаптивные иконки Android растягиваем на весь квадрат, остальные кладём на белую подложку. */
    private fun fullBleed(icon: Drawable, size: Int): Drawable {
        if (icon is AdaptiveIconDrawable) {
            val bg = icon.background ?: GradientDrawable().apply { setColor(Color.WHITE) }
            val fg = icon.foreground
            val layers = if (fg != null) arrayOf(bg, fg) else arrayOf(bg)
            val inset = -(size / 4) // слои 108dp, видимая область 72dp
            return LayerDrawable(layers).apply {
                for (i in layers.indices) setLayerInset(i, inset, inset, inset, inset)
            }
        }
        return LayerDrawable(
            arrayOf(
                GradientDrawable().apply { setColor(Color.WHITE) },
                icon,
            )
        ).apply {
            val pad = size / 8
            setLayerInset(1, pad, pad, pad, pad)
        }
    }

    // ---------- interactions ----------

    private fun bindApp(v: View, app: AppEntry) {
        v.setOnClickListener { launch(app, v) }
        v.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showMenu(it, app)
            true
        }
    }

    private fun launch(app: AppEntry, from: View) {
        try {
            val opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height)
            startActivity(app.intent, opts.toBundle())
        } catch (_: Exception) {
        }
    }

    private fun showMenu(anchor: View, app: AppEntry) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Открыть")
            menu.add(0, 2, 1, "О приложении")
            menu.add(0, 3, 2, "Удалить приложение")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> launch(app, anchor)
                    2 -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${app.pkg}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    3 -> startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                true
            }
            show()
        }
    }

    // ---------- pager ----------

    private inner class PagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = homePages.size + 1
        override fun getItemViewType(position: Int) = if (position < homePages.size) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
            }
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val host = holder.itemView as FrameLayout
            host.removeAllViews()
            host.addView(
                if (position < homePages.size) homePage(homePages[position]) else libraryPage()
            )
        }
    }

    private fun homePage(apps: List<AppEntry>): View {
        val grid = GridLayout(this).apply {
            columnCount = columns
            rowCount = rows
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setPadding(dp(12), dp(8), dp(12), 0)
        }
        apps.forEachIndexed { i, app ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                setPadding(0, dp(10), 0, 0)
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(i / columns, 1f),
                    GridLayout.spec(i % columns, 1f),
                ).apply { width = 0; height = 0 }
            }
            val icon = iconView(app, iconDp)
            cell.addView(icon, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
            cell.addView(label(app.label))
            bindApp(icon, app)
            grid.addView(cell)
        }
        return grid
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setShadowLayer(4f, 0f, 1f, Color.argb(120, 0, 0, 0))
        setPadding(dp(2), dp(6), dp(2), 0)
    }

    // ---------- App Library ----------

    private fun categoryName(c: Int): String = when (c) {
        ApplicationInfo.CATEGORY_SOCIAL -> "Соцсети"
        ApplicationInfo.CATEGORY_GAME -> "Игры"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Развлечения"
        ApplicationInfo.CATEGORY_IMAGE -> "Фото и видео"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Продуктивность"
        ApplicationInfo.CATEGORY_NEWS -> "Новости"
        ApplicationInfo.CATEGORY_MAPS -> "Путешествия"
        else -> "Утилиты"
    }

    private fun libraryPage(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        // Строка поиска
        column.addView(TextView(this).apply {
            text = "Библиотека приложений"
            setTextColor(Color.argb(200, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                cornerRadius = dpf(12)
                setColor(glass())
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(16) }
        })

        val groups = libraryApps.groupBy { categoryName(it.category) }.toSortedMap()
        val grid = GridLayout(this).apply { columnCount = 2 }
        var idx = 0
        for ((name, apps) in groups) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(idx / 2),
                    GridLayout.spec(idx % 2, 1f),
                ).apply { width = 0; setMargins(dp(6), dp(6), dp(6), dp(10)) }
            }
            val box = GridLayout(this).apply {
                columnCount = 2
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dpf(22)
                    setColor(glass())
                }
            }
            apps.take(4).forEach { app ->
                val ic = iconView(app, 52)
                ic.layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(52); height = dp(52); setMargins(dp(5), dp(5), dp(5), dp(5))
                }
                bindApp(ic, app)
                box.addView(ic)
            }
            card.addView(box)
            card.addView(label(name))
            grid.addView(card)
            idx++
        }
        column.addView(grid)

        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            addView(column)
        }
    }

    // ---------- util ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Int) = v * resources.displayMetrics.density
}

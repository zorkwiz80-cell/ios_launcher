package com.example.ioslauncher

import android.app.Activity
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class PanelKind {
    AIRPLANE, DATA, WIFI, BLUETOOTH, FLASHLIGHT, TIMER, CALC, CAMERA, SETTINGS, SUN, SPEAKER, MOON
}

/** Оригинальные контурные символы для панели управления, нарисованные кодом. */
class PanelGlyph(private val kind: PanelKind, var color: Int = Color.WHITE) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fill() { p.style = Paint.Style.FILL; p.color = color }
    private fun stroke(w: Float = 7f) {
        p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
    }

    private fun rays(c: Canvas, from: Float, to: Float, n: Int) {
        for (i in 0 until n) {
            val a = (i * Math.PI * 2 / n).toFloat()
            c.drawLine(50f + cos(a) * from, 50f + sin(a) * from, 50f + cos(a) * to, 50f + sin(a) * to, p)
        }
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        val s = b.width() / 100f
        c.scale(s, s)
        when (kind) {
            PanelKind.AIRPLANE -> {
                fill()
                val path = Path().apply {
                    moveTo(50f, 10f); lineTo(58f, 40f); lineTo(90f, 58f); lineTo(90f, 67f)
                    lineTo(58f, 57f); lineTo(56f, 77f); lineTo(67f, 85f); lineTo(67f, 91f)
                    lineTo(50f, 87f); lineTo(33f, 91f); lineTo(33f, 85f); lineTo(44f, 77f)
                    lineTo(42f, 57f); lineTo(10f, 67f); lineTo(10f, 58f); lineTo(42f, 40f); close()
                }
                c.drawPath(path, p)
            }
            PanelKind.DATA -> {
                stroke()
                for (r in listOf(18f, 32f)) {
                    c.drawArc(RectF(50f - r, 62f - r, 50f + r, 62f + r), 215f, 110f, false, p)
                }
                fill(); c.drawCircle(50f, 62f, 6f, p)
                stroke(); c.drawLine(50f, 62f, 50f, 84f, p)
            }
            PanelKind.WIFI -> {
                stroke()
                for (r in listOf(16f, 30f, 44f)) {
                    c.drawArc(RectF(50f - r, 74f - r, 50f + r, 74f + r), 225f, 90f, false, p)
                }
                fill(); c.drawCircle(50f, 74f, 5.5f, p)
            }
            PanelKind.BLUETOOTH -> {
                stroke()
                val path = Path().apply {
                    moveTo(30f, 34f); lineTo(68f, 66f); lineTo(50f, 83f); lineTo(50f, 17f)
                    lineTo(68f, 34f); lineTo(30f, 66f)
                }
                c.drawPath(path, p)
            }
            PanelKind.FLASHLIGHT -> {
                fill()
                c.drawPath(Path().apply {
                    moveTo(34f, 12f); lineTo(66f, 12f); lineTo(61f, 34f); lineTo(39f, 34f); close()
                }, p)
                c.drawRoundRect(RectF(39f, 38f, 61f, 88f), 7f, 7f, p)
            }
            PanelKind.TIMER -> {
                stroke()
                c.drawCircle(50f, 57f, 29f, p)
                c.drawLine(50f, 57f, 61f, 44f, p)
                c.drawLine(42f, 14f, 58f, 14f, p)
            }
            PanelKind.CALC -> {
                stroke(6f)
                c.drawRoundRect(RectF(24f, 12f, 76f, 88f), 9f, 9f, p)
                fill()
                c.drawRoundRect(RectF(33f, 21f, 67f, 37f), 3f, 3f, p)
                for (row in 0..2) for (col in 0..2) {
                    c.drawCircle(37f + col * 13f, 50f + row * 13f, 3.8f, p)
                }
            }
            PanelKind.CAMERA -> {
                stroke(6f)
                c.drawRoundRect(RectF(14f, 30f, 86f, 78f), 11f, 11f, p)
                c.drawLine(38f, 22f, 62f, 22f, p)
                c.drawCircle(50f, 54f, 13f, p)
            }
            PanelKind.SETTINGS -> {
                stroke(7f)
                c.drawCircle(50f, 50f, 16f, p)
                stroke(11f)
                rays(c, 28f, 36f, 8)
            }
            PanelKind.SUN -> {
                fill(); c.drawCircle(50f, 50f, 13f, p)
                stroke(6f); rays(c, 25f, 37f, 8)
            }
            PanelKind.SPEAKER -> {
                fill()
                c.drawPath(Path().apply {
                    moveTo(14f, 40f); lineTo(30f, 40f); lineTo(48f, 24f); lineTo(48f, 76f)
                    lineTo(30f, 60f); lineTo(14f, 60f); close()
                }, p)
                stroke(6f)
                for (r in listOf(18f, 32f)) {
                    c.drawArc(RectF(48f - r, 50f - r, 48f + r, 50f + r), -45f, 90f, false, p)
                }
            }
            PanelKind.MOON -> {
                fill()
                val a = Path().apply { addCircle(48f, 52f, 30f, Path.Direction.CW) }
                val cut = Path().apply { addCircle(64f, 40f, 25f, Path.Direction.CW) }
                a.op(cut, Path.Op.DIFFERENCE)
                c.drawPath(a, p)
            }
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Вертикальный ползунок-«таблетка» (яркость, громкость). */
private class VSlider(
    ctx: Context,
    kind: PanelKind,
    private val dark: Boolean,
    var value: Float,
    private val onChange: (Float) -> Unit,
    private val blocked: () -> Boolean,
) : View(ctx) {
    private val glyph = PanelGlyph(kind)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private var swallow = false

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = w * 0.42f
        clip.reset()
        clip.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        paint.style = Paint.Style.FILL
        paint.color = if (dark) Color.argb(110, 40, 40, 48) else Color.argb(85, 255, 255, 255)
        c.drawRect(0f, 0f, w, h, paint)
        paint.color = Color.argb(238, 255, 255, 255)
        c.drawRect(0f, h * (1f - value), w, h, paint)
        c.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, paint)

        val gs = w * 0.5f
        glyph.color = if (value > 0.14f) 0xFF55555B.toInt() else Color.WHITE
        val bottom = h - w * 0.26f
        glyph.setBounds(((w - gs) / 2).toInt(), (bottom - gs).toInt(), ((w + gs) / 2).toInt(), bottom.toInt())
        glyph.draw(c)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) swallow = blocked()
        if (swallow) return true
        parent?.requestDisallowInterceptTouchEvent(true)
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            value = (1f - e.y / height).coerceIn(0f, 1f)
            onChange(value)
            invalidate()
        }
        return true
    }
}

/**
 * Панель управления: выезжает свайпом вниз из правого верхнего угла,
 * фон домашнего экрана размывается (делает MainActivity).
 */
class ControlPanel(private val act: Activity, private val onClose: () -> Unit) {

    val view = FrameLayout(act)
    val visible: Boolean get() = view.visibility == View.VISIBLE

    private val content = FrameLayout(act)
    private val cam = act.getSystemService(CameraManager::class.java)
    private val audio = act.getSystemService(AudioManager::class.java)
    private val nm = act.getSystemService(NotificationManager::class.java)

    private var torchId: String? = null
    private var torchOn = false
    private var flashBtn: Circle? = null

    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                flashBtn?.active = enabled
            }
        }
    }

    init {
        view.visibility = View.GONE
        view.setBackgroundColor(Color.argb(90, 0, 0, 0))
        view.setOnClickListener { onClose() }
        view.addView(content)
        try {
            torchId = cam.cameraIdList.firstOrNull { id ->
                val ch = cam.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            cam.registerTorchCallback(torchCb, null)
        } catch (_: Exception) {
        }
    }

    fun destroy() {
        try { cam.unregisterTorchCallback(torchCb) } catch (_: Exception) {}
    }

    fun show() {
        build()
        view.animate().cancel()
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(180).start()
    }

    fun hide() {
        if (!visible) return
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(150).withEndAction { view.visibility = View.GONE }.start()
    }

    // ---------- helpers ----------

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    private val dark: Boolean
        get() = (act.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun glassFill() =
        if (dark) Color.argb(110, 40, 40, 48) else Color.argb(85, 255, 255, 255)

    private fun tileBg(radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(glassFill())
        setStroke(dp(1), Color.argb(70, 255, 255, 255))
    }

    private fun put(v: View, x: Int, y: Int, w: Int, h: Int) {
        content.addView(v, FrameLayout.LayoutParams(w, h).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = x
            topMargin = y
        })
    }

    private fun go(i: Intent) {
        try { act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        onClose()
    }

    private inner class Circle(
        kind: PanelKind,
        size: Int,
        private val activeColor: Int = 0xFF2F7CF6.toInt(),
    ) : FrameLayout(act) {
        private val glyph = PanelGlyph(kind)
        var active = false
            set(v) { field = v; restyle() }

        init {
            val m = (size * 0.24f).roundToInt()
            addView(ImageView(act).apply { setImageDrawable(glyph) },
                FrameLayout.LayoutParams(-1, -1).apply { setMargins(m, m, m, m) })
            restyle()
        }

        private fun restyle() {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (active) activeColor else glassFill())
                setStroke(dp(1), Color.argb(70, 255, 255, 255))
            }
            glyph.color = if (active && activeColor == Color.WHITE) 0xFF222226.toInt() else Color.WHITE
            glyph.invalidateSelf()
        }
    }

    // ---------- build ----------

    private fun build() {
        content.removeAllViews()
        flashBtn = null

        val screenW = act.resources.displayMetrics.widthPixels
        val margin = dp(24)
        val g = dp(12)
        val u = (screenW - 2 * margin - 3 * g) / 4
        val full = 4 * u + 3 * g
        val blockW = 2 * u + g
        val rowB = blockW + g
        val rowC = rowB + u + g

        view.removeView(content)
        view.addView(content, FrameLayout.LayoutParams(full, rowC + u).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(96)
        })

        // --- связь (2×2) ---
        put(View(act).apply { background = tileBg(dp(30).toFloat()) }, 0, 0, blockW, blockW)
        val cs = (blockW * 0.39f).toInt()
        val gap = (blockW - 2 * cs) / 3
        fun cell(i: Int, j: Int, b: Circle) = put(b, gap + i * (cs + gap), gap + j * (cs + gap), cs, cs)

        val airplaneOn = try {
            Settings.Global.getInt(act.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
        cell(0, 0, Circle(PanelKind.AIRPLANE, cs, 0xFFFF9F0A.toInt()).apply {
            active = airplaneOn
            setOnClickListener { go(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
        })

        val cellular = try {
            val cm = act.getSystemService(ConnectivityManager::class.java)
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (_: Exception) { false }
        cell(1, 0, Circle(PanelKind.DATA, cs, 0xFF30C85E.toInt()).apply {
            active = cellular
            setOnClickListener { go(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
        })

        val wifiOn = try {
            act.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled
        } catch (_: Exception) { false }
        cell(0, 1, Circle(PanelKind.WIFI, cs).apply {
            active = wifiOn
            setOnClickListener { go(Intent(Settings.Panel.ACTION_WIFI)) }
        })

        val btOn = try {
            act.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
        } catch (_: Exception) { false }
        cell(1, 1, Circle(PanelKind.BLUETOOTH, cs).apply {
            active = btOn
            setOnClickListener { go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        })

        // --- ползунки ---
        val bright = try {
            Settings.System.getInt(act.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) { 0.5f }
        put(
            VSlider(act, PanelKind.SUN, dark, bright, { v ->
                try {
                    Settings.System.putInt(
                        act.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    Settings.System.putInt(
                        act.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                        (v * 255).toInt().coerceIn(1, 255)
                    )
                } catch (_: Exception) {
                }
            }, {
                if (!Settings.System.canWrite(act)) {
                    go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${act.packageName}")))
                    true
                } else false
            }),
            2 * (u + g), 0, u, blockW
        )

        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        put(
            VSlider(act, PanelKind.SPEAKER, dark,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat(), { v ->
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0)
                }, { false }),
            3 * (u + g), 0, u, blockW
        )

        // --- Фокус ---
        val focusText = TextView(act).apply {
            text = "Фокус"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
        }
        val focusIcon = PanelGlyph(PanelKind.MOON)
        val focus = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            addView(ImageView(act).apply { setImageDrawable(focusIcon) }, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(focusText, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        }
        fun styleFocus(on: Boolean) {
            focus.background = if (on) GradientDrawable().apply {
                cornerRadius = u / 2f; setColor(Color.argb(238, 255, 255, 255))
            } else tileBg(u / 2f)
            focusText.setTextColor(if (on) 0xFF222226.toInt() else Color.WHITE)
            focusIcon.color = if (on) 0xFF222226.toInt() else Color.WHITE
            focusIcon.invalidateSelf()
        }
        styleFocus(nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            nm.isNotificationPolicyAccessGranted)
        focus.setOnClickListener {
            if (!nm.isNotificationPolicyAccessGranted) {
                go(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                return@setOnClickListener
            }
            val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            styleFocus(!on)
        }
        put(focus, 0, rowB, blockW, u)

        // --- круглые кнопки ---
        val flash = Circle(PanelKind.FLASHLIGHT, u, Color.WHITE).apply {
            active = torchOn
            setOnClickListener {
                val id = torchId ?: return@setOnClickListener
                try { cam.setTorchMode(id, !torchOn) } catch (_: Exception) {}
            }
        }
        flashBtn = flash
        put(flash, 2 * (u + g), rowB, u, u)

        put(Circle(PanelKind.TIMER, u).apply {
            setOnClickListener { go(Intent(AlarmClock.ACTION_SHOW_TIMERS)) }
        }, 3 * (u + g), rowB, u, u)

        put(Circle(PanelKind.CALC, u).apply {
            setOnClickListener {
                go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            }
        }, 0, rowC, u, u)

        put(Circle(PanelKind.CAMERA, u).apply {
            setOnClickListener { go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        }, u + g, rowC, u, u)

        put(Circle(PanelKind.SETTINGS, u).apply {
            setOnClickListener { go(Intent(Settings.ACTION_SETTINGS)) }
        }, 2 * (u + g), rowC, u, u)
    }
}

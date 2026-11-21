package com.example.ioslauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

enum class GlyphKind { PHONE, MESSAGES, BROWSER, CAMERA, SETTINGS }

/**
 * Оригинальные иконки лаунчера, нарисованные кодом (собственные символы).
 * Светлая тема: цветная плитка с белым символом.
 * Тёмная тема: тёмная плитка с цветным символом.
 */
class GlyphIcon(private val kind: GlyphKind, private val dark: Boolean) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class Palette(val top: Int, val bottom: Int, val accent: Int)

    private val palette: Palette = when (kind) {
        GlyphKind.PHONE -> Palette(0xFF5BD97A.toInt(), 0xFF1FA64A.toInt(), 0xFF4ADE70.toInt())
        GlyphKind.MESSAGES -> Palette(0xFF42D3C8.toInt(), 0xFF0E9F9A.toInt(), 0xFF3FD8CC.toInt())
        GlyphKind.BROWSER -> Palette(0xFF5AA9FF.toInt(), 0xFF2A5FE0.toInt(), 0xFF6FB1FF.toInt())
        GlyphKind.CAMERA -> Palette(0xFF8E8E93.toInt(), 0xFF48484A.toInt(), 0xFFD1D1D6.toInt())
        GlyphKind.SETTINGS -> Palette(0xFFB4B4BA.toInt(), 0xFF76767C.toInt(), 0xFFC7C7CC.toInt())
    }

    override fun getIntrinsicWidth() = 512
    override fun getIntrinsicHeight() = 512

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        val s = b.width() / 100f
        canvas.scale(s, s)

        // Плитка (скругление обрезает сам View, здесь заливка на весь квадрат)
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.shader = if (dark) {
            LinearGradient(0f, 0f, 0f, 100f, 0xFF2E2E31.toInt(), 0xFF161618.toInt(), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, 0f, 0f, 100f, palette.top, palette.bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 100f, 100f, paint)
        paint.shader = null

        val glyph = if (dark) palette.accent else Color.WHITE
        val hole = if (dark) 0xFF1E1E20.toInt() else palette.bottom

        when (kind) {
            GlyphKind.PHONE -> drawPhone(canvas, glyph)
            GlyphKind.MESSAGES -> drawMessages(canvas, glyph, hole)
            GlyphKind.BROWSER -> drawBrowser(canvas, glyph)
            GlyphKind.CAMERA -> drawCamera(canvas, glyph, hole)
            GlyphKind.SETTINGS -> drawGear(canvas, glyph, hole)
        }
        canvas.restore()
    }

    private fun drawPhone(c: Canvas, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.strokeWidth = 13f
        paint.strokeCap = Paint.Cap.ROUND
        val p = Path().apply {
            moveTo(33f, 29f)
            cubicTo(22f, 52f, 30f, 72f, 71f, 71f)
        }
        c.drawPath(p, paint)
        paint.style = Paint.Style.FILL
        c.drawRoundRect(RectF(25f, 21f, 43f, 38f), 6f, 6f, paint)
        c.drawRoundRect(RectF(62f, 62f, 79f, 80f), 6f, 6f, paint)
    }

    private fun drawMessages(c: Canvas, color: Int, hole: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawRoundRect(RectF(19f, 24f, 81f, 66f), 14f, 14f, paint)
        val tail = Path().apply {
            moveTo(34f, 62f); lineTo(30f, 80f); lineTo(50f, 64f); close()
        }
        c.drawPath(tail, paint)
        paint.color = hole
        for (i in 0..2) c.drawCircle(35f + i * 15f, 45f, 4.5f, paint)
    }

    private fun drawBrowser(c: Canvas, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        c.drawCircle(50f, 50f, 29f, paint)
        c.drawOval(RectF(36f, 21f, 64f, 79f), paint)
        c.drawLine(21f, 50f, 79f, 50f, paint)
        c.drawLine(26f, 35f, 74f, 35f, paint)
        c.drawLine(26f, 65f, 74f, 65f, paint)
    }

    private fun drawCamera(c: Canvas, color: Int, hole: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawRoundRect(RectF(36f, 24f, 64f, 36f), 5f, 5f, paint)
        c.drawRoundRect(RectF(17f, 31f, 83f, 76f), 11f, 11f, paint)
        paint.color = hole
        c.drawCircle(50f, 54f, 16f, paint)
        paint.color = color
        c.drawCircle(50f, 54f, 11f, paint)
        paint.color = hole
        c.drawCircle(50f, 54f, 5f, paint)
        c.drawCircle(73f, 41f, 2.8f, paint)
    }

    private fun drawGear(c: Canvas, color: Int, hole: Int) {
        val teeth = 8
        val path = Path()
        val rOut = 33f
        val rIn = 25f
        val step = (Math.PI * 2 / teeth).toFloat()
        for (i in 0 until teeth) {
            val a = i * step
            val pts = listOf(
                a - step * 0.22f to rIn, a - step * 0.14f to rOut,
                a + step * 0.14f to rOut, a + step * 0.22f to rIn,
            )
            for ((idx, p) in pts.withIndex()) {
                val x = 50f + p.second * cos(p.first)
                val y = 50f + p.second * sin(p.first)
                if (i == 0 && idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(path, paint)
        paint.color = hole
        c.drawCircle(50f, 50f, 11f, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

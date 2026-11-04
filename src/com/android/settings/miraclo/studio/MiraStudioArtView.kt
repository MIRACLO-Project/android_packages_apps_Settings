/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * The small illustrations on the Mira Studio cards: a sketch of each feature (Quick Settings
 * tiles, a glass lens, the island pill, the volume slider, the split shade, the recents stack)
 * and the thumbnails of the Quick Settings styles. Everything is drawn here, no image assets.
 */
class MiraStudioArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var kind: Int = QS
        set(value) {
            field = value
            invalidate()
        }

    /** Stock thumbnail only: draw the tiles round. */
    var round: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = resources.displayMetrics.density

    private fun dp(v: Float) = v * density

    private fun fill(color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        fill(color)
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, radius, radius, paint)
    }

    private fun dot(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        fill(color)
        c.drawCircle(x, y, radius, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bg = BACKGROUNDS[kind] ?: 0xFF2C3C62.toInt()
        // Nothing is drawn outside the tile.
        canvas.clipRect(0f, 0f, w, h)
        rr(canvas, 0f, 0f, w, h, dp(18f), bg)
        when (kind) {
            QS, BANNER_QS -> drawQs(canvas, w, h)
            GLASS, BANNER_GLASS -> drawGlass(canvas, w, h)
            ISLAND, BANNER_ISLAND -> drawIsland(canvas, w, h)
            VOLUME -> drawVolume(canvas, w, h)
            SHADE -> drawShade(canvas, w, h)
            HOME -> drawHome(canvas, w, h)
            COLORS -> drawColors(canvas, w, h)
            THUMB_COLOROS -> drawThumb(canvas, w, h, WHITE, glass(0.18f), true)
            THUMB_ONEUI -> drawThumb(canvas, w, h, WHITE, 0x400A1440, true)
            THUMB_IOS -> drawThumb(canvas, w, h, 0xFF0A84FF.toInt(), glass(0.18f), true)
            THUMB_STOCK -> drawThumb(canvas, w, h, 0xFFA8C7FA.toInt(), 0xFF33363E.toInt(), round)
        }
    }

    private fun drawQs(c: Canvas, w: Float, h: Float) {
        // Everything is sized from the view's height so the picture always fits its tile (the
        // banner tiles are only 74dp tall).
        val pad = minOf(dp(14f), h * 0.14f)
        val gap = h * 0.07f
        val pill = h * 0.22f
        val half = (w - pad * 2 - gap) / 2
        rr(c, pad, pad, pad + half, pad + pill, pill / 2, WHITE)
        rr(c, pad + half + gap, pad, w - pad, pad + pill, pill / 2, glass(0.22f))
        val cell = (w - pad * 2) / 4
        val rows = h - pad * 2 - pill - gap
        val r = minOf(cell * 0.38f, (rows - gap) / 4)
        val top = pad + pill + gap
        for (row in 0..1) {
            for (col in 0..3) {
                val on = (row == 0 && col == 1) || (row == 1 && col == 2)
                dot(c, pad + cell * col + cell / 2, top + r + row * (r * 2 + gap),
                    r, if (on) WHITE else glass(0.22f))
            }
        }
    }

    /** Colours & style: three overlapping palette circles. */
    private fun drawColors(c: Canvas, w: Float, h: Float) {
        val r = minOf(w, h) * 0.22f
        dot(c, w * 0.38f, h * 0.42f, r, 0xFFE8A0BF.toInt())
        dot(c, w * 0.62f, h * 0.42f, r, 0xFFA8C7FA.toInt())
        dot(c, w * 0.50f, h * 0.64f, r, 0xFFB5E3B0.toInt())
    }

    private fun drawGlass(c: Canvas, w: Float, h: Float) {
        dot(c, w * 0.28f, h * 0.36f, dp(25f), 0xFFF08A5D.toInt())
        dot(c, w * 0.60f, h * 0.62f, dp(20f), 0xFF5DB8F0.toInt())
        rr(c, w * 0.22f, h * 0.25f, w * 0.78f, h * 0.75f, h * 0.25f, glass(0.20f))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = glass(0.75f)
        rect.set(w * 0.22f, h * 0.25f, w * 0.78f, h * 0.75f)
        c.drawRoundRect(rect, h * 0.25f, h * 0.25f, paint)
    }

    private fun drawIsland(c: Canvas, w: Float, h: Float) {
        val ph = dp(30f)
        val l = w * 0.14f
        val r = w * 0.86f
        val t = (h - ph) / 2
        rr(c, l, t, r, t + ph, ph / 2, Color.BLACK)
        rr(c, l + dp(6f), t + dp(5f), l + dp(26f), t + ph - dp(5f), dp(5f), 0xFFE8846A.toInt())
        val heights = floatArrayOf(8f, 14f, 10f, 6f)
        heights.forEachIndexed { i, bh ->
            val x = r - dp(30f) + i * dp(6f)
            rr(c, x, h / 2 - dp(bh) / 2, x + dp(3f), h / 2 + dp(bh) / 2, dp(1.5f), 0xFF8EE6A0.toInt())
        }
    }

    private fun drawVolume(c: Canvas, w: Float, h: Float) {
        val sw = dp(34f)
        val l = w / 2 - sw / 2 - dp(10f)
        val t = dp(12f)
        val b = h - dp(12f)
        rr(c, l, t, l + sw, b, sw / 2, glass(0.18f))
        c.save()
        rect.set(l, t, l + sw, b)
        val clip = android.graphics.Path().apply { addRoundRect(rect, sw / 2, sw / 2, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        fill(WHITE)
        c.drawRect(l, b - (b - t) * 0.62f, l + sw, b, paint)
        c.restore()
        dot(c, l + sw + dp(22f), b - dp(12f), dp(12f), glass(0.18f))
    }

    private fun drawShade(c: Canvas, w: Float, h: Float) {
        val pw = (w - dp(16f) * 2 - dp(8f)) / 2
        val t = dp(12f)
        val b = h - dp(12f)
        val l1 = dp(16f)
        rr(c, l1, t, l1 + pw, b, dp(12f), glass(0.14f))
        for (i in 0..2) {
            val y = t + dp(8f) + i * dp(17f)
            rr(c, l1 + dp(6f), y, l1 + pw - dp(6f), y + dp(12f), dp(4f), glass(0.5f))
        }
        val l2 = l1 + pw + dp(8f)
        rr(c, l2, t, l2 + pw, b, dp(12f), glass(0.14f))
        for (i in 0..3) {
            val cx = l2 + pw * (if (i % 2 == 0) 0.3f else 0.7f)
            val cy = t + dp(16f) + (i / 2) * dp(22f)
            dot(c, cx, cy, dp(8f), if (i == 0 || i == 3) WHITE else glass(0.5f))
        }
    }

    private fun drawHome(c: Canvas, w: Float, h: Float) {
        val cw = dp(52f)
        val ch = dp(78f)
        val t = (h - ch) / 2
        val xs = floatArrayOf(w * 0.18f, w * 0.30f, w * 0.46f)
        val scales = floatArrayOf(0.88f, 0.94f, 1f)
        val colors = intArrayOf(glass(0.25f), glass(0.4f), WHITE)
        for (i in 0..2) {
            val s = scales[i]
            val dx = cw * (1 - s) / 2
            val dy = ch * (1 - s) / 2
            rr(c, xs[i] + dx, t + dy, xs[i] + cw - dx, t + ch - dy, dp(10f), colors[i])
        }
    }

    private fun drawThumb(c: Canvas, w: Float, h: Float, on: Int, off: Int, round: Boolean) {
        val pad = dp(10f)
        val gap = dp(5f)
        val inner = w - pad * 2
        val row1 = dp(16f)
        val r1 = if (round) row1 / 2 else dp(5f)
        val half = (inner - gap) / 2
        rr(c, pad, pad, pad + half, pad + row1, r1, on)
        rr(c, pad + half + gap, pad, w - pad, pad + row1, r1, off)
        val row2 = dp(34f)
        val t2 = pad + row1 + gap
        val unit = (inner - gap * 2) / 4
        rr(c, pad, t2, pad + unit * 2, t2 + row2, dp(10f), off)
        rr(c, pad + unit * 2 + gap, t2, pad + unit * 3 + gap, t2 + row2, dp(10f), off)
        rr(c, pad + unit * 3 + gap * 2, t2, w - pad, t2 + row2, dp(10f), on)
        val t3 = t2 + row2 + gap
        val d = dp(16f)
        val cell = inner / 4
        for (i in 0..3) {
            val cx = pad + cell * i + cell / 2
            if (round) dot(c, cx, t3 + d / 2, d / 2, if (i == 1) on else off)
            else rr(c, cx - d / 2, t3, cx + d / 2, t3 + d, dp(5f), if (i == 1) on else off)
        }
    }

    companion object {
        const val QS = 0
        const val GLASS = 1
        const val ISLAND = 2
        const val VOLUME = 3
        const val SHADE = 4
        const val HOME = 5
        const val BANNER_ISLAND = 6
        const val BANNER_QS = 7
        const val BANNER_GLASS = 8
        const val THUMB_COLOROS = 10
        const val THUMB_ONEUI = 11
        const val THUMB_IOS = 12
        const val THUMB_STOCK = 13
        const val COLORS = 14

        private val WHITE = Color.WHITE

        private fun glass(alpha: Float): Int = Color.argb((alpha * 255).toInt(), 255, 255, 255)

        private val BACKGROUNDS =
            mapOf(
                QS to 0xFF2C3C62.toInt(),
                GLASS to 0xFF3A2F4F.toInt(),
                ISLAND to 0xFF1F3B36.toInt(),
                VOLUME to 0xFF3D3326.toInt(),
                SHADE to 0xFF283546.toInt(),
                HOME to 0xFF3A2A35.toInt(),
                BANNER_ISLAND to 0xFF2C3C62.toInt(),
                BANNER_QS to 0xFF2C3C62.toInt(),
                BANNER_GLASS to 0xFF2C3C62.toInt(),
                THUMB_COLOROS to 0xFF23262E.toInt(),
                THUMB_ONEUI to 0xFF3550A8.toInt(),
                THUMB_IOS to 0xFF2A2A30.toInt(),
                THUMB_STOCK to 0xFF1B1D24.toInt(),
                COLORS to 0xFF2F2A40.toInt(),
            )
    }
}

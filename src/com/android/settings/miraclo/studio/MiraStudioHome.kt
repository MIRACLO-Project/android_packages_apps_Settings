/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.android.settings.R
import java.util.Date
import kotlin.math.min

/** Colours of the Mira Studio pages, dark and light (the Design board's tokens). */
internal class MiraInk(context: Context) {
    val dark =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    val card = if (dark) 0xFF1A1C22.toInt() else 0xFFFFFFFF.toInt()
    val card3 = if (dark) 0xFF22252D.toInt() else 0xFFECEEF4.toInt()
    val fg = if (dark) 0xFFEEF0F5.toInt() else 0xFF15171C.toInt()
    val fg2 = if (dark) 0xFFA3A8B4.toInt() else 0xFF5E6370.toInt()
    val link = if (dark) 0xFF9FB3FF.toInt() else 0xFF3550C8.toInt()
    val acc = if (dark) 0xFF7E99FF.toInt() else 0xFF3A5BD9.toInt()
    val hl = if (dark) 0xFF26304F.toInt() else 0xFFE3E9FF.toInt()
    val div = if (dark) 0x0FFFFFFF else 0x12000000
}

/** One app as the launcher shows it: [full] is the whole icon (to be cut to a shape). */
internal class MiraIcon(val pkg: String, val label: String, val full: Bitmap, val mono: Bitmap?)

/**
 * The real app icons, drawn the way Android's launcher draws them by default: adaptive icons
 * whole (each preview cuts them to its shape, round by default), older icons on a white plate.
 * The everyday apps come first, so previews show what people see on their own home screen.
 */
internal object MiraIcons {
    private val PREFERRED =
        listOf(
            "com.google.android.dialer", "com.android.dialer",
            "com.google.android.apps.messaging", "com.android.messaging",
            "com.android.chrome", "com.google.android.apps.photos",
            "com.google.android.GoogleCamera", "com.android.camera2", "org.codeaurora.snapcam",
            "com.android.vending", "com.google.android.gm", "com.google.android.apps.maps",
            "com.google.android.youtube", "com.google.android.deskclock", "com.android.deskclock",
            "com.google.android.calendar", "com.android.calendar",
            "com.google.android.contacts", "com.android.contacts",
            "com.google.android.documentsui", "com.android.documentsui",
            "com.google.android.calculator", "com.android.calculator2",
            "com.android.gallery3d", "com.android.settings",
        )
    private var cache: List<MiraIcon> = emptyList()
    private var asked = 0
    private var stamp = 0L

    fun load(context: Context, n: Int): List<MiraIcon> {
        val now = System.currentTimeMillis()
        if (n > asked || now - stamp > 30_000L) {
            asked = maxOf(n, 24)
            cache = read(context, asked)
            stamp = now
        }
        return cache.take(n)
    }

    private fun read(context: Context, n: Int): List<MiraIcon> =
        try {
            val la = context.getSystemService(LauncherApps::class.java)
            val dpi = context.resources.displayMetrics.densityDpi
            val all = la!!.getActivityList(null, Process.myUserHandle())
                .distinctBy { it.componentName.packageName }
            val order = { pkg: String -> PREFERRED.indexOf(pkg).let { if (it < 0) PREFERRED.size else it } }
            all.sortedWith(compareBy({ order(it.componentName.packageName) }, { it.label.toString() }))
                .take(n)
                .map {
                    val (full, mono) = render(it.getIcon(dpi), 168)
                    MiraIcon(it.componentName.packageName, it.label.toString(), full, mono)
                }
        } catch (_: Throwable) {
            emptyList()
        }

    /** The icon whole at [size] px, and its one-colour layer for themed icons when it has one. */
    fun render(d: Drawable, size: Int): Pair<Bitmap, Bitmap?> {
        val full = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(full)
        var mono: Bitmap? = null
        if (d is AdaptiveIconDrawable) {
            // The layers are a quarter bigger than the icon on every side; the shape cuts them.
            val e = size / 4
            d.background?.let { it.setBounds(-e, -e, size + e, size + e); it.draw(c) }
            d.foreground?.let { it.setBounds(-e, -e, size + e, size + e); it.draw(c) }
            d.monochrome?.let { m ->
                mono = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
                    m.setBounds(-e, -e, size + e, size + e)
                    m.draw(Canvas(b))
                }
            }
        } else {
            c.drawColor(Color.WHITE)
            val inset = (size * 0.16f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(c)
        }
        return full to mono
    }
}

/** The user's real wallpaper, app icons and the time, for the live previews. */
internal object MiraLive {
    private var home: Bitmap? = null
    private var lock: Bitmap? = null
    private var stamp = 0L

    private fun toBitmap(d: Drawable?, w: Int): Bitmap? {
        d ?: return null
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: return null
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: return null
        val h = (w.toFloat() * ih / iw).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(b))
        return b
    }

    /** Reloads at most every few seconds, so returning to the page shows a new wallpaper. */
    fun refresh(context: Context) {
        val now = System.currentTimeMillis()
        if (now - stamp < 4000 && home != null) return
        stamp = now
        val wm = WallpaperManager.getInstance(context)
        home = try { toBitmap(wm.getDrawable(WallpaperManager.FLAG_SYSTEM), 360) } catch (_: Throwable) { null }
            ?: try { toBitmap(wm.drawable, 360) } catch (_: Throwable) { null }
        lock = try { toBitmap(wm.getDrawable(WallpaperManager.FLAG_LOCK), 360) } catch (_: Throwable) { null }
            ?: home
    }

    fun home(context: Context): Bitmap? { refresh(context); return home }

    fun lock(context: Context): Bitmap? { refresh(context); return lock }

    fun icons(context: Context, n: Int): List<Bitmap> = MiraIcons.load(context, n).map { it.full }

    fun time(context: Context): String =
        DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", Date()).toString()

    fun date(context: Context): String =
        DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEMMMd"), Date())
            .toString()

    fun battery(context: Context): Int =
        try {
            context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Throwable) {
            -1
        }

    fun refreshRate(context: Context): Int =
        try {
            Math.round(context.display?.refreshRate ?: 60f)
        } catch (_: Throwable) {
            60
        }

    /** The refresh rates the panel offers at its current resolution. */
    fun rates(context: Context): List<Int> =
        try {
            val d = context.display!!
            val m = d.mode
            d.supportedModes
                .filter { it.physicalWidth == m.physicalWidth && it.physicalHeight == m.physicalHeight }
                .map { Math.round(it.refreshRate) }
                .distinct()
                .sorted()
        } catch (_: Throwable) {
            listOf(60)
        }

    /** The refresh rate choice in words, as on the Display page. */
    fun refreshMode(context: Context): String {
        val cr = context.contentResolver
        val min = Settings.System.getFloat(cr, "min_refresh_rate", 0f)
        val peak = Settings.System.getFloat(cr, "peak_refresh_rate", 0f)
        return when {
            min.isInfinite() -> "Always the highest rate"
            min > 0f && Math.round(min) == Math.round(peak) -> "Fixed at ${Math.round(min)} Hz"
            else -> "Adaptive"
        }
    }

    /** The current Material You colours, for the Colours & fonts preview. */
    fun palette(context: Context): IntArray =
        intArrayOf(
            context.getColor(android.R.color.system_accent1_400),
            context.getColor(android.R.color.system_accent2_400),
            context.getColor(android.R.color.system_accent3_400),
            context.getColor(android.R.color.system_neutral2_500),
        )
}

/**
 * A live preview drawn for one Mira Studio card or the hero: the real wallpaper, time, icons,
 * battery and refresh rate where the card shows them.
 */
internal class MiraPreviewView(context: Context, val kind: Int) : View(context) {
    private val ink = MiraInk(context)
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("mira-sans", Typeface.NORMAL) }
    private val rect = RectF()
    private val clip = Path()
    private val icons by lazy { MiraLive.icons(context, 20) }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun drawWall(c: Canvas, b: Bitmap?, r: RectF, radius: Float) {
        if (b == null) {
            p.shader =
                LinearGradient(r.left, r.top, r.right, r.bottom,
                    intArrayOf(0xFF2B3A7A.toInt(), 0xFF6A4FB8.toInt(), 0xFFC0789C.toInt()), null,
                    Shader.TileMode.CLAMP)
        } else {
            val s = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val k = maxOf(r.width() / b.width, r.height() / b.height)
            val m = Matrix()
            m.setScale(k, k)
            m.postTranslate(r.left + (r.width() - b.width * k) / 2f, r.top + (r.height() - b.height * k) / 2f)
            s.setLocalMatrix(m)
            p.shader = s
        }
        c.drawRoundRect(r, radius, radius, p)
        p.shader = null
    }

    private fun centreText(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, weight: Int) {
        text.textSize = size
        text.color = color
        text.typeface = Typeface.create(Typeface.create("mira-sans", Typeface.NORMAL), weight, false)
        text.textAlign = Paint.Align.CENTER
        c.drawText(s, x, y, text)
    }

    private fun leftText(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, weight: Int) {
        text.textSize = size
        text.color = color
        text.typeface = Typeface.create(Typeface.create("mira-sans", Typeface.NORMAL), weight, false)
        text.textAlign = Paint.Align.LEFT
        c.drawText(s, x, y, text)
    }

    private fun phone(c: Canvas, r: RectF, lockScreen: Boolean) {
        p.color = if (ink.dark) 0xFF0B0C10.toInt() else 0xFF1A1C22.toInt()
        c.drawRoundRect(r, dp(22f), dp(22f), p)
        val s = RectF(r.left + dp(4f), r.top + dp(4f), r.right - dp(4f), r.bottom - dp(4f))
        drawWall(c, if (lockScreen) MiraLive.lock(context) else MiraLive.home(context), s, dp(18f))
        p.color = Color.BLACK
        c.drawRoundRect(s.centerX() - dp(14f), s.top + dp(6f), s.centerX() + dp(14f), s.top + dp(14f), dp(4f), dp(4f), p)
        // Everything scales with the phone, so small phones (the Settings card) stay tidy.
        val u = s.width() / dp(100f)
        if (lockScreen) {
            centreText(c, MiraLive.date(context), s.centerX(), s.top + dp(30f) * u, dp(7.5f) * u, 0xE6FFFFFF.toInt(), 500)
            centreText(c, MiraLive.time(context), s.centerX(), s.top + dp(62f) * u, dp(30f) * u, Color.WHITE, 700)
            p.color = 0x33FFFFFF
            c.drawRoundRect(s.left + dp(10f) * u, s.bottom - dp(26f) * u, s.right - dp(10f) * u, s.bottom - dp(12f) * u, dp(7f) * u, dp(7f) * u, p)
        } else {
            val gx = s.left + dp(10f) * u
            iconGrid(c, gx, s.top + dp(26f) * u, s.width() - dp(20f) * u, 4, 3)
            // The dock.
            iconGrid(c, gx, s.bottom - dp(40f) * u, s.width() - dp(20f) * u, 4, 1)
        }
    }

    private var iconStart = 0
    private val iconRect = RectF()

    /** Real app icons, round as on Android's home screen; the dock row takes the next apps. */
    private fun iconGrid(c: Canvas, x: Float, y: Float, w: Float, cols: Int, rows: Int) {
        val cell = w / cols
        val size = cell * 0.72f
        for (i in 0 until cols * rows) {
            val ix = x + (i % cols) * cell + (cell - size) / 2f
            val iy = y + (i / cols) * cell
            val icon = icons.getOrNull(iconStart + i)
            iconRect.set(ix, iy, ix + size, iy + size)
            if (icon != null) {
                val sh = BitmapShader(icon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val m = Matrix()
                m.setScale(size / icon.width, size / icon.height)
                m.postTranslate(ix, iy)
                sh.setLocalMatrix(m)
                p.shader = sh
                c.drawOval(iconRect, p)
                p.shader = null
            } else {
                p.color = 0x40FFFFFF
                c.drawOval(iconRect, p)
            }
        }
        iconStart += cols * rows
    }

    override fun onDraw(c: Canvas) {
        iconStart = 0
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        val radius = dp(20f)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        when (kind) {
            HERO, BANNER -> {
                val pw = min(h * 0.5f, w * 0.34f)
                val ph = pw * 2.05f
                val top = (h - ph) / 2f
                if (kind == HERO) {
                    val gap = dp(12f)
                    val l = w / 2f - pw - gap / 2f
                    phone(c, RectF(l, top, l + pw, top + ph), true)
                    phone(c, RectF(w / 2f + gap / 2f, top, w / 2f + gap / 2f + pw, top + ph), false)
                } else {
                    drawLogo(c, w, h)
                }
                return
            }
        }
        c.save()
        c.clipPath(clip)
        when (kind) {
            LOCK -> {
                drawWall(c, MiraLive.lock(context), rect, radius)
                centreText(c, MiraLive.time(context), w / 2f, h / 2f + dp(14f), dp(40f), Color.WHITE, 800)
            }
            HOME -> {
                drawWall(c, MiraLive.home(context), rect, radius)
                iconGrid(c, dp(10f), dp(12f), w - dp(20f), 4, 2)
            }
            WALLPAPER -> drawWall(c, MiraLive.home(context), rect, radius)
            COLOURS -> {
                p.color = ink.card3
                c.drawRect(rect, p)
                centreText(c, "Aa", w * 0.38f, h / 2f + dp(12f), dp(32f), ink.fg, 500)
                val pal = MiraLive.palette(context)
                val r = dp(8f)
                for (i in 0 until 4) {
                    p.color = pal[i]
                    c.drawCircle(w * 0.64f + (i % 2) * r * 2.6f, h / 2f - r * 1.3f + (i / 2) * r * 2.6f, r, p)
                }
            }
            STATUS_BAR -> {
                drawWall(c, MiraLive.home(context), rect, radius)
                p.color = 0x33000000
                c.drawRect(0f, 0f, w, dp(30f), p)
                leftText(c, MiraLive.time(context), dp(14f), dp(21f), dp(13f), Color.WHITE, 600)
                p.color = Color.WHITE
                c.drawRoundRect(w - dp(38f), dp(11f), w - dp(14f), dp(21f), dp(3f), dp(3f), p)
                for (i in 0 until 4) {
                    val bx = w - dp(70f) + i * dp(5f)
                    c.drawRoundRect(bx, dp(21f) - dp(3f + i * 2.2f), bx + dp(3f), dp(21f), dp(1.2f), dp(1.2f), p)
                }
            }
            ISLAND -> {
                p.color = Color.BLACK
                c.drawRect(rect, p)
                val pw = w * 0.66f
                val pr = RectF((w - pw) / 2f, h / 2f - dp(15f), (w + pw) / 2f, h / 2f + dp(15f))
                p.color = 0xFF1C1D22.toInt()
                c.drawRoundRect(pr, dp(15f), dp(15f), p)
                p.shader = LinearGradient(pr.left, 0f, pr.left + dp(22f), 0f,
                    0xFFFF8A4C.toInt(), 0xFFC27BFF.toInt(), Shader.TileMode.CLAMP)
                c.drawRoundRect(pr.left + dp(6f), pr.top + dp(5f), pr.left + dp(26f), pr.bottom - dp(5f), dp(6f), dp(6f), p)
                p.shader = null
                p.color = 0x66FFFFFF
                c.drawRoundRect(pr.left + dp(34f), pr.centerY() - dp(2f), pr.right - dp(34f), pr.centerY() + dp(2f), dp(2f), dp(2f), p)
                p.color = 0xFFFF9F6A.toInt()
                for (i in 0 until 4) {
                    val bh = dp(floatArrayOf(5f, 10f, 7f, 12f)[i])
                    val bx = pr.right - dp(26f) + i * dp(4.5f)
                    c.drawRoundRect(bx, pr.centerY() - bh / 2f, bx + dp(2.6f), pr.centerY() + bh / 2f, dp(1.3f), dp(1.3f), p)
                }
            }
            DISPLAY -> {
                p.color = ink.card3
                c.drawRect(rect, p)
                val now = MiraLive.refreshRate(context)
                val big = min(dp(40f), h * 0.36f)
                val base = h / 2f + big * 0.18f
                leftText(c, now.toString(), dp(16f), base, big, ink.fg, 700)
                text.textSize = big
                val nw = text.measureText(now.toString())
                leftText(c, "Hz", dp(20f) + nw, base, big * 0.36f, ink.fg2, 500)
                leftText(c, MiraLive.refreshMode(context), dp(16f), base + big * 0.62f, min(dp(12f), h * 0.11f), ink.fg2, 400)
                // The panel's rates as bars, the one in use lit.
                val rates = MiraLive.rates(context)
                val bw = min(dp(22f), w * 0.07f)
                val gap = bw * 0.6f
                var bx = w - dp(16f) - rates.size * bw - (rates.size - 1) * gap
                val top = rates.maxOrNull()?.toFloat() ?: 120f
                for (r in rates) {
                    val bh = (h - dp(44f)) * (r / top)
                    p.color = if (r == now) ink.acc else ink.hl
                    c.drawRoundRect(bx, h - dp(26f) - bh, bx + bw, h - dp(26f), bw / 2f, bw / 2f, p)
                    centreText(c, r.toString(), bx + bw / 2f, h - dp(10f), min(dp(10f), h * 0.09f), ink.fg2, 500)
                    bx += bw + gap
                }
            }
            BATTERY -> {
                p.color = ink.card3
                c.drawRect(rect, p)
                val level = MiraLive.battery(context).coerceIn(0, 100)
                val cx = dp(46f)
                val cy = h / 2f
                val r = dp(24f)
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(6f)
                p.strokeCap = Paint.Cap.ROUND
                p.color = ink.hl
                c.drawCircle(cx, cy, r, p)
                p.color = 0xFF34C759.toInt()
                c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * level / 100f, false, p)
                p.style = Paint.Style.FILL
                centreText(c, "$level%", cx, cy + dp(4.5f), dp(12f), ink.fg, 700)
            }
            ANIMATIONS -> {
                p.color = ink.card3
                c.drawRect(rect, p)
                val path = Path()
                path.moveTo(dp(14f), h - dp(20f))
                path.cubicTo(w * 0.45f, h - dp(20f), w * 0.45f, dp(22f), w - dp(14f), dp(22f))
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(2f)
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
                p.color = ink.acc
                c.drawPath(path, p)
                p.pathEffect = null
                p.style = Paint.Style.FILL
                c.drawCircle(dp(14f), h - dp(20f), dp(5f), p)
                c.drawCircle(w - dp(14f), dp(22f), dp(7f), p)
            }
        }
        c.restore()
    }

    // The MIRACLO mark on the Settings card: the O, with the small blue light circling it.
    private var spin: android.animation.ValueAnimator? = null
    private var angle = -40f
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (kind == BANNER) {
            spin = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 9000
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    angle = -40f + it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        spin?.cancel()
        spin = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) spin?.resume() else spin?.pause()
    }

    private fun drawLogo(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.34f
        // a soft halo behind the ring
        glow.shader = android.graphics.RadialGradient(cx, cy, r * 1.6f,
            intArrayOf(0x40FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.6f, glow)
        glow.shader = null
        // the O
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.16f
        p.color = Color.WHITE
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        // the light on it
        val a = Math.toRadians(angle.toDouble())
        val dx = cx + (r * kotlin.math.cos(a)).toFloat()
        val dy = cy + (r * kotlin.math.sin(a)).toFloat()
        val dr = r * 0.17f
        glow.shader = android.graphics.RadialGradient(dx, dy, dr * 3.2f,
            intArrayOf(0xCC6FA0FF.toInt(), 0x006FA0FF), null, Shader.TileMode.CLAMP)
        c.drawCircle(dx, dy, dr * 3.2f, glow)
        glow.shader = null
        p.color = 0xFF4F86FF.toInt()
        c.drawCircle(dx, dy, dr, p)
        p.color = Color.WHITE
        c.drawCircle(dx, dy, dr * 0.42f, p)
    }

    companion object {
        const val HERO = 100
        const val BANNER = 101
        const val LOCK = 102
        const val HOME = 103
        const val WALLPAPER = 104
        const val COLOURS = 105
        const val STATUS_BAR = 106
        const val ISLAND = 107
        const val DISPLAY = 108
        const val BATTERY = 109
        const val ANIMATIONS = 110
    }
}

/** Builds the Mira Studio home as the approved design: search, hero, sections of cards, rows. */
internal object MiraStudioHomeBuilder {
    class Item(
        val preview: Int,
        @StringRes val title: Int,
        @StringRes val summary: Int,
        val fragment: String,
        /** For cards whose preview is one of [MiraStudioArtView]'s drawings. */
        val art: Int = -1,
    )

    fun build(context: Context, root: LinearLayout, sections: List<Pair<Int, List<Item>>>, rows: Pair<Int, List<Item>>?, update: Item?) {
        val ink = MiraInk(context)
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        fun bg(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = r * dp }
        fun ripple(content: Drawable) =
            android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ink.div or 0x10000000), content, null)
        val sans = Typeface.create("mira-sans", Typeface.NORMAL)
        fun tv(s: CharSequence, size: Float, color: Int, weight: Int = 400) =
            TextView(context).apply {
                text = s
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                typeface = Typeface.create(sans, weight, false)
            }
        root.removeAllViews()

        // Search: right here, over Mira Studio only. Typing swaps the page for the matches;
        // clearing it brings the page back as it was.
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val results = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val field =
            EditText(context).apply {
                hint = context.getString(R.string.miraclo_search_studio)
                setHintTextColor(ink.fg2)
                setTextColor(ink.fg)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = sans
                background = null
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setPadding(px(12), 0, px(8), 0)
            }
        val clear =
            tv("✕", 15f, ink.fg2).apply {
                gravity = Gravity.CENTER
                visibility = View.GONE
                contentDescription = context.getString(android.R.string.cancel)
                setOnClickListener {
                    field.setText("")
                    field.clearFocus()
                    context.getSystemService(InputMethodManager::class.java)
                        ?.hideSoftInputFromWindow(field.windowToken, 0)
                }
            }
        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = bg(ink.card, 24)
                setPadding(px(16), 0, px(6), 0)
                addView(ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_menu_search)
                    setColorFilter(ink.fg2)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(px(20), px(20)))
                addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                addView(clear, LinearLayout.LayoutParams(px(40), px(40)))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48)).apply { topMargin = px(4) },
        )
        root.addView(results, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun showResults(q: String) {
            results.removeAllViews()
            val hits = MiraStudioSearch.find(context, q)
            if (hits.isEmpty()) {
                results.addView(tv(context.getString(R.string.miraclo_search_none), 14f, ink.fg2).apply {
                    gravity = Gravity.CENTER
                    setPadding(px(16), px(28), px(16), px(28))
                })
                return
            }
            val group = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = bg(ink.card, 26)
                setPadding(0, px(6), 0, px(6))
            }
            hits.forEach { e ->
                group.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ripple(bg(0, 20))
                    setPadding(px(18), px(12), px(18), px(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        context.getSystemService(InputMethodManager::class.java)
                            ?.hideSoftInputFromWindow(field.windowToken, 0)
                        context.openStudioPage(e.fragment, e.page)
                    }
                    addView(tv(e.name, 15f, ink.fg, 600))
                    val where = context.getString(e.page)
                    addView(tv(if (e.name == where) e.summary else "$where · ${e.summary}", 12f, ink.fg2).apply {
                        setPadding(0, px(2), 0, 0)
                    })
                })
            }
            results.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12); bottomMargin = px(12) })
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) {
                val q = e?.toString().orEmpty().trim()
                clear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                if (q.isEmpty()) {
                    results.visibility = View.GONE
                    content.visibility = View.VISIBLE
                } else {
                    showResults(q)
                    results.visibility = View.VISIBLE
                    content.visibility = View.GONE
                }
            }
        })
        field.setOnEditorActionListener { _, _, _ ->
            context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(field.windowToken, 0)
            true
        }

        // Hero: the real lock and home screens, and the way to change them.
        content.addView(
            FrameLayout(context).apply {
                background = bg(ink.card, 28)
                addView(MiraPreviewView(context, MiraPreviewView.HERO),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(196)).apply { topMargin = px(10) })
                addView(tv(context.getString(R.string.miraclo_customise_screens), 13f, ink.fg, 600).apply {
                    gravity = Gravity.CENTER
                    background = ripple(bg(ink.card3, 16))
                    setPadding(px(16), px(8), px(16), px(8))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { MiraWallpaperFx.openPicker(context) }
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = px(12) })
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(260)).apply { topMargin = px(12) },
        )

        fun header(@StringRes t: Int) {
            content.addView(tv(context.getString(t), 13f, ink.link, 600).apply {
                setPadding(px(6), px(22), px(6), px(10))
                isAccessibilityHeading = true
            })
        }

        for ((title, items) in sections) {
            header(title)
            for (pair in items.chunked(2)) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEachIndexed { i, item ->
                    val card =
                        LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            background = ripple(bg(ink.card, 26))
                            isClickable = true
                            isFocusable = true
                            contentDescription = context.getString(item.title)
                            setOnClickListener { context.openStudioPage(item.fragment, item.title) }
                        }
                    val pv: View =
                        if (item.art >= 0) MiraStudioArtView(context).apply { kind = item.art }
                        else MiraPreviewView(context, item.preview)
                    card.addView(pv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(96)).apply {
                        setMargins(px(6), px(6), px(6), 0)
                    })
                    val cap = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(px(14), px(10), px(14), px(13))
                    }
                    cap.addView(tv(context.getString(item.title), 15f, ink.fg, 600).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    cap.addView(tv(context.getString(item.summary), 12f, ink.fg2).apply {
                        setPadding(0, px(2), 0, 0)
                    })
                    card.addView(cap)
                    if (i == 1) row.addView(View(context), LinearLayout.LayoutParams(px(12), 1))
                    row.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                if (pair.size == 1) {
                    row.addView(View(context), LinearLayout.LayoutParams(px(12), 1))
                    row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                }
                content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(12) })
            }
        }

        // Rows (Privacy & tools) and the Software update banner.
        if (rows != null) {
            header(rows.first)
            val group = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = bg(ink.card, 26)
            }
            rows.second.forEach { item ->
                group.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = ripple(bg(0, 26))
                    setPadding(px(16), px(12), px(16), px(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { context.openStudioPage(item.fragment, item.title) }
                    addView(MiraStudioArtView(context).apply { kind = MiraStudioArtView.SHADE },
                        LinearLayout.LayoutParams(px(38), px(38)))
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(px(14), 0, px(8), 0)
                        addView(tv(context.getString(item.title), 15f, ink.fg, 600))
                        addView(tv(context.getString(item.summary), 12f, ink.fg2))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(tv("›", 20f, ink.fg2))
                })
            }
            content.addView(group)
        }
        if (update != null) {
            content.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ripple(GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(if (ink.dark) 0xFF2A2F5A.toInt() else 0xFFE3E9FF.toInt(),
                        if (ink.dark) 0xFF3A2E5E.toInt() else 0xFFEDE3FF.toInt())).apply { cornerRadius = 26 * dp })
                setPadding(px(16), px(16), px(16), px(16))
                isClickable = true
                isFocusable = true
                setOnClickListener { context.openStudioPage(update.fragment, update.title) }
                addView(TextView(context).apply {
                    text = "↓"
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        intArrayOf(0xFF7E99FF.toInt(), 0xFFB07BFF.toInt())).apply { cornerRadius = 12 * dp }
                }, LinearLayout.LayoutParams(px(40), px(40)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(px(14), 0, px(8), 0)
                    addView(tv(context.getString(update.title), 15f, ink.fg, 600))
                    addView(tv(context.getString(update.summary), 12f, ink.fg2))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(tv("›", 20f, ink.fg2))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(16); bottomMargin = px(8) })
        }
    }
}

/**
 * Search over Mira Studio only: every page and the things on it, with the words people use for
 * them. Forgiving: word starts, parts of words and small typos all match ("refrsh", "ilsand").
 */
internal object MiraStudioSearch {
    class Entry(
        val name: String,
        val summary: String,
        @StringRes val page: Int,
        val fragment: String,
        val words: String,
    )

    private fun entries(context: Context): List<Entry> {
        fun e(name: String, summary: String, page: Int, fragment: String, words: String) =
            Entry(name, summary, page, fragment, words)
        fun s(id: Int) = context.getString(id)
        val clocks = MiraStudioLockClocksFragment::class.java.name
        val home = MiraStudioHomeFragment::class.java.name
        val launcher = MiraStudioLauncherFragment::class.java.name
        val search = MiraStudioHomeSearchFragment::class.java.name
        val wall = MiraStudioWallpaperFragment::class.java.name
        val colours = MiraStudioColorsFragment::class.java.name
        val fonts = MiraStudioFontsFragment::class.java.name
        val icons = MiraStudioIconsFragment::class.java.name
        val speed = MiraStudioNetSpeedFragment::class.java.name
        val island = com.android.settings.notification.MiraCLOIslandSettings::class.java.name
        val qs = MiraStudioQsFragment::class.java.name
        val shade = MiraStudioShadeFragment::class.java.name
        val volume = MiraStudioVolumeFragment::class.java.name
        val power = MiraStudioPowerFragment::class.java.name
        val glass = com.android.settings.display.MiraCLOLiquidGlassSettings::class.java.name
        val motion = MiraStudioMotionFragment::class.java.name
        val sounds = MiraStudioSoundsFragment::class.java.name
        val display = MiraStudioDisplayTuningFragment::class.java.name
        val battery = MiraStudioBatteryTuningFragment::class.java.name
        val privacy = MiraStudioPrivacyFragment::class.java.name
        val update = MiraStudioUpdateFragment::class.java.name
        val L = R.string.miraclo_card_lock
        val H = R.string.miraclo_card_home
        return listOf(
            e(s(L), s(R.string.miraclo_lock_clocks_sum), L, clocks,
                "lock screen clock clocks time digits face style font weight size colour color aod always on display outline coloros ios iphone one ui samsung nothing hyperos miui analog custom adjustable"),
            e("Clock style", "ColorOS, iOS, One UI, Nothing, HyperOS, analog, outline or your own", L, clocks,
                "clock style face lock screen coloros ios one ui nothing hyperos analog outline custom"),
            e("Clock weight and size", "Make the lock screen clock bolder, thinner, bigger or smaller", L, clocks,
                "clock weight size bold thin light big small thickness font"),
            e(s(H), s(R.string.miraclo_studio_home_summary), H, home,
                "home screen icons icon shape circle round squircle grid columns rows layout themed icons monochrome icon size app drawer"),
            e("Icon shape", "Round, square, cookie and more, for the home screen and app drawer", H, home,
                "icon shape circle round square squircle cookie arch"),
            e("Themed icons", "Icons in your wallpaper colours", H, home, "themed icons monochrome material you colour"),
            e("App grid", "Columns and rows on the home screen", H, home, "grid columns rows layout 4x5 5x5 6x6"),
            e("Icon pack", "Android's round icons, Mira icons or an installed pack", H, home, "icon pack mira icons android stock round adw nova"),
            e(s(R.string.miraclo_studio_launcher_title), s(R.string.miraclo_studio_launcher_summary), R.string.miraclo_studio_launcher_title, launcher,
                "launcher labels names notification dots badges page transition cube depth fade slide gestures double tap swipe pinch hidden apps hide lock layout folder blur"),
            e("Home screen gestures", "Double-tap, swipe down, swipe up and pinch", R.string.miraclo_studio_launcher_title, launcher,
                "gestures double tap sleep lock screen off swipe down notifications swipe up app drawer pinch"),
            e("Hidden apps", "Keep apps out of the app drawer and search", R.string.miraclo_studio_launcher_title, launcher,
                "hidden apps hide app drawer"),
            e("Page transition", "Slide, cube, depth or fade between home screen pages", R.string.miraclo_studio_launcher_title, launcher,
                "page transition animation cube depth fade slide"),
            e(s(R.string.miraclo_studio_home_search_title), "Search on the home screen and which app it uses", R.string.miraclo_studio_home_search_title, search,
                "search bar pill page indicator dots google chrome browser brave firefox gemini chatgpt assistant web engine"),
            e(s(R.string.miraclo_style_wallpaper), s(R.string.miraclo_style_wallpaper_sum), R.string.miraclo_style_wallpaper, wall,
                "wallpaper background picture image photo blur dim depth effects tone"),
            e(s(R.string.miraclo_card_colours_fonts), s(R.string.miraclo_card_colours_fonts_sum), R.string.miraclo_card_colours_fonts, colours,
                "colour color colours colors palette theme material you accent dark mode light mode contrast"),
            e(s(R.string.miraclo_style_fonts), s(R.string.miraclo_style_fonts_sum), R.string.miraclo_style_fonts, fonts,
                "font fonts typeface text size bold mira sans"),
            e(s(R.string.miraclo_card_statusbar), s(R.string.miraclo_card_statusbar_sum), R.string.miraclo_card_statusbar, icons,
                "status bar icons wifi wi-fi signal mobile battery percentage clock seconds carrier icon style"),
            e(s(R.string.miraclo_speed_title), "Upload and download speed in the status bar", R.string.miraclo_speed_title, speed,
                "network speed internet speed data speed meter indicator kb/s mb/s traffic"),
            e(s(R.string.miraclo_island_title), s(R.string.miraclo_studio_island_summary), R.string.miraclo_island_title, island,
                "dynamic island pill music media charging calls timer notifications pop up popup length height size"),
            e(s(R.string.miraclo_studio_qs_title), s(R.string.miraclo_studio_qs_summary), R.string.miraclo_studio_qs_title, qs,
                "quick settings qs control centre center panel tiles toggles coloros ios one ui stock edit brightness media"),
            e(s(R.string.miraclo_studio_shade_title), s(R.string.miraclo_studio_shade_summary), R.string.miraclo_studio_shade_title, shade,
                "notification shade notifications pull down split dual shade cards"),
            e(s(R.string.miraclo_studio_volume_title), s(R.string.miraclo_studio_volume_summary), R.string.miraclo_studio_volume_title, volume,
                "volume panel slider sound side left right ringer media"),
            e(s(R.string.miraclo_liquid_glass_title), s(R.string.miraclo_studio_glass_summary), R.string.miraclo_liquid_glass_title, glass,
                "liquid glass blur transparency transparent refraction glow translucent"),
            e(s(R.string.miraclo_style_motion), s(R.string.miraclo_style_motion_sum), R.string.miraclo_style_motion, motion,
                "animations animation motion speed spring transitions smooth"),
            e(s(R.string.miraclo_sounds_title), s(R.string.miraclo_sounds_card_sum), R.string.miraclo_sounds_title, sounds,
                "sounds sound haptics vibration ringtone notification sound alarm charging sound lock sound keyboard touch"),
            e(s(R.string.miraclo_tuning_display), s(R.string.miraclo_tuning_display_sum), R.string.miraclo_tuning_display, display,
                "display screen refresh rate hz 60 90 120 adaptive force smooth fps colour profile vivid natural saturated counter"),
            e("Refresh rate", "Adaptive, 60, 90 or 120 Hz, or always the highest", R.string.miraclo_tuning_display, display,
                "refresh rate hz 60hz 90hz 120hz adaptive force peak smooth fps"),
            e(s(R.string.miraclo_card_battery), s(R.string.miraclo_tuning_battery_sum), R.string.miraclo_card_battery, battery,
                "battery health cycles capacity charging ring animation saver percentage"),
            e(s(R.string.miraclo_card_privacy), s(R.string.miraclo_card_privacy_sum), R.string.miraclo_card_privacy, privacy,
                "privacy app lock hide permissions clipboard camera microphone indicators"),
            e(s(R.string.miraclo_card_update), s(R.string.miraclo_card_update_sum), R.string.miraclo_card_update, update,
                "software update ota updater version download install system update upgrade"),
        )
    }

    private fun norm(s: String): List<String> =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(" ").filter { it.isNotEmpty() }

    /** Edit distance, stopping early once it is over [max]. */
    private fun distance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var best = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                best = minOf(best, cur[j])
            }
            if (best > max) return max + 1
            prev = cur
        }
        return prev[b.length]
    }

    /** How well one typed word fits one word of an entry; 0 = not at all. */
    private fun fit(q: String, w: String): Float {
        if (w == q) return 4f
        if (w.startsWith(q)) return 3f
        if (q.length >= 3 && w.contains(q)) return 2f
        if (q.length >= 4) {
            val max = if (q.length >= 7) 2 else 1
            // A typo in the whole word, or in the start of a longer word.
            if (distance(q, w, max) <= max) return 1.5f
            if (w.length > q.length && distance(q, w.substring(0, q.length), max) <= max) return 1.2f
        }
        return 0f
    }

    fun find(context: Context, query: String): List<Entry> {
        val qs = norm(query)
        if (qs.isEmpty()) return emptyList()
        return entries(context)
            .mapNotNull { e ->
                val title = norm(e.name)
                val rest = norm(e.summary + " " + e.words)
                var score = 0f
                for (q in qs) {
                    val t = title.maxOfOrNull { fit(q, it) } ?: 0f
                    val r = rest.maxOfOrNull { fit(q, it) } ?: 0f
                    val best = maxOf(t * 2f, r)
                    if (best <= 0f) return@mapNotNull null
                    score += best
                }
                e to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.name }
            .take(20)
    }
}

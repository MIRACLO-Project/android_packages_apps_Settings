/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.spa.preference.ComposePreference
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mira Studio › Style › Wallpaper: opens Wallpaper & style to pick a wallpaper (home, lock or
 * both), and adds effects on top of the picture wallpaper: blur, dim, tone and vignette, for the
 * home screen, the lock screen or both.
 *
 * Effects never stack: the untouched picture is kept (files/miraclo_wallpaper_<home|lock>.png)
 * together with the wallpaper id it produced, so changing the effect later starts again from
 * the original. Picking a new wallpaper in Wallpaper & style replaces the kept original.
 */
class MiraStudioWallpaperPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { Page() }
    }

    @Composable
    private fun Page() {
        val ctx = LocalContext.current
        val cs = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        var target by remember { mutableStateOf(MiraWallpaperFx.TARGET_BOTH) }
        var blur by remember { mutableFloatStateOf(MiraWallpaperFx.getInt(ctx, "blur", 0).toFloat()) }
        var dim by remember { mutableFloatStateOf(MiraWallpaperFx.getInt(ctx, "dim", 0).toFloat()) }
        var tone by remember { mutableStateOf(MiraWallpaperFx.TONES[MiraWallpaperFx.getInt(ctx, "tone", 0).coerceIn(0, 3)]) }
        var vignette by remember { mutableStateOf(MiraWallpaperFx.getInt(ctx, "vignette", 0) != 0) }
        var source by remember { mutableStateOf<Bitmap?>(null) }
        var preview by remember { mutableStateOf<Bitmap?>(null) }
        var busy by remember { mutableStateOf(false) }
        var reload by remember { mutableStateOf(0) }

        // The untouched picture for the chosen screen, scaled down for the live preview.
        LaunchedEffect(target, reload) {
            source = withContext(Dispatchers.IO) {
                MiraWallpaperFx.original(ctx, if (target == MiraWallpaperFx.TARGET_LOCK) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM)
                    ?.let { MiraWallpaperFx.fit(it, 360) }
            }
        }
        LaunchedEffect(source, blur, dim, tone, vignette) {
            delay(60)
            val src = source ?: run { preview = null; return@LaunchedEffect }
            preview = withContext(Dispatchers.Default) {
                MiraWallpaperFx.apply(ctx, src, blur.roundToInt(), dim.roundToInt(), tone, vignette)
            }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Preview, phone shaped.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.width(180.dp)
                        .aspectRatio(9f / 20f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(cs.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val p = preview
                    if (p != null) {
                        Image(p.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(
                            "Effects work on picture wallpapers",
                            color = cs.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            StudioCard {
                Column {
                    StudioNavRow("Change wallpaper", "Wallpaper & style: home screen, lock screen, colours") {
                        MiraWallpaperFx.openPicker(ctx)
                    }
                    StudioNavRow("Reload", "Use the wallpaper you have set now") { reload++ }
                }
            }

            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
                    Text("Apply to", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                    StudioSegmented(
                        listOf(
                            MiraWallpaperFx.TARGET_HOME to "Home",
                            MiraWallpaperFx.TARGET_LOCK to "Lock",
                            MiraWallpaperFx.TARGET_BOTH to "Both",
                        ),
                        target,
                    ) { target = it }
                }
            }

            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                    FxSlider("Blur", "${blur.roundToInt()}", blur, 0f..25f, 24) { blur = it }
                    FxSlider("Dim", "${dim.roundToInt()}%", dim, 0f..60f, 11) { dim = it }
                    Text("Tone", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
                    StudioSegmented(
                        listOf("none" to "None", "mono" to "Mono", "duo" to "Duotone", "warm" to "Warm"),
                        tone,
                    ) { tone = it }
                    Box(Modifier.height(6.dp))
                }
            }

            StudioCard {
                StudioSwitchRow("Vignette", "Darker edges, so icons and text stand out", vignette) { vignette = it }
            }

            // Apply.
            Box(
                Modifier.fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (busy || source == null) cs.surfaceContainerHighest else cs.primary)
                    .clickable(enabled = !busy && source != null) {
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.Default) {
                                MiraWallpaperFx.applyToWallpaper(ctx, target, blur.roundToInt(), dim.roundToInt(), tone, vignette)
                            }
                            busy = false
                            Toast.makeText(ctx, if (ok) "Wallpaper updated" else "Couldn't change the wallpaper", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) "Applying…" else "Apply",
                    color = if (busy || source == null) cs.onSurfaceVariant else cs.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Remove effects: set Blur and Dim to 0, Tone to None, then Apply.",
                color = cs.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    @Composable
    private fun FxSlider(
        title: String,
        value: String,
        v: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        onChange: (Float) -> Unit,
    ) {
        val cs = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(value, color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = v, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

/** Wallpaper effects: the image work, kept apart from the UI. */
internal object MiraWallpaperFx {
    private const val TAG = "MiraWallpaperFx"
    const val TARGET_HOME = "home"
    const val TARGET_LOCK = "lock"
    const val TARGET_BOTH = "both"
    val TONES = listOf("none", "mono", "duo", "warm")
    private const val KEY_PREFIX = "miraclo_wallpaper_fx_"

    fun getInt(ctx: Context, key: String, def: Int) =
        Settings.Secure.getInt(ctx.contentResolver, KEY_PREFIX + key, def)

    private fun putInt(ctx: Context, key: String, v: Int) {
        Settings.Secure.putInt(ctx.contentResolver, KEY_PREFIX + key, v)
    }

    fun openPicker(ctx: Context) {
        val picker =
            Intent().setComponent(
                ComponentName("com.android.wallpaper", "com.android.customization.picker.CustomizationPickerActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(picker) }.recoverCatching { ctx.startActivity(fallback) }
            .onFailure { Log.w(TAG, "no wallpaper picker", it) }
    }

    private fun keptFile(ctx: Context, which: Int) =
        File(ctx.filesDir, if (which == WallpaperManager.FLAG_LOCK) "miraclo_wallpaper_lock.png" else "miraclo_wallpaper_home.png")

    /**
     * The untouched picture for [which]: the kept original when the current wallpaper is the one
     * our effects produced, otherwise the wallpaper as it is now (a new pick replaces the original).
     */
    fun original(ctx: Context, which: Int): Bitmap? {
        val wm = WallpaperManager.getInstance(ctx)
        if (wm.getWallpaperInfo(which) != null) return null // live wallpaper
        val id = runCatching { wm.getWallpaperId(which) }.getOrDefault(-1)
        val kept = keptFile(ctx, which)
        val keptId = getInt(ctx, if (which == WallpaperManager.FLAG_LOCK) "lock_id" else "home_id", -2)
        if (kept.exists() && id == keptId) {
            runCatching { android.graphics.BitmapFactory.decodeFile(kept.path) }.getOrNull()?.let { return it }
        }
        val d = runCatching { wm.getDrawable(which) }.getOrNull()
            ?: if (which == WallpaperManager.FLAG_LOCK) runCatching { wm.getDrawable(WallpaperManager.FLAG_SYSTEM) }.getOrNull() else null
        return (d as? BitmapDrawable)?.bitmap
    }

    fun fit(src: Bitmap, maxW: Int): Bitmap {
        if (src.width <= maxW) return src
        val h = (src.height.toFloat() * maxW / src.width).roundToInt()
        return Bitmap.createScaledBitmap(src, maxW, max(1, h), true)
    }

    /** Applies the effects to [src]. */
    fun apply(ctx: Context, src: Bitmap, blur: Int, dim: Int, tone: String, vignette: Boolean): Bitmap {
        var img = src.copy(Bitmap.Config.ARGB_8888, true)
        // blur 1..25 is 3..75 px on a 1080 px wide screen, whatever the size of this copy
        if (blur > 0) img = blurred(img, blur * 3f * img.width / 1080f)
        val out = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        toneMatrix(ctx, tone)?.let { p.colorFilter = ColorMatrixColorFilter(it) }
        c.drawBitmap(img, 0f, 0f, p)
        if (dim > 0) c.drawColor(android.graphics.Color.argb((dim * 255 / 100).coerceIn(0, 255), 0, 0, 0))
        if (vignette) {
            val r = max(out.width, out.height) * 0.75f
            val v = Paint().apply {
                shader = RadialGradient(
                    out.width / 2f, out.height / 2f, r,
                    intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), v)
        }
        return out
    }

    private fun toneMatrix(ctx: Context, tone: String): ColorMatrix? =
        when (tone) {
            "mono" -> ColorMatrix().apply { setSaturation(0f) }
            "warm" -> ColorMatrix(floatArrayOf(
                1.06f, 0f, 0f, 0f, 6f,
                0f, 1.0f, 0f, 0f, 2f,
                0f, 0f, 0.88f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f))
            "duo" -> {
                // Shadows in the theme's deep accent, highlights in its light accent.
                val dark = ctx.getColor(android.R.color.system_accent1_900)
                val light = ctx.getColor(android.R.color.system_accent1_100)
                fun row(sh: Int, hi: Int): FloatArray {
                    val a = (hi - sh) / 255f
                    return floatArrayOf(0.299f * a, 0.587f * a, 0.114f * a, 0f, sh.toFloat())
                }
                val r = row(android.graphics.Color.red(dark), android.graphics.Color.red(light))
                val g = row(android.graphics.Color.green(dark), android.graphics.Color.green(light))
                val b = row(android.graphics.Color.blue(dark), android.graphics.Color.blue(light))
                ColorMatrix(r + g + b + floatArrayOf(0f, 0f, 0f, 1f, 0f))
            }
            else -> null
        }

    /** A smooth blur: work on a small copy (3 box passes), then scale back up. */
    private fun blurred(src: Bitmap, radiusPx: Float): Bitmap {
        if (radiusPx < 0.5f) return src
        val down = (radiusPx / 3f).coerceIn(1f, 16f)
        val w = max(4, (src.width / down).roundToInt())
        val h = max(4, (src.height / down).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val r = max(1, (radiusPx / down).roundToInt())
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxH(px, tmp, w, h, r)
            boxV(tmp, px, w, h, r)
        }
        small.setPixels(px, 0, w, 0, 0, w, h)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            var rs = 0; var gs = 0; var bs = 0
            val row = y * w
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = src[row + (x - r).coerceIn(0, w - 1)]
                val inn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                rs += ((inn shr 16) and 255) - ((out shr 16) and 255)
                gs += ((inn shr 8) and 255) - ((out shr 8) and 255)
                bs += (inn and 255) - (out and 255)
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rs += ((inn shr 16) and 255) - ((out shr 16) and 255)
                gs += ((inn shr 8) and 255) - ((out shr 8) and 255)
                bs += (inn and 255) - (out and 255)
            }
        }
    }

    /** Applies the effects to the real wallpaper(s), starting from the kept originals. */
    fun applyToWallpaper(ctx: Context, target: String, blur: Int, dim: Int, tone: String, vignette: Boolean): Boolean {
        val wm = WallpaperManager.getInstance(ctx)
        val screens =
            when (target) {
                TARGET_HOME -> listOf(WallpaperManager.FLAG_SYSTEM)
                TARGET_LOCK -> listOf(WallpaperManager.FLAG_LOCK)
                else -> listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK)
            }
        // Read every original before changing anything: a lock screen without its own picture
        // shows the home one, which is about to change.
        val sources = screens.associateWith { original(ctx, it) }
        var ok = true
        for (which in screens) {
            val src = sources[which] ?: continue
            runCatching {
                // keep the untouched picture first
                keptFile(ctx, which).outputStream().use { src.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val full = fit(src, 1440)
                val out = apply(ctx, full, blur, dim, tone, vignette)
                val id = wm.setBitmap(out, null, true, which)
                putInt(ctx, if (which == WallpaperManager.FLAG_LOCK) "lock_id" else "home_id", id)
            }.onFailure {
                Log.w(TAG, "apply failed", it)
                ok = false
            }
        }
        putInt(ctx, "blur", blur)
        putInt(ctx, "dim", dim)
        putInt(ctx, "tone", TONES.indexOf(tone).coerceAtLeast(0))
        putInt(ctx, "vignette", if (vignette) 1 else 0)
        return ok
    }
}

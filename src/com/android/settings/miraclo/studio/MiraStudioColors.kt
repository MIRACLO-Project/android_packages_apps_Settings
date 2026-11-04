/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.spa.preference.ComposePreference
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.PI

/**
 * Mira Studio > Colours & style: the ROM-wide Material You picker. Colour from the wallpaper, a
 * basic colour or any custom colour; the style (Tonal spot, Vibrant, Expressive, Rainbow, Fruit
 * salad, Monochrome); dark theme and contrast. It writes the same setting the system theme
 * engine reads (Settings.Secure THEME_CUSTOMIZATION_OVERLAY_PACKAGES), so every app that follows
 * the system colours changes with it. ColorOS, iOS and One UI Quick Settings keep their own
 * colours.
 */
class MiraStudioColorsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { Colors() }
    }

    private class Theme(val source: String, val seed: Int, val style: String)

    private fun read(ctx: Context): Theme {
        val json =
            runCatching {
                    JSONObject(
                        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES) ?: "{}"
                    )
                }
                .getOrDefault(JSONObject())
        val source = json.optString(KEY_SOURCE, SOURCE_HOME)
        val seed =
            json.optString(KEY_PALETTE, "").let { s ->
                runCatching { android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s") }.getOrDefault(0)
            }
        return Theme(source, seed, json.optString(KEY_STYLE, "TONAL_SPOT"))
    }

    private fun write(ctx: Context, source: String, seed: Int?, style: String, index: Int) {
        val json = JSONObject()
        // keep the status bar icon style stored in the same setting
        runCatching {
            val old = JSONObject(
                Settings.Secure.getString(ctx.contentResolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES) ?: "{}"
            )
            old.keys().forEach { k ->
                if (k.startsWith("android.theme.customization.icon_pack")) json.put(k, old.get(k))
            }
        }
        json.put(KEY_SOURCE, source)
        json.put(KEY_STYLE, style)
        if (seed != null) {
            val hex = String.format("%08X", seed)
            json.put(KEY_PALETTE, hex)
            json.put(KEY_ACCENT, hex)
        }
        json.put(KEY_INDEX, index.toString())
        json.put("_applied_timestamp", System.currentTimeMillis())
        Settings.Secure.putString(ctx.contentResolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, json.toString())
    }

    private fun wallpaperSeeds(ctx: Context): List<Int> {
        val colors =
            runCatching { WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
                ?: return listOf(0xFF4A6FA5.toInt())
        return listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)
            .map { it.toArgb() }
            .distinct()
            .ifEmpty { listOf(0xFF4A6FA5.toInt()) }
    }

    @Composable
    private fun Colors() {
        val ctx = LocalContext.current
        val cs = MaterialTheme.colorScheme
        val saved = remember { read(ctx) }
        val wall = remember { wallpaperSeeds(ctx) }
        var source by remember {
            mutableStateOf(
                when {
                    saved.source == SOURCE_HOME -> "wallpaper"
                    BASIC.any { it == saved.seed } -> "basic"
                    else -> "custom"
                }
            )
        }
        var seedIndex by remember {
            mutableStateOf(
                when (source) {
                    "basic" -> BASIC.indexOf(saved.seed).coerceAtLeast(0)
                    else -> 0
                }
            )
        }
        val savedHue = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(saved.seed, it) }[0] }
        var hue by remember { mutableFloatStateOf(savedHue) }
        var style by remember { mutableStateOf(saved.style) }
        val ui = remember { ctx.getSystemService(UiModeManager::class.java) }
        var dark by remember { mutableStateOf(ui?.nightMode == UiModeManager.MODE_NIGHT_YES) }
        var contrast by remember {
            mutableStateOf(runCatching { Settings.Secure.getFloat(ctx.contentResolver, "contrast_level", 0f) }.getOrDefault(0f))
        }

        fun seedNow(): Int =
            when (source) {
                "wallpaper" -> wall[seedIndex.coerceIn(0, wall.size - 1)]
                "basic" -> BASIC[seedIndex.coerceIn(0, BASIC.size - 1)]
                else -> android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.78f))
            }

        fun apply() {
            when (source) {
                "wallpaper" -> write(ctx, SOURCE_HOME, if (seedIndex == 0) null else seedNow(), style, seedIndex)
                else -> write(ctx, SOURCE_PRESET, seedNow(), if (source == "basic" && seedIndex == BASIC.size - 1) "MONOCHROMATIC" else style, seedIndex)
            }
        }

        val seed = Color(seedNow())
        val pv = preview(seed, style, dark, contrast)

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Live preview of the whole system.
            StudioCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(listOf(pv.primary to pv.onPrimary, pv.primary to pv.onPrimary), listOf(pv.secondary to pv.onSecondary, pv.secondary to pv.onSecondary)).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (bg, _) -> Box(Modifier.weight(1f).height(52.dp).background(bg, RoundedCornerShape(26.dp))) }
                                }
                            }
                        }
                        Column(
                            Modifier.width(120.dp).height(112.dp).background(pv.tertiary, RoundedCornerShape(28.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("3:16", color = pv.onTertiary, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
                            Text("Mon, Nov 2", color = pv.onTertiary, fontSize = 12.sp)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().background(pv.surface, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(36.dp).background(pv.secondary, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text("Aarav", color = pv.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("See you at 7?", color = pv.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                        Box(Modifier.height(32.dp).background(pv.primary, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("Reply", color = pv.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        repeat(5) { Box(Modifier.size(48.dp).background(pv.primaryContainer, CircleShape)) }
                    }
                }
            }

            // Colour source.
            StudioSegmented(listOf("wallpaper" to "Wallpaper", "basic" to "Basic", "custom" to "Custom"), source) {
                source = it
                seedIndex = 0
                apply()
            }
            StudioCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                    if (source == "custom") {
                        Text("Pick any colour", color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                        Box(
                            Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)).background(
                                Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 0.62f, 0.78f))) })
                            )
                        )
                        Slider(value = hue, onValueChange = { hue = it }, onValueChangeFinished = { apply() }, valueRange = 0f..359f)
                    } else {
                        val list = if (source == "basic") BASIC else wall
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            list.forEachIndexed { i, c ->
                                val sel = i == seedIndex
                                val mono = source == "basic" && i == BASIC.size - 1
                                Box(
                                    Modifier.size(72.dp).clip(CircleShape)
                                        .background(if (sel) cs.primary else Color.Transparent)
                                        .clickable {
                                            seedIndex = i
                                            apply()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val base = Color(c)
                                    Column(Modifier.size(56.dp).clip(CircleShape)) {
                                        Box(Modifier.fillMaxWidth().weight(1f).background(if (mono) Color(0xFF8C8C8C) else tone(base, 0.62f)))
                                        Row(Modifier.fillMaxWidth().weight(1f)) {
                                            Box(Modifier.weight(1f).fillMaxHeight().background(if (mono) Color(0xFFBFBFBF) else shift(base, 60f, 0.72f)))
                                            Box(Modifier.weight(1f).fillMaxHeight().background(if (mono) Color(0xFF595959) else tone(base, 0.40f)))
                                        }
                                    }
                                    if (sel) Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Style.
            StudioCard {
                Column(Modifier.padding(vertical = 18.dp)) {
                    Text("Style", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        STYLES.forEach { (key, label) ->
                            val sel = key == style
                            val p = preview(seed, key, dark, 0f)
                            Column(
                                Modifier.width(92.dp).clip(RoundedCornerShape(22.dp))
                                    .background(if (sel) cs.primaryContainer else cs.surface)
                                    .clickable {
                                        style = key
                                        apply()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    listOf(p.primary, p.secondary, p.tertiary).forEach {
                                        Box(Modifier.size(18.dp, 36.dp).background(it, RoundedCornerShape(9.dp)))
                                    }
                                }
                                Text(label, color = if (sel) cs.onPrimaryContainer else cs.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Dark theme, contrast, where it applies.
            StudioCard {
                Column {
                    StudioSwitchRow("Dark theme", null, dark) {
                        dark = it
                        ui?.setNightModeActivated(it)
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text("Contrast", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                        StudioSegmented(
                            listOf("0" to "Standard", "0.5" to "Medium", "1" to "High"),
                            when {
                                contrast >= 0.75f -> "1"
                                contrast >= 0.25f -> "0.5"
                                else -> "0"
                            },
                        ) {
                            contrast = it.toFloat()
                            runCatching { Settings.Secure.putFloat(ctx.contentResolver, "contrast_level", contrast) }
                        }
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text("Used everywhere", color = cs.onSurface, fontSize = 16.sp)
                        Text(
                            "Settings, Stock Quick Settings, notifications, the launcher, themed icons, keyboard and apps that follow the system colours. ColorOS, iOS and One UI Quick Settings keep their own colours.",
                            color = cs.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    /** Preview tones for a seed and style (an approximation of the system palette). */
    private class Preview(
        val primary: Color, val onPrimary: Color,
        val secondary: Color, val onSecondary: Color,
        val tertiary: Color, val onTertiary: Color,
        val primaryContainer: Color, val surface: Color, val onSurface: Color,
    )

    private fun hsl(h: Float, s: Float, l: Float): Color {
        val hh = ((h % 360f) + 360f) % 360f
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((hh / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) =
            when {
                hh < 60 -> Triple(c, x, 0f)
                hh < 120 -> Triple(x, c, 0f)
                hh < 180 -> Triple(0f, c, x)
                hh < 240 -> Triple(0f, x, c)
                hh < 300 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
        return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
    }

    private fun hueOf(c: Color): Float = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }[0]

    private fun tone(c: Color, l: Float) = hsl(hueOf(c), 0.6f, l)

    private fun shift(c: Color, by: Float, l: Float) = hsl(hueOf(c) + by, 0.4f, l)

    private fun preview(seed: Color, style: String, dark: Boolean, contrast: Float): Preview {
        val h = hueOf(seed)
        val (k, h2, h3) =
            when (style) {
                "VIBRANT" -> Triple(1.45f, 15f, 40f)
                "EXPRESSIVE" -> Triple(1.25f, 120f, 240f)
                "RAINBOW" -> Triple(1.3f, 0f, 60f)
                "FRUIT_SALAD" -> Triple(1.3f, -50f, 30f)
                "MONOCHROMATIC" -> Triple(0f, 0f, 0f)
                else -> Triple(1f, 0f, 60f)
            }
        val ct = contrast * 0.14f
        return if (dark) {
            Preview(
                primary = hsl(h, (0.7f * k).coerceAtMost(1f), 0.78f + ct / 2), onPrimary = hsl(h, 0.6f * k, 0.18f - ct / 2),
                secondary = hsl(h + h2, 0.22f * k, 0.26f - ct), onSecondary = hsl(h + h2, 0.3f * k, 0.9f),
                tertiary = hsl(h + h3, 0.4f * k, 0.3f - ct), onTertiary = hsl(h + h3, 0.7f * k, 0.9f),
                primaryContainer = hsl(h, 0.45f * k, 0.3f - ct), surface = hsl(h, 0.1f * k, 0.07f), onSurface = hsl(h, 0.1f * k, 0.91f),
            )
        } else {
            Preview(
                primary = hsl(h, 0.55f * k, 0.4f - ct), onPrimary = Color.White,
                secondary = hsl(h + h2, 0.35f * k, 0.88f), onSecondary = hsl(h + h2, 0.4f * k, 0.15f),
                tertiary = hsl(h + h3, 0.7f * k, 0.88f), onTertiary = hsl(h + h3, 0.7f * k, 0.15f),
                primaryContainer = hsl(h, (0.8f * k).coerceAtMost(1f), 0.88f), surface = hsl(h, 0.3f * k, 0.98f), onSurface = hsl(h, 0.1f * k, 0.11f),
            )
        }
    }

    private companion object {
        const val KEY_PALETTE = "android.theme.customization.system_palette"
        const val KEY_ACCENT = "android.theme.customization.accent_color"
        const val KEY_SOURCE = "android.theme.customization.color_source"
        const val KEY_INDEX = "android.theme.customization.color_index"
        const val KEY_STYLE = "android.theme.customization.theme_style"
        const val SOURCE_HOME = "home_wallpaper"
        const val SOURCE_PRESET = "preset"

        /** Basic colours: blue, green, purple, pink, orange, teal, yellow, and grey (monochrome). */
        val BASIC =
            listOf(0xFF3D6FD6, 0xFF2E8B57, 0xFF7E57C2, 0xFFD6457A, 0xFFE0782E, 0xFF1E9AA0, 0xFFC9A227, 0xFF8C8C8C)
                .map { it.toInt() }

        val STYLES =
            listOf(
                "TONAL_SPOT" to "Tonal spot",
                "VIBRANT" to "Vibrant",
                "EXPRESSIVE" to "Expressive",
                "RAINBOW" to "Rainbow",
                "FRUIT_SALAD" to "Fruit salad",
                "MONOCHROMATIC" to "Monochrome",
            )
    }
}

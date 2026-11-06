/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.Context
import android.graphics.fonts.FontStyle
import android.provider.Settings
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.spa.preference.ComposePreference
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Mira Studio › Style › Fonts: text size (the system font scale) and bold text (the system
 * font weight adjustment), with a live sample.
 */
class MiraStudioFontsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { Page() }
    }

    override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        miraFlat(holder)
    }

    @Composable
    private fun Page() {
        val ctx = LocalContext.current
        val cr = ctx.contentResolver
        val cs = MaterialTheme.colorScheme
        var scale by remember { mutableFloatStateOf(Settings.System.getFloat(cr, Settings.System.FONT_SCALE, 1f)) }
        var bold by remember {
            mutableStateOf(Settings.Secure.getInt(cr, Settings.Secure.FONT_WEIGHT_ADJUSTMENT, 0) > 0)
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Sample.
            Column(
                Modifier.fillMaxWidth()
                    .background(cs.surfaceContainerHigh, RoundedCornerShape(28.dp))
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val w = if (bold) FontWeight.Bold else FontWeight.Normal
                Text("Aa", color = cs.onSurface, fontSize = (40 * scale).sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
                Text("The quick brown fox jumps over the lazy dog.", color = cs.onSurface, fontSize = (16 * scale).sp, fontWeight = w)
                Text("1 2 3 4 5 6 7 8 9 0", color = cs.onSurfaceVariant, fontSize = (13 * scale).sp, fontWeight = w)
            }
            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Text size", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("${(scale * 100).roundToInt()}%", color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        StudioResetIcon(scale != 1f) {
                            scale = 1f
                            Settings.System.putFloat(cr, Settings.System.FONT_SCALE, 1f)
                        }
                    }
                    Slider(
                        value = scale,
                        onValueChange = { scale = (it * 20).roundToInt() / 20f },
                        onValueChangeFinished = { Settings.System.putFloat(cr, Settings.System.FONT_SCALE, scale) },
                        valueRange = 0.85f..1.3f,
                        steps = 8,
                    )
                }
            }
            StudioCard {
                StudioSwitchRow("Bold text", "Heavier text everywhere", bold) {
                    bold = it
                    Settings.Secure.putInt(
                        cr,
                        Settings.Secure.FONT_WEIGHT_ADJUSTMENT,
                        if (it) FontStyle.FONT_WEIGHT_BOLD - FontStyle.FONT_WEIGHT_NORMAL else 0,
                    )
                }
            }
        }
    }
}

/**
 * Mira Studio › Look & feel › Animations: the Material 3 motion style (Standard, Expressive or
 * Snappy, read by System UI and Settings) and the speed (the three system animation scales, so
 * apps and the launcher follow too). The preview plays each scene with the chosen spring and
 * draws its curve, so the overshoot can be seen.
 */
class MiraStudioMotionPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { Page() }
    }

    override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        miraFlat(holder)
    }

    @Composable
    private fun Page() {
        val ctx = LocalContext.current
        val cr = ctx.contentResolver
        val cs = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        var style by remember {
            mutableIntStateOf(Settings.Secure.getInt(cr, KEY_STYLE, EXPRESSIVE).coerceIn(0, 2))
        }
        var speed by remember {
            val v = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            mutableStateOf(SPEEDS.minByOrNull { abs(it.second - v) }!!.first)
        }
        var scene by remember { mutableStateOf("shade") }
        val anim = remember { Animatable(0f) }
        var target by remember { mutableFloatStateOf(0f) }
        val trace = remember { mutableStateListOf<Float>() }

        fun play() {
            if (!anim.isRunning) trace.clear()
            target = if (target > 0.5f) 0f else 1f
            val spec = SPRINGS[style]
            scope.launch {
                anim.animateTo(target, spring(spec.first, spec.second)) {
                    trace.add(value)
                    if (trace.size > 60) trace.removeAt(0)
                }
            }
        }

        fun reset(newScene: String) {
            scene = newScene
            target = 0f
            trace.clear()
            scope.launch { anim.snapTo(0f) }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Preview: the phone plays the scene; tap it (again mid-way to interrupt).
            StudioCard {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
                    Text("Preview", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SCENES.forEach { (key, label) ->
                            val sel = key == scene
                            Box(
                                Modifier.clip(RoundedCornerShape(18.dp))
                                    .background(if (sel) cs.secondaryContainer else Color.Transparent)
                                    .border(1.dp, if (sel) cs.secondaryContainer else cs.outlineVariant, RoundedCornerShape(18.dp))
                                    .clickable { reset(key) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(label, color = if (sel) cs.onSecondaryContainer else cs.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                        MotionPhone(scene, anim.value, target, Modifier.clickable { play() })
                    }
                    Text(
                        "Tap the phone to play · tap again mid-way to interrupt",
                        color = cs.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
                    Text("Motion style", color = cs.onSurface, fontSize = 16.sp)
                    Text(
                        when (style) {
                            STANDARD -> "Material 3 standard: calm and precise, barely any bounce."
                            SNAPPY -> "Material 3 fast springs: quick and firm."
                            else -> "Material 3 Expressive: lively, with a soft overshoot."
                        },
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    )
                    StudioSegmented(STYLES.map { it.first.toString() to it.second }, style.toString()) { pick ->
                        style = pick.toInt()
                        Settings.Secure.putInt(cr, KEY_STYLE, style)
                        reset(scene)
                    }
                }
            }
            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
                    Text("Speed", color = cs.onSurface, fontSize = 16.sp)
                    Text(
                        "Apps, the launcher and System UI all follow it",
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    )
                    StudioSegmented(SPEEDS.map { it.first to it.third }, speed) { pick ->
                        speed = pick
                        val v = SPEEDS.first { it.first == pick }.second
                        for (key in listOf(
                            Settings.Global.WINDOW_ANIMATION_SCALE,
                            Settings.Global.TRANSITION_ANIMATION_SCALE,
                            Settings.Global.ANIMATOR_DURATION_SCALE,
                        )) {
                            Settings.Global.putFloat(cr, key, v)
                        }
                        reset(scene)
                    }
                }
            }
            // The curve of the last run: the dashed line is the end position; above it is overshoot.
            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
                    val over = trace.maxOrNull() ?: 1f
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Motion curve", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (over > 1.001f) "overshoot ${((over - 1f) * 100).roundToInt()}%" else "no overshoot",
                            color = cs.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(96.dp).padding(top = 12.dp)) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (i in 0 until 60) {
                                val v = trace.getOrNull(i)
                                Box(
                                    Modifier.weight(1f)
                                        .height(if (v == null) 2.dp else (84f * (v / 1.33f).coerceIn(0.02f, 1f)).dp)
                                        .background(
                                            when {
                                                v == null -> cs.surfaceContainerHighest
                                                v > 1.001f -> cs.error
                                                else -> cs.primary
                                            },
                                            RoundedCornerShape(2.dp),
                                        )
                                )
                            }
                        }
                        // end position: 1.0 of 1.33
                        Canvas(Modifier.fillMaxWidth().height(84.dp).align(Alignment.BottomStart)) {
                            val y = size.height * (1f - 1f / 1.33f)
                            drawLine(
                                cs.onSurfaceVariant,
                                androidx.compose.ui.geometry.Offset(0f, y),
                                androidx.compose.ui.geometry.Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                            )
                        }
                    }
                    Text(
                        "Dashed line = end position. Bars above it are the spring's overshoot.",
                        color = cs.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }

    /** A small phone (drawn in 412 x 915 units) playing one scene at progress [p]. */
    @Composable
    private fun MotionPhone(scene: String, p: Float, target: Float, modifier: Modifier) {
        val w = 196f
        val f = w / 412f
        fun d(v: Float) = (v * f).dp
        val c = { v: Float -> v.coerceIn(0f, 1f) }
        fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
        Box(
            modifier.width(d(412f)).height(d(915f))
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF243A63), Color(0xFF101521))))
        ) {
            // Home: clock, 3 rows of apps and the dock.
            val homeAlpha = if (scene == "app") 1f - c(p) * 0.6f else 1f
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha }) {
                Text("12:45", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.offset(d(40f), d(130f)))
                for (i in 0 until 12) {
                    Box(
                        Modifier.offset(d(52f + (i % 4) * 83f), d(470f + (i / 4) * 96f))
                            .size(d(58f))
                            .background(APP_COLORS[i % APP_COLORS.size], RoundedCornerShape(d(18f)))
                    )
                }
                for (i in 0 until 4) {
                    Box(
                        Modifier.offset(d(52f + i * 83f), d(772f)).size(d(58f))
                            .background(APP_COLORS[(i + 5) % APP_COLORS.size], RoundedCornerShape(d(18f)))
                    )
                }
            }
            when (scene) {
                "shade", "all" -> {
                    val travel = if (scene == "all") 620f else 600f
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = c(p) * 0.45f)))
                    Box(
                        Modifier.width(d(412f)).height(d(travel))
                            .offset(y = d((p - 1f) * travel))
                            .background(Color(0xFF1D2026), RoundedCornerShape(bottomStart = d(36f), bottomEnd = d(36f)))
                    ) {
                        for (i in 0 until 6) {
                            val q = c((p - i * 0.05f) / 0.8f)
                            val on = i in setOf(0, 1, 4, 5)
                            Box(
                                Modifier.offset(d(24f + (i % 2) * 186f), d(110f + (i / 2) * 84f + (1f - q) * 28f))
                                    .graphicsLayer { alpha = q }
                                    .width(d(176f)).height(d(72f))
                                    .background(if (on) Color(0xFFA8C7FA) else Color(0xFF33363E), RoundedCornerShape(d(36f)))
                            )
                        }
                    }
                }
                "tile" -> {
                    val s = 1f - 0.09f * kotlin.math.sin(Math.PI.toFloat() * c(p))
                    val on = target > 0.5f
                    Box(
                        Modifier.offset(d(56f), d(380f)).width(d(300f)).height(d(96f))
                            .graphicsLayer { scaleX = s; scaleY = s }
                            .background(lerp(Color(0xFF33363E), Color(0xFFA8C7FA), c(p)), RoundedCornerShape(d(48f))),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column(Modifier.padding(start = d(32f))) {
                            Text("Internet", color = if (p > 0.5f) Color(0xFF0A305F) else Color(0xFFE2E2E9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (on) "Home 5G" else "Off", color = if (p > 0.5f) Color(0xFF0A305F) else Color(0xFFC4C6D0), fontSize = 9.sp)
                        }
                    }
                }
                "dialog", "app" -> {
                    val isApp = scene == "app"
                    val from = if (isApp) floatArrayOf(52f, 772f, 58f, 58f, 18f) else floatArrayOf(56f, 380f, 300f, 96f, 28f)
                    val to = if (isApp) floatArrayOf(0f, 0f, 412f, 915f, 44f) else floatArrayOf(24f, 250f, 364f, 400f, 30f)
                    if (!isApp) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = c(p) * 0.5f)))
                    val bg =
                        if (isApp) lerp(Color(0xFF1F7A43), Color(0xFF121316), c(p * 1.4f))
                        else lerp(Color(0xFFA8C7FA), Color(0xFF1D2026), c(p * 1.6f))
                    Box(
                        Modifier.offset(d(lerpF(from[0], to[0], p)), d(lerpF(from[1], to[1], p)))
                            .width(d(lerpF(from[2], to[2], p).coerceAtLeast(1f)))
                            .height(d(lerpF(from[3], to[3], p).coerceAtLeast(1f)))
                            .background(bg, RoundedCornerShape(d(lerpF(from[4], to[4], p).coerceAtLeast(0f))))
                    ) {
                        val inner = c((p - 0.55f) / 0.45f)
                        Column(
                            Modifier.graphicsLayer { alpha = inner }.padding(d(28f)),
                            verticalArrangement = Arrangement.spacedBy(d(18f)),
                        ) {
                            Text(if (isApp) "Messages" else "Internet", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = if (isApp) d(70f) else 0.dp))
                            repeat(if (isApp) 3 else 4) {
                                Box(Modifier.fillMaxWidth().height(d(44f)).background(Color(0x22FFFFFF), RoundedCornerShape(d(22f))))
                            }
                        }
                    }
                }
                "recents" -> {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = c(p))))
                    for (i in -1..1) {
                        val s = lerpF(1f, 0.62f, p)
                        val x = if (i == 0) 0f else i * lerpF(430f, 270f, p)
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer { translationX = d(x).toPx(); scaleX = s; scaleY = s }
                                .clip(RoundedCornerShape(d(lerpF(44f, 34f, c(p)))))
                                .background(if (i == 0) Color(0xFF1D2026) else Color(0xFF262A33))
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(d(120f))
                                    .background(if (i == -1) Color(0xFF3C5A8A) else if (i == 0) Color(0xFF1F7A43) else Color(0xFFB8336A))
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_STYLE = "miraclo_motion_style"
        const val STANDARD = 0
        const val EXPRESSIVE = 1
        const val SNAPPY = 2

        /** Damping ratio and stiffness of each style's default spatial spring (Material 3 tokens). */
        val SPRINGS = listOf(0.9f to 700f, 0.8f to 380f, 0.9f to 1400f)
        val STYLES = listOf(STANDARD to "Standard", EXPRESSIVE to "Expressive", SNAPPY to "Snappy")

        /** Key, system animation scale (time), label: Relaxed is 0.7x the speed, Fastest 1.7x. */
        val SPEEDS =
            listOf(
                Triple("relaxed", 1.4f, "Relaxed"),
                Triple("normal", 1f, "Normal"),
                Triple("quick", 0.75f, "Quick"),
                Triple("fastest", 0.6f, "Fastest"),
            )

        val SCENES =
            listOf(
                "shade" to "Shade",
                "tile" to "Tile press",
                "dialog" to "Dialog",
                "app" to "App open",
                "recents" to "Recents",
                "all" to "Pull to QS",
            )

        val APP_COLORS =
            listOf(
                Color(0xFFD2672D), Color(0xFFC4453B), Color(0xFF2F6FD6), Color(0xFF1F2A44),
                Color(0xFF1F7A43), Color(0xFFB8336A), Color(0xFF3C5A8A), Color(0xFFA07A14),
            )
    }
}

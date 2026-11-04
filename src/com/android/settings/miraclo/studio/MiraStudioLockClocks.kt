/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.Context
import android.graphics.Typeface
import android.provider.Settings
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settings.spa.preference.ComposePreference
import org.json.JSONObject

/**
 * Mira Studio › Top Bar › Lock Screen: the 18 MiraCLO clocks (drawn by System UI), their colour,
 * weight and size. Writes the same setting the wallpaper & style picker writes
 * (Settings.Secure LOCK_SCREEN_CUSTOM_CLOCK_FACE), so both stay in step.
 */
class MiraStudioLockClocksPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { Page() }
    }

    private class Style(val key: String, val name: String, val family: String, val weight: Int, val stacked: Boolean)

    private val styles =
        listOf(
            Style("stack", "Stack", "sans-serif", 300, true),
            Style("bold", "Bold", "sans-serif", 650, false),
            Style("duo", "Duo", "sans-serif", 700, true),
            Style("slim", "Slim", "sans-serif", 200, false),
            Style("classic", "Classic", "sans-serif", 500, false),
            Style("tall", "Tall", "sans-serif-condensed", 500, true),
            Style("serif", "Serif", "serif", 400, false),
            Style("lens", "Lens", "sans-serif", 200, false),
            Style("analog", "Analog", "sans-serif", 400, false),
            Style("mono", "Mono", "monospace", 700, false),
            Style("round", "Round", "sans-serif", 600, true),
            Style("dot", "Dot", "monospace", 400, false),
            Style("outline", "Outline", "sans-serif", 800, false),
            Style("split", "Split", "sans-serif", 700, true),
            Style("quad", "Quad", "sans-serif", 800, true),
            Style("elegant", "Elegant", "serif", 500, false),
            Style("wide", "Wide", "sans-serif", 300, false),
            Style("neo", "Neo", "sans-serif", 300, true),
        )

    private val colours =
        listOf<Pair<String, Int?>>(
            "White" to 0xFFFFFFFF.toInt(),
            "From wallpaper" to null,
            "Blue" to 0xFFD6E3FF.toInt(),
            "Peach" to 0xFFFFDBCF.toInt(),
            "Mint" to 0xFFC9F0DC.toInt(),
            "Lilac" to 0xFFEBDDFF.toInt(),
            "Gold" to 0xFFFFE08A.toInt(),
        )

    private fun idOf(key: String) = "MIRA_" + key.uppercase()

    @Composable
    private fun Page() {
        val ctx = LocalContext.current
        val cr = ctx.contentResolver
        val cs = MaterialTheme.colorScheme
        val current = remember {
            runCatching { JSONObject(Settings.Secure.getString(cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE)) }
                .getOrNull()
        }
        var styleKey by remember {
            mutableStateOf(
                current?.optString("clockId")?.let { id -> styles.firstOrNull { idOf(it.key) == id }?.key } ?: ""
            )
        }
        var seed by remember {
            mutableStateOf(current?.takeIf { it.has("seedColor") && !it.isNull("seedColor") }?.optInt("seedColor"))
        }
        var weight by remember { mutableIntStateOf(Settings.Secure.getInt(cr, KEY_WEIGHT, 0)) }
        var scale by remember { mutableIntStateOf(Settings.Secure.getInt(cr, KEY_SCALE, 100).coerceIn(60, 140)) }

        fun write(key: String, colour: Int?) {
            if (key.isEmpty()) return
            val json = JSONObject().put("clockId", idOf(key))
            if (colour != null) json.put("seedColor", colour)
            json.put("metadata", JSONObject().put("_applied_timestamp", System.currentTimeMillis()))
            Settings.Secure.putString(cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE, json.toString())
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Styles, three per row.
            StudioCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Clock", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                    styles.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { s ->
                                val sel = s.key == styleKey
                                Column(
                                    Modifier.weight(1f)
                                        .height(96.dp)
                                        .background(Color(0xFF15171C), RoundedCornerShape(20.dp))
                                        .border(2.dp, if (sel) cs.primary else Color.Transparent, RoundedCornerShape(20.dp))
                                        .clickable {
                                            styleKey = s.key
                                            write(s.key, seed)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    val family = remember(s.family, s.weight) {
                                        FontFamily(Typeface.create(Typeface.create(s.family, Typeface.NORMAL), s.weight, false))
                                    }
                                    Text(
                                        if (s.key == "analog") "◷" else if (s.stacked) "10\n45" else "10:45",
                                        color = Color.White,
                                        fontFamily = family,
                                        fontSize = if (s.stacked) 20.sp else 22.sp,
                                        lineHeight = 20.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(s.name, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Text(
                        "Also in Wallpaper & style › Lock screen.",
                        color = cs.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            // Colour.
            StudioCard {
                Column(Modifier.padding(vertical = 18.dp)) {
                    Text("Colour", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
                    Text(colours.firstOrNull { it.second == seed }?.first ?: "Custom", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colours.forEach { (_, c) ->
                            val sel = c == seed
                            Box(
                                Modifier.size(44.dp)
                                    .border(3.dp, if (sel) cs.primary else Color.Transparent, CircleShape)
                                    .padding(5.dp)
                                    .background(
                                        if (c == null) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF3D6C9), Color(0xFFC9B5FF)))
                                        else androidx.compose.ui.graphics.SolidColor(Color(c)),
                                        CircleShape,
                                    )
                                    .clickable {
                                        seed = c
                                        write(styleKey, c)
                                    }
                            )
                        }
                    }
                }
            }
            // Weight and size.
            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Weight", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                weight == 0 -> "Style's own"
                                weight <= 250 -> "Hairline"
                                weight <= 350 -> "Light"
                                weight <= 450 -> "Regular"
                                weight <= 600 -> "Medium"
                                else -> "Heavy"
                            },
                            color = cs.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Slider(
                        value = weight.toFloat(),
                        onValueChange = { weight = (Math.round(it / 100f) * 100) },
                        onValueChangeFinished = {
                            Settings.Secure.putInt(cr, KEY_WEIGHT, weight)
                            write(styleKey, seed)
                        },
                        valueRange = 0f..900f,
                        steps = 8,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Size", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("$scale%", color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = scale.toFloat(),
                        onValueChange = { scale = (Math.round(it / 5f) * 5).coerceIn(60, 140) },
                        onValueChangeFinished = {
                            Settings.Secure.putInt(cr, KEY_SCALE, scale)
                            write(styleKey, seed)
                        },
                        valueRange = 60f..140f,
                        steps = 15,
                    )
                }
            }
        }
    }

    private companion object {
        const val KEY_WEIGHT = "miraclo_lock_clock_weight"
        const val KEY_SCALE = "miraclo_lock_clock_scale"
    }
}

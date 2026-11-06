/*
 * SPDX-FileCopyrightText: MIRACLO
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toArgb
import androidx.preference.PreferenceViewHolder
import com.android.settings.spa.preference.ComposePreference
import org.json.JSONObject

/**
 * Mira Studio › Lock screen: the MIRACLO clock faces (drawn by System UI with the same code as
 * these previews), their colour, weight and size, and the Custom face's own choices. Writes the
 * setting the wallpaper & style picker writes (Settings.Secure LOCK_SCREEN_CUSTOM_CLOCK_FACE),
 * so both stay in step.
 */
class MiraStudioLockClocksPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { Page() }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        miraFlat(holder)
    }

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

    /** A face drawn exactly as on the lock screen, filling the box it is given. */
    @Composable
    private fun Face(face: MiraClockFaces.Face, o: MiraClockFaces.Options, color: Color, modifier: Modifier) {
        val density = LocalContext.current.resources.displayMetrics.density
        Canvas(modifier) {
            // Laid out at phone width, then scaled down into the box.
            val phoneW = 360f * density
            val (fw, fh) = MiraClockFaces.measure(face, o, "10", "45", density, phoneW * 0.9f)
            val k = minOf(size.width * 0.86f / maxOf(1f, fw), size.height * 0.8f / maxOf(1f, fh), 1f)
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save()
                n.translate(size.width / 2f - fw * k / 2f, size.height / 2f - fh * k / 2f)
                n.scale(k, k)
                MiraClockFaces.draw(n, face, o, "10", "45", 322.5f, 270f, color.toArgb(), false, 0f, 0f, fw, fh, density)
                n.restore()
            }
        }
    }

    @Composable
    private fun Page() {
        val ctx = LocalContext.current
        val cr = ctx.contentResolver
        val cs = MaterialTheme.colorScheme
        val current = remember {
            runCatching { JSONObject(Settings.Secure.getString(cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE)) }
                .getOrNull()
        }
        var faceKey by remember {
            mutableStateOf(MiraClockFaces.FACES.firstOrNull { it.id == current?.optString("clockId") }?.key ?: "")
        }
        var seed by remember {
            mutableStateOf(current?.takeIf { it.has("seedColor") && !it.isNull("seedColor") }?.optInt("seedColor"))
        }
        fun get(k: String, d: Int) = Settings.Secure.getInt(cr, k, d)
        var weight by remember { mutableIntStateOf(get(MiraClockFaces.KEY_WEIGHT, 0)) }
        var scale by remember { mutableIntStateOf(get(MiraClockFaces.KEY_SCALE, 100).coerceIn(60, 140)) }
        var stacked by remember { mutableStateOf(get(MiraClockFaces.KEY_LAYOUT, 0) == 1) }
        var alignStart by remember { mutableStateOf(get(MiraClockFaces.KEY_ALIGN, 0) == 1) }
        var font by remember { mutableIntStateOf(get(MiraClockFaces.KEY_FONT, 0)) }
        val options = MiraClockFaces.Options(weight, scale / 100f, stacked, alignStart, font)
        val wall = remember { MiraLive.lock(ctx)?.asImageBitmap() }
        val shown = MiraClockFaces.FACES.firstOrNull { it.key == faceKey }
        val ink = seed?.let { Color(it) } ?: Color.White

        fun write(key: String, colour: Int?) {
            if (key.isEmpty()) return
            val f = MiraClockFaces.FACES.first { it.key == key }
            val json = JSONObject().put("clockId", f.id)
            if (colour != null) json.put("seedColor", colour)
            json.put("metadata", JSONObject().put("_applied_timestamp", System.currentTimeMillis()))
            Settings.Secure.putString(cr, Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE, json.toString())
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The lock screen as it will look.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(220.dp, 400.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFF15171C))
                        .border(5.dp, cs.surfaceContainerHigh, RoundedCornerShape(30.dp))
                ) {
                    if (wall != null) {
                        Image(wall, null, Modifier.fillMaxWidth().height(400.dp), contentScale = ContentScale.Crop)
                    }
                    if (shown != null) {
                        Face(shown, options, ink, Modifier.fillMaxWidth().height(150.dp).padding(top = 30.dp))
                    } else {
                        Text("Android's clock", color = Color.White, fontSize = 15.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp))
                    }
                }
            }

            // Faces, two per row, each drawn for real.
            StudioCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Clock", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                    MiraClockFaces.FACES.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { f ->
                                val sel = f.key == faceKey
                                Column(
                                    Modifier.weight(1f)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(Color(0xFF15171C))
                                        .border(2.5.dp, if (sel) cs.primary else Color.Transparent, RoundedCornerShape(22.dp))
                                        .clickable {
                                            faceKey = f.key
                                            write(f.key, seed)
                                        }
                                        .padding(bottom = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Face(f, options, Color.White, Modifier.fillMaxWidth().height(92.dp))
                                    Text(f.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (shown != null) {
                        Text(shown.description, color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            // The Custom face's own choices.
            if (faceKey == "custom") {
                StudioCard {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        StudioDropdownRow("Lines", null, listOf("0" to "One line", "1" to "Hours over minutes"), if (stacked) "1" else "0") {
                            stacked = it == "1"
                            Settings.Secure.putInt(cr, MiraClockFaces.KEY_LAYOUT, if (stacked) 1 else 0)
                            write(faceKey, seed)
                        }
                        StudioDropdownRow("Position", null, listOf("0" to "Centre", "1" to "Left"), if (alignStart) "1" else "0") {
                            alignStart = it == "1"
                            Settings.Secure.putInt(cr, MiraClockFaces.KEY_ALIGN, if (alignStart) 1 else 0)
                            write(faceKey, seed)
                        }
                        StudioDropdownRow("Font", null, MiraClockFaces.FONT_NAMES.mapIndexed { i, n -> i.toString() to n }, font.toString()) {
                            font = it.toInt()
                            Settings.Secure.putInt(cr, MiraClockFaces.KEY_FONT, font)
                            write(faceKey, seed)
                        }
                    }
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
                                        write(faceKey, c)
                                    }
                            )
                        }
                    }
                }
            }

            // Weight and size (every face).
            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Weight", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                weight == 0 -> "The face's own"
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
                        StudioResetIcon(weight != 0) {
                            weight = 0
                            Settings.Secure.putInt(cr, MiraClockFaces.KEY_WEIGHT, 0)
                            write(faceKey, seed)
                        }
                    }
                    Slider(
                        value = weight.toFloat(),
                        onValueChange = { weight = (Math.round(it / 100f) * 100) },
                        onValueChangeFinished = {
                            Settings.Secure.putInt(cr, MiraClockFaces.KEY_WEIGHT, weight)
                            write(faceKey, seed)
                        },
                        valueRange = 0f..900f,
                        steps = 8,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Size", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("$scale%", color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        StudioResetIcon(scale != 100) {
                            scale = 100
                            Settings.Secure.putInt(cr, MiraClockFaces.KEY_SCALE, 100)
                            write(faceKey, seed)
                        }
                    }
                    Slider(
                        value = scale.toFloat(),
                        onValueChange = { scale = (Math.round(it / 5f) * 5).coerceIn(60, 140) },
                        onValueChangeFinished = {
                            Settings.Secure.putInt(cr, MiraClockFaces.KEY_SCALE, scale)
                            write(faceKey, seed)
                        },
                        valueRange = 60f..140f,
                        steps = 15,
                    )
                    Text(
                        "Also in Wallpaper & style › Lock screen.",
                        color = cs.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

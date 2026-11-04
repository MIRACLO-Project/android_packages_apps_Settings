/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.ColorDisplayManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.provider.Settings
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Group title used on the Tuning pages. */
@Composable
private fun TuningHeader(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
    )
}

@Composable
private fun TuningSegmentRow(title: String, summary: String, options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
        Text(title, color = cs.onSurface, fontSize = 16.sp)
        Text(summary, color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        StudioSegmented(options, selected, onPick)
    }
}

/**
 * Mira Studio › Tuning › Display: refresh rate (Adaptive or a fixed rate, from the rates the
 * panel really has), the refresh-rate counter, and the colour profile when the device offers
 * more than one.
 */
class MiraStudioDisplayTuningPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { Page() }
    }

    @Composable
    private fun Page() {
        val ctx = LocalContext.current
        val cr = ctx.contentResolver
        val rates = remember {
            runCatching {
                ctx.display!!.supportedModes.map { it.refreshRate.roundToInt() }.distinct().sorted()
            }.getOrDefault(listOf(60, 90, 120))
        }
        val top = rates.maxOrNull() ?: 120
        var rate by remember {
            val peak = Settings.System.getFloat(cr, KEY_PEAK, top.toFloat()).roundToInt()
            val min = Settings.System.getFloat(cr, KEY_MIN, 0f).roundToInt()
            mutableStateOf(if (min > 0 && min == peak) "$peak" else "auto")
        }
        var counter by remember { mutableStateOf(refreshCounter(null)) }
        val cdm = remember { ctx.getSystemService(ColorDisplayManager::class.java) }
        val modes = remember {
            runCatching {
                ctx.resources.getIntArray(com.android.internal.R.array.config_availableColorModes).toList()
            }.getOrDefault(emptyList()).filter { it in COLOR_NAMES }
        }
        var colour by remember { mutableStateOf(runCatching { cdm?.colorMode ?: -1 }.getOrDefault(-1)) }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TuningHeader("Refresh rate")
            StudioCard {
                Column {
                    val summary =
                        if (rate == "auto") "Switches with what's on screen, up to $top Hz"
                        else "Always $rate Hz (locked)"
                    TuningSegmentRow(
                        "Refresh rate",
                        summary,
                        listOf("auto" to "Adaptive") + rates.filter { it >= 60 }.map { "$it" to "$it" },
                        rate,
                    ) { pick ->
                        rate = pick
                        if (pick == "auto") {
                            Settings.System.putFloat(cr, KEY_PEAK, top.toFloat())
                            Settings.System.putFloat(cr, KEY_MIN, 0f)
                        } else {
                            Settings.System.putFloat(cr, KEY_PEAK, pick.toFloat())
                            Settings.System.putFloat(cr, KEY_MIN, pick.toFloat())
                        }
                    }
                    if (counter != null) {
                        StudioSwitchRow("Show refresh rate", "Small counter in the corner, for checking", counter == true) {
                            counter = refreshCounter(it)
                        }
                    }
                }
            }
            if (modes.size > 1 && cdm != null) {
                TuningHeader("Colour")
                StudioCard {
                    TuningSegmentRow(
                        "Colour profile",
                        "How saturated the screen looks",
                        modes.map { "$it" to COLOR_NAMES.getValue(it) },
                        "$colour",
                    ) { pick ->
                        runCatching { cdm.colorMode = pick.toInt() }
                        colour = runCatching { cdm.colorMode }.getOrDefault(pick.toInt())
                    }
                }
            }
        }
    }

    /**
     * SurfaceFlinger's refresh-rate overlay (the same switch as in Developer options).
     * [on] = null only reads the state. Returns null when it can't be reached.
     */
    private fun refreshCounter(on: Boolean?): Boolean? {
        val flinger: IBinder = ServiceManager.getService("SurfaceFlinger") ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer")
            data.writeInt(if (on == null) 2 else if (on) 1 else 0)
            if (on == null) {
                flinger.transact(SF_SHOW_REFRESH_RATE, data, reply, 0)
                reply.readBoolean()
            } else {
                flinger.transact(SF_SHOW_REFRESH_RATE, data, null, 0)
                on
            }
        } catch (_: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private companion object {
        const val KEY_PEAK = "peak_refresh_rate"
        const val KEY_MIN = "min_refresh_rate"
        const val SF_SHOW_REFRESH_RATE = 1034
        val COLOR_NAMES =
            mapOf(
                ColorDisplayManager.COLOR_MODE_NATURAL to "Natural",
                ColorDisplayManager.COLOR_MODE_BOOSTED to "Vivid",
                ColorDisplayManager.COLOR_MODE_SATURATED to "Saturated",
                ColorDisplayManager.COLOR_MODE_AUTOMATIC to "Adaptive",
            )
    }
}

/**
 * Mira Studio › Tuning › Battery: battery health from the battery itself (state of health,
 * charge cycles, design capacity), charging in the Dynamic Island, and Battery saver.
 */
class MiraStudioBatteryTuningPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { Page() }
    }

    @Composable
    private fun Page() {
        val ctx = LocalContext.current
        val cr = ctx.contentResolver
        val cs = MaterialTheme.colorScheme
        val health = remember {
            runCatching {
                ctx.getSystemService(BatteryManager::class.java)!!
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH)
            }.getOrDefault(-1)
        }
        val cycles = remember {
            runCatching {
                ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
                    .getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
            }.getOrDefault(-1)
        }
        val design = remember {
            runCatching { com.android.internal.os.PowerProfile(ctx).batteryCapacity.roundToInt() }
                .getOrDefault(0)
        }
        var island by remember { mutableStateOf(Settings.Secure.getInt(cr, KEY_ISLAND_CHARGING, 0) != 0) }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().background(cs.primaryContainer, RoundedCornerShape(28.dp))) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            if (health in 1..100) "Health $health%" else "Health not reported",
                            color = cs.onPrimaryContainer,
                            fontSize = 22.sp,
                        )
                        val parts = buildList {
                            if (cycles >= 0) add("$cycles charge cycles")
                            if (design > 0) add("%,d mAh design".format(design))
                        }
                        if (parts.isNotEmpty()) {
                            Text(parts.joinToString(" · "), color = cs.onPrimaryContainer.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                    }
                }
            }
            TuningHeader("Charging")
            StudioCard {
                Column {
                    StudioSwitchRow("Charging in the Dynamic Island", "Level and time to full when you plug in", island) {
                        island = it
                        Settings.Secure.putInt(cr, KEY_ISLAND_CHARGING, if (it) 1 else 0)
                    }
                    StudioNavRow("Battery saver", "Saves power when the battery runs low") {
                        ctx.startActivity(
                            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_ISLAND_CHARGING = "miraclo_island_charging"
    }
}

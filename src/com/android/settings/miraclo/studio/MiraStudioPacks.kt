/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Mira Studio > Home screen & icons: icon packs (home screen and app drawer) and the recents
 * style. Kept in Settings.Secure; the launcher follows them live.
 */
internal object MiraStudioPacks {
    const val KEY_SAME = "miraclo_icon_pack_same"
    const val KEY_HOME = "miraclo_icon_pack_home"
    const val KEY_DRAWER = "miraclo_icon_pack_drawer"
    const val KEY_RECENTS = "miraclo_recents_style"

    /** Intent actions icon packs register for (the launcher looks for the same ones). */
    private val PACK_ACTIONS = arrayOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME",
        "app.lawnchair.icons.THEMED_ICON",
    )

    /** "" (Android's round icons) first, then every installed pack by name. */
    fun packs(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, String>()
        for (action in PACK_ACTIONS) {
            val list = runCatching {
                pm.queryIntentActivities(Intent(action), PackageManager.MATCH_ALL)
            }.getOrNull() ?: continue
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg !in found) found[pkg] = ri.activityInfo.applicationInfo.loadLabel(pm).toString()
            }
        }
        return listOf("" to "Android icons (round)") +
            found.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }
    }

    /** Back to how MIRACLO ships (the page's Reset to default). */
    fun reset(context: Context) {
        val cr = context.contentResolver
        Settings.Secure.putInt(cr, KEY_SAME, 1)
        Settings.Secure.putString(cr, KEY_HOME, "")
        Settings.Secure.putString(cr, KEY_DRAWER, "")
        Settings.Secure.putString(cr, KEY_RECENTS, "default")
    }
}

/** The icon pack and recents rows, inside the "More" card of Home screen & icons. */
@Composable
internal fun MiraStudioPacksRows() {
    val ctx = LocalContext.current
    val cr = ctx.contentResolver
    val packs = remember { MiraStudioPacks.packs(ctx) }
    var same by remember {
        mutableStateOf(runCatching { Settings.Secure.getInt(cr, MiraStudioPacks.KEY_SAME, 1) }.getOrDefault(1) != 0)
    }
    var home by remember { mutableStateOf(Settings.Secure.getString(cr, MiraStudioPacks.KEY_HOME).orEmpty()) }
    var drawer by remember { mutableStateOf(Settings.Secure.getString(cr, MiraStudioPacks.KEY_DRAWER).orEmpty()) }
    var recents by remember {
        mutableStateOf(Settings.Secure.getString(cr, MiraStudioPacks.KEY_RECENTS) ?: "default")
    }
    StudioDropdownRow(
        if (same) "Icon pack" else "Icon pack · Home screen",
        "Android's round icons, Mira icons or a pack you installed",
        packs,
        home,
    ) {
        home = it
        Settings.Secure.putString(cr, MiraStudioPacks.KEY_HOME, it)
    }
    StudioSwitchRow("Same icon pack in the app drawer", null, same) {
        same = it
        Settings.Secure.putInt(cr, MiraStudioPacks.KEY_SAME, if (it) 1 else 0)
    }
    if (!same) {
        StudioDropdownRow("Icon pack · App drawer", null, packs, drawer) {
            drawer = it
            Settings.Secure.putString(cr, MiraStudioPacks.KEY_DRAWER, it)
        }
    }
    StudioDropdownRow(
        "Recents style",
        "How recent apps are laid out",
        listOf("coloros" to "ColorOS stack", "default" to "Default"),
        recents,
    ) {
        recents = it
        Settings.Secure.putString(cr, MiraStudioPacks.KEY_RECENTS, it)
    }
}

/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.AttributeSet
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.android.settings.spa.preference.ComposePreference
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mira Studio › Home screen › Launcher: home screen labels and notification dots, the page
 * transition, home screen gestures, hidden apps, layout lock and folder blur. The launcher reads
 * every value from Settings.Secure and applies it live.
 */
class MiraStudioLauncherFeaturesPreference @JvmOverloads constructor(
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

    private class App(val pkg: String, val label: String, val icon: ImageBitmap)

    @Composable
    private fun Page() {
        val ctx = LocalContext.current
        val cr = ctx.contentResolver
        val cs = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        fun get(key: String, def: Int) = Settings.Secure.getInt(cr, key, def)

        var labels by remember { mutableStateOf(get(KEY_LABELS, 1) != 0) }
        var dots by remember { mutableStateOf(get(Settings.Secure.NOTIFICATION_BADGING, 1) != 0) }
        var transition by remember { mutableIntStateOf(get(KEY_TRANSITION, 0).coerceIn(0, 3)) }
        var lock by remember { mutableStateOf(get(KEY_LAYOUT_LOCK, 0) != 0) }
        var blur by remember { mutableStateOf(get(KEY_FOLDER_BLUR, 0) != 0) }
        val gestures = remember {
            GESTURES.map { g ->
                val v =
                    if (g.key == KEY_SWIPE_DOWN && get(KEY_SWIPE_DOWN, -1) < 0) {
                        // Not chosen here yet: Home search's own swipe switch decides.
                        if (get(KEY_SEARCH_STYLE, 0) != 0 && get(KEY_SEARCH_SWIPE, 0) != 0) 1 else 0
                    } else {
                        get(g.key, 0)
                    }
                mutableIntStateOf(v.coerceIn(0, g.values.size - 1))
            }
        }
        var hidden by remember {
            mutableStateOf(
                (Settings.Secure.getString(cr, KEY_HIDDEN) ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            )
        }
        var showHidden by remember { mutableStateOf(false) }
        val apps by produceState(initialValue = emptyList<App>()) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }

        // Preview: page flip with the chosen transition.
        val flip = remember { Animatable(0f) }
        var target by remember { mutableFloatStateOf(0f) }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioCard {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PreviewPhone(apps, labels, dots, transition, flip.value) {
                        target = if (target > 0.5f) 0f else 1f
                        scope.launch { flip.animateTo(target, tween(520, easing = FastOutSlowInEasing)) }
                    }
                    Text(
                        "Tap the phone to swipe to the next page",
                        color = cs.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }

            // Home screen.
            StudioCard {
                Column {
                    StudioSwitchRow("App labels", "Names under home screen icons", labels) {
                        labels = it
                        Settings.Secure.putInt(cr, KEY_LABELS, if (it) 1 else 0)
                    }
                    StudioSwitchRow("Notification dots", "A dot on apps with new notifications", dots) {
                        dots = it
                        Settings.Secure.putInt(cr, Settings.Secure.NOTIFICATION_BADGING, if (it) 1 else 0)
                    }
                    StudioNavRow("Icon pack", "Android's round icons, Mira icons or a pack you installed") {
                        ctx.startActivity(
                            Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                                .setPackage("com.android.launcher3")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }

            // Page transition.
            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
                    Text("Page transition", color = cs.onSurface, fontSize = 16.sp)
                    Text("When you swipe between home screen pages", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    StudioSegmented(TRANSITIONS.mapIndexed { i, l -> i.toString() to l }, transition.toString()) {
                        transition = it.toInt()
                        Settings.Secure.putInt(cr, KEY_TRANSITION, transition)
                        target = 1f
                        scope.launch {
                            flip.snapTo(0f)
                            flip.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                        }
                    }
                }
            }

            // Gestures: each opens its list of choices.
            StudioCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("Gestures", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                    GESTURES.forEachIndexed { i, g ->
                        val state = gestures[i]
                        StudioDropdownRow(
                            g.title,
                            g.summary,
                            g.values.mapIndexed { k, label -> k.toString() to label },
                            state.intValue.toString(),
                        ) { pick ->
                            state.intValue = pick.toInt()
                            Settings.Secure.putInt(cr, g.key, state.intValue)
                            if (g.key == KEY_SWIPE_DOWN) {
                                Settings.Secure.putInt(cr, KEY_SEARCH_SWIPE, if (state.intValue == 1) 1 else 0)
                            }
                        }
                    }
                }
            }

            // Hidden apps.
            StudioCard {
                Column(Modifier.padding(bottom = if (showHidden) 8.dp else 0.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showHidden = !showHidden }.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Hidden apps", color = cs.onSurface, fontSize = 16.sp)
                            Text(
                                if (hidden.isEmpty()) "None. Hidden apps stay out of the app drawer and search"
                                else "${hidden.size} hidden from the app drawer and search",
                                color = cs.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text(if (showHidden) "Done" else "Choose", color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    if (showHidden) {
                        if (apps.isEmpty()) {
                            Text("Loading apps…", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        }
                        apps.forEach { app ->
                            val on = app.pkg in hidden
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        hidden = if (on) hidden - app.pkg else hidden + app.pkg
                                        Settings.Secure.putString(cr, KEY_HIDDEN, hidden.sorted().joinToString(","))
                                    }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(app.icon, null, Modifier.size(36.dp))
                                Text(app.label, color = cs.onSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp))
                                Switch(checked = on, onCheckedChange = null)
                            }
                        }
                    }
                }
            }

            // More.
            StudioCard {
                Column {
                    StudioSwitchRow("Lock home screen layout", "Stops icons and widgets being moved by accident", lock) {
                        lock = it
                        Settings.Secure.putInt(cr, KEY_LAYOUT_LOCK, if (it) 1 else 0)
                    }
                    StudioSwitchRow("Blur behind folders", "Open folders blur the wallpaper", blur) {
                        blur = it
                        Settings.Secure.putInt(cr, KEY_FOLDER_BLUR, if (it) 1 else 0)
                    }
                }
            }
        }
    }

    /** Two home screen pages; [t] 0..1 swipes from the first to the second with [transition]. */
    @Composable
    private fun PreviewPhone(apps: List<App>, labels: Boolean, dots: Boolean, transition: Int, t: Float, onTap: () -> Unit) {
        val w = 196.dp
        val h = 400.dp
        Box(
            Modifier.width(w).height(h)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2B4A7E), Color(0xFF151B2B))))
                .clickable(onClick = onTap)
        ) {
            for (page in 0..1) {
                // Scroll progress as the launcher computes it: positive while left of centre.
                val p = if (page == 0) t else t - 1f
                val a = abs(p).coerceAtMost(1f)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val width = size.width
                        var tx = -p * width
                        var rot = 0f
                        var sc = 1f
                        var al = 1f
                        var origin = TransformOrigin.Center
                        if (p != 0f) {
                            when (transition) {
                                1 -> {
                                    rot = -70f * p
                                    origin = TransformOrigin(if (p > 0f) 1f else 0f, 0.5f)
                                    cameraDistance = 12f
                                }
                                2 -> {
                                    sc = 1f - 0.18f * a
                                    al = 1f - 0.9f * a
                                }
                                3 -> {
                                    tx = 0f
                                    al = 1f - a
                                }
                            }
                        }
                        translationX = tx
                        rotationY = rot
                        scaleX = sc
                        scaleY = sc
                        alpha = if (a >= 1f && transition != 0) 0f else al
                        transformOrigin = origin
                    }
                ) {
                    if (page == 0) {
                        Column(Modifier.padding(start = 16.dp, top = 34.dp)) {
                            Text("12:45", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                            Text("Tuesday, 3 November", color = Color(0xCCFFFFFF), fontSize = 10.sp)
                        }
                        AppGrid(apps.take(8), labels, dots, top = 170.dp)
                    } else {
                        Box(
                            Modifier.offset(14.dp, 34.dp).width(168.dp).height(92.dp)
                                .background(Color(0x33FFFFFF), RoundedCornerShape(22.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text("29°", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                                Text("Partly cloudy", color = Color(0xCCFFFFFF), fontSize = 10.sp)
                            }
                        }
                        AppGrid(apps.drop(8).take(8), labels, dots, top = 170.dp)
                    }
                }
            }
            // Dock.
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                apps.drop(16).take(4).forEach { Image(it.icon, null, Modifier.size(30.dp)) }
            }
        }
    }

    @Composable
    private fun AppGrid(apps: List<App>, labels: Boolean, dots: Boolean, top: androidx.compose.ui.unit.Dp) {
        Column(Modifier.padding(top = top, start = 10.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(if (labels) 8.dp else 16.dp)) {
            apps.chunked(4).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEachIndexed { c, app ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                Image(app.icon, null, Modifier.size(32.dp))
                                if (dots && (r * 4 + c) % 5 == 1) {
                                    Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(Color(0xFFFFB4AB), CircleShape))
                                }
                            }
                            if (labels) {
                                Text(app.label, color = Color.White, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp))
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    private class Gesture(val key: String, val title: String, val summary: String, val values: List<String>)

    private companion object {
        const val KEY_LABELS = "miraclo_home_labels"
        const val KEY_TRANSITION = "miraclo_page_transition"
        const val KEY_SWIPE_DOWN = "miraclo_gesture_swipe_down"
        const val KEY_LAYOUT_LOCK = "miraclo_layout_lock"
        const val KEY_FOLDER_BLUR = "miraclo_folder_blur"
        const val KEY_HIDDEN = "miraclo_hidden_apps"
        const val KEY_SEARCH_STYLE = "miraclo_home_search"
        const val KEY_SEARCH_SWIPE = "miraclo_home_search_swipe"

        val TRANSITIONS = listOf("Slide", "Cube", "Depth", "Fade")

        val GESTURES =
            listOf(
                Gesture("miraclo_gesture_double_tap", "Double-tap", "On empty home screen space", listOf("Nothing", "Lock screen")),
                Gesture(KEY_SWIPE_DOWN, "Swipe down", "From the middle of the home screen", listOf("Notifications", "Search", "Quick Settings")),
                Gesture("miraclo_gesture_swipe_up", "Swipe up", "On the home screen", listOf("App drawer", "Search")),
                Gesture("miraclo_gesture_pinch", "Pinch in", "Two fingers on the home screen", listOf("Nothing", "Overview", "Edit home screen")),
            )

        fun loadApps(context: Context): List<App> {
            val pm = context.packageManager
            return runCatching {
                    pm.queryIntentActivities(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                            PackageManager.MATCH_DEFAULT_ONLY,
                        )
                        .distinctBy { it.activityInfo.packageName }
                        .map {
                            App(
                                it.activityInfo.packageName,
                                it.loadLabel(pm).toString(),
                                it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
                            )
                        }
                        .sortedBy { it.label.lowercase() }
                }
                .getOrNull()
                .orEmpty()
        }
    }
}

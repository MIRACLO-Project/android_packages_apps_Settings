/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
import com.android.settings.R
import com.android.settings.spa.preference.ComposePreference
import androidx.preference.PreferenceViewHolder

/**
 * Talks to the MIRACLO launcher through its customisation provider (the same one the wallpaper
 * picker uses): icon shapes, grid and themed icons.
 */
internal class MiraLauncherBridge(private val context: Context) {
    private fun uri(path: String) = Uri.parse("content://$AUTHORITY/$path")

    class Shape(val key: String, val title: String, val path: String, val selected: Boolean)

    class Grid(val name: String, val cols: Int, val rows: Int, val selected: Boolean)

    fun shapes(): List<Shape> =
        runCatching {
                context.contentResolver.query(uri("shape_options"), null, null, null, null)?.use { c ->
                    val out = mutableListOf<Shape>()
                    while (c.moveToNext()) {
                        out +=
                            Shape(
                                c.getString(c.getColumnIndexOrThrow("shape_key")),
                                c.getString(c.getColumnIndexOrThrow("shape_title")),
                                c.getString(c.getColumnIndexOrThrow("path")),
                                c.getString(c.getColumnIndexOrThrow("is_default")).let {
                                    it == "1" || it.equals("true", true)
                                },
                            )
                    }
                    out
                }
            }
            .getOrNull()
            .orEmpty()

    fun setShape(key: String) {
        runCatching {
            context.contentResolver.update(
                uri("shape"), ContentValues().apply { put("shape_key", key) }, null, null)
        }
    }

    fun grids(): List<Grid> =
        runCatching {
                context.contentResolver.query(uri("list_options"), null, null, null, null)?.use { c ->
                    val out = mutableListOf<Grid>()
                    while (c.moveToNext()) {
                        out +=
                            Grid(
                                c.getString(c.getColumnIndexOrThrow("name")),
                                c.getInt(c.getColumnIndexOrThrow("cols")),
                                c.getInt(c.getColumnIndexOrThrow("rows")),
                                c.getString(c.getColumnIndexOrThrow("is_default")).let {
                                    it == "1" || it.equals("true", true)
                                },
                            )
                    }
                    out
                }
            }
            .getOrNull()
            .orEmpty()

    fun setGrid(name: String) {
        runCatching {
            context.contentResolver.update(
                uri("default_grid"), ContentValues().apply { put("name", name) }, null, null)
        }
    }

    fun themed(): Boolean =
        runCatching {
                context.contentResolver.query(uri("get_icon_themed"), null, null, null, null)?.use {
                    it.moveToFirst() && it.getString(0).let { v -> v == "1" || v.equals("true", true) }
                }
            }
            .getOrNull() ?: false

    fun setThemed(on: Boolean) {
        runCatching {
            context.contentResolver.update(
                uri("set_icon_themed"),
                ContentValues().apply { put("boolean_value", if (on) 1 else 0) },
                null,
                null,
            )
        }
    }

    companion object {
        const val AUTHORITY = "com.android.launcher3.grid_control"
        const val KEY_THEMED_DRAWER = "miraclo_themed_icons_drawer"
        const val KEY_ICON_SCALE = "miraclo_icon_scale"
        /** The app drawer's own shape key; empty = same as the home screen. */
        const val KEY_DRAWER_SHAPE = "miraclo_icon_shape_drawer"
        /** The grids Mira Studio offers, as columns x rows, with the launcher grid behind each. */
        val GRIDS =
            listOf(
                Triple(3, 3, "3_by_3"),
                Triple(4, 4, "4_by_4"),
                Triple(4, 5, "miraclo_4_by_5"),
                Triple(4, 6, "miraclo_4_by_6"),
                Triple(5, 5, "5_by_5"),
                Triple(5, 6, "miraclo_5_by_6"),
                Triple(6, 5, "miraclo_6_by_5"),
                Triple(6, 6, "miraclo_6_by_6"),
            )
    }
}

/** A launcher shape path (100 x 100) as a Compose shape. */
internal fun miraLauncherShape(path: String): Shape =
    GenericShape { size, _ ->
        val p = runCatching { PathParser.createPathFromPathData(path) }.getOrNull()
        if (p != null) {
            val m = android.graphics.Matrix().apply { setScale(size.width / 100f, size.height / 100f) }
            p.transform(m)
            addPath(p.asComposePath())
        } else {
            addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        }
    }

/** Rounded card used by the Mira Studio pages (Material 3 surface container). */
@Composable
internal fun StudioCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(28.dp))
    ) {
        content()
    }
}

@Composable
internal fun StudioSegmented(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(cs.surface, RoundedCornerShape(26.dp)).padding(4.dp)
    ) {
        options.forEach { (key, label) ->
            val sel = key == selected
            Box(
                Modifier.weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (sel) cs.primary else Color.Transparent)
                    .clickable { onPick(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) cs.onPrimary else cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun StudioSwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = cs.onSurface, fontSize = 16.sp)
            if (summary != null) Text(summary, color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun StudioNavRow(title: String, summary: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, color = cs.onSurface, fontSize = 16.sp)
        Text(summary, color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * A choice that opens a list: the title and summary on the left, the current value on the right
 * with a small arrow; a tap opens the options under it, the current one ticked. Used for every
 * "pick one" setting in Mira Studio.
 */
@Composable
internal fun StudioDropdownRow(
    title: String,
    summary: String?,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = cs.onSurface, fontSize = 16.sp)
            if (summary != null) Text(summary, color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current, color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                Canvas(Modifier.padding(start = 6.dp).size(10.dp, 6.dp)) {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f); lineTo(size.width / 2f, size.height); lineTo(size.width, 0f)
                    }
                    drawPath(p, cs.primary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
            ) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = cs.onSurface, fontSize = 15.sp) },
                        trailingIcon = {
                            if (key == selected) Text("✓", color = cs.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        },
                        onClick = {
                            open = false
                            onPick(key)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The small reset button beside a slider's value: back to the value MIRACLO ships with. It only
 * shows (fading in) once the value differs from the default.
 */
@Composable
internal fun StudioResetIcon(visible: Boolean, onReset: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.6f),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.6f),
    ) {
        Box(
            Modifier.padding(start = 6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Reset to default") {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    onReset()
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                )
                // a circle turning back, with its arrow head
                drawArc(cs.primary, startAngle = -135f, sweepAngle = 300f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.12f, w * 0.12f),
                    size = androidx.compose.ui.geometry.Size(w * 0.76f, w * 0.76f), style = stroke)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.10f, w * 0.10f)
                    lineTo(w * 0.18f, w * 0.34f)
                    lineTo(w * 0.42f, w * 0.27f)
                }
                drawPath(p, cs.primary, style = stroke)
            }
        }
    }
}

/** One option of a list where exactly one is chosen (radio button on the right). */
@Composable
internal fun StudioRadioRow(title: String, summary: String?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = cs.onSurface, fontSize = 16.sp)
            if (summary != null) Text(summary, color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

/**
 * Mira Studio pages draw their own cards; this drops the rounded group background Settings
 * puts behind every row, so a page never shows a card inside a card.
 */
internal fun miraFlat(holder: PreferenceViewHolder) {
    val v = holder.itemView
    v.background = null
    // Settings sets its background after the row is bound; clear it once that is done.
    v.post { v.background = null }
}

/** The apps shown in the home screen preview: the real apps, as the launcher draws them. */
private fun previewApps(context: Context, count: Int): List<MiraIcon> = MiraIcons.load(context, count)

/**
 * Mira Studio > Home screen & icons: live preview (home screen and app drawer), icon shape,
 * app grid, themed icons with their scope, and the rows for home search, icon packs and recents.
 */
class MiraStudioHomeIconsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { HomeIcons() }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        miraFlat(holder)
    }

    @Composable
    private fun HomeIcons() {
        val ctx = LocalContext.current
        val bridge = remember { MiraLauncherBridge(ctx) }
        val shapes = remember { bridge.shapes() }
        var shapeKey by remember { mutableStateOf(shapes.firstOrNull { it.selected }?.key ?: "circle") }
        val launcherGrids = remember { bridge.grids() }
        var grid by remember {
            mutableStateOf(
                launcherGrids.firstOrNull { it.selected }?.let { g -> "${g.cols}x${g.rows}" } ?: "5x5"
            )
        }
        var themed by remember { mutableStateOf(bridge.themed()) }
        var drawer by remember {
            mutableStateOf(Settings.Secure.getInt(ctx.contentResolver, MiraLauncherBridge.KEY_THEMED_DRAWER, 0) != 0)
        }
        var tab by remember { mutableStateOf("home") }
        var iconScale by remember {
            mutableStateOf(Settings.Secure.getInt(ctx.contentResolver, MiraLauncherBridge.KEY_ICON_SCALE, 100).coerceIn(80, 120))
        }
        val cs = MaterialTheme.colorScheme
        val (cols, rows) = grid.split("x").map { it.toInt() }
        var drawerShape by remember {
            mutableStateOf(Settings.Secure.getString(ctx.contentResolver, MiraLauncherBridge.KEY_DRAWER_SHAPE).orEmpty())
        }
        // The App drawer tab shows (and sets) the drawer's own shape; empty = same as home.
        val drawerSame = drawerShape.isEmpty()
        val shownKey = if (tab == "drawer" && !drawerSame) drawerShape else shapeKey
        val shapePath = shapes.firstOrNull { it.key == shownKey }?.path
        val iconShape = remember(shapePath) { shapePath?.let { miraLauncherShape(it) } ?: CircleShape }
        val apps = remember { previewApps(ctx, 24) }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Live preview.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(236.dp, 500.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer)))
                        .border(6.dp, cs.surfaceContainerHigh, RoundedCornerShape(32.dp))
                ) {
                    val iconSize = (minOf(48f, (216f / cols) * 0.66f) * iconScale / 100f).dp
                    val showThemed = themed && (tab == "home" || drawer)
                    if (tab == "home") {
                        Column(Modifier.padding(start = 16.dp, top = 22.dp)) {
                            Text("3:16", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium)
                            Text("Mon, Nov 2", color = Color.White, fontSize = 11.sp)
                        }
                        PreviewGrid(apps.drop(cols).take(cols * (rows - 1)), cols, iconSize, iconShape, showThemed, Modifier.padding(start = 10.dp, end = 10.dp, top = 104.dp))
                        PreviewGrid(apps.take(cols), cols, iconSize, iconShape, showThemed, Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 54.dp), labels = false)
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 16.dp).fillMaxWidth().height(30.dp)
                                .background(cs.surfaceContainerLowest.copy(alpha = 0.85f), RoundedCornerShape(15.dp))
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(cs.surface.copy(alpha = 0.94f)))
                        Box(
                            Modifier.padding(start = 12.dp, end = 12.dp, top = 30.dp).fillMaxWidth().height(30.dp)
                                .background(cs.surfaceContainerHighest, RoundedCornerShape(15.dp))
                        )
                        PreviewGrid(apps.take(cols * 5), cols, iconSize, iconShape, showThemed, Modifier.padding(start = 10.dp, end = 10.dp, top = 78.dp), dark = true)
                    }
                }
                Box(Modifier.width(260.dp)) {
                    StudioSegmented(listOf("home" to "Home screen", "drawer" to "App drawer"), tab) { tab = it }
                }
            }

            // Icon shape.
            StudioCard {
                Column(Modifier.padding(vertical = 18.dp)) {
                    Text(if (tab == "drawer") "Icon shape · App drawer" else "Icon shape · Home screen", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
                    Text(if (tab == "drawer" && drawerSame) "Same as the home screen" else shapes.firstOrNull { it.key == shownKey }?.title ?: "", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (tab == "drawer") {
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                                    .background(if (drawerSame) cs.primaryContainer else cs.surface)
                                    .clickable {
                                        drawerShape = ""
                                        Settings.Secure.putString(ctx.contentResolver, MiraLauncherBridge.KEY_DRAWER_SHAPE, "")
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Same as\nhome", color = if (drawerSame) cs.onPrimaryContainer else cs.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
                            }
                        }
                        shapes.forEach { s ->
                            val sel = s.key == shownKey && !(tab == "drawer" && drawerSame)
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                                    .background(if (sel) cs.primaryContainer else cs.surface)
                                    .clickable {
                                        if (tab == "drawer") {
                                            drawerShape = s.key
                                            Settings.Secure.putString(ctx.contentResolver, MiraLauncherBridge.KEY_DRAWER_SHAPE, s.key)
                                        } else {
                                            shapeKey = s.key
                                            bridge.setShape(s.key)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                val shape = remember(s.path) { miraLauncherShape(s.path) }
                                Box(Modifier.size(40.dp).clip(shape).background(if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant))
                            }
                        }
                    }
                }
            }

            // Icon size.
            StudioCard {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Icon size", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("$iconScale%", color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        StudioResetIcon(iconScale != 100) {
                            iconScale = 100
                            Settings.Secure.putInt(ctx.contentResolver, MiraLauncherBridge.KEY_ICON_SCALE, 100)
                        }
                    }
                    Text("Home screen, dock and app drawer", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    androidx.compose.material3.Slider(
                        value = iconScale.toFloat(),
                        onValueChange = { iconScale = (Math.round(it / 5f) * 5).coerceIn(80, 120) },
                        onValueChangeFinished = {
                            Settings.Secure.putInt(ctx.contentResolver, MiraLauncherBridge.KEY_ICON_SCALE, iconScale)
                        },
                        valueRange = 80f..120f,
                        steps = 7,
                    )
                }
            }

            // App grid.
            StudioCard {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
                    Text("App grid", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                    Text("$cols columns, $rows rows. The app drawer uses $cols columns too.", color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 12.dp))
                    MiraLauncherBridge.GRIDS.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (c, r, name) ->
                                val key = "${c}x$r"
                                val sel = key == grid
                                Column(
                                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(18.dp))
                                        .background(if (sel) cs.primaryContainer else cs.surface)
                                        .clickable {
                                            grid = key
                                            bridge.setGrid(name)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    GridDots(c, r, if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant)
                                    Text("$c × $r", color = if (sel) cs.onPrimaryContainer else cs.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Themed icons.
            StudioCard {
                Column(Modifier.padding(bottom = if (themed) 16.dp else 0.dp)) {
                    StudioSwitchRow("Themed icons", "Icons take the colours of your wallpaper and theme", themed) {
                        themed = it
                        bridge.setThemed(it)
                    }
                    if (themed) {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            StudioSegmented(listOf("home" to "Home screen", "both" to "Home screen & app drawer"), if (drawer) "both" else "home") {
                                drawer = it == "both"
                                Settings.Secure.putInt(ctx.contentResolver, MiraLauncherBridge.KEY_THEMED_DRAWER, if (drawer) 1 else 0)
                                // Reapplies themed icons so the launcher redraws every icon.
                                bridge.setThemed(true)
                            }
                        }
                    }
                }
            }

            // More.
            StudioCard {
                Column {
                    StudioNavRow("Launcher", "Labels, page transition, gestures, hidden apps") {
                        ctx.openStudioPage(MiraStudioLauncherFragment::class.java.name, R.string.miraclo_studio_launcher_title)
                    }
                    StudioNavRow("Home search", "Search pill or bar, and what it searches") {
                        ctx.openStudioPage(MiraStudioHomeSearchFragment::class.java.name, R.string.miraclo_studio_home_search_title)
                    }
                    MiraStudioPacksRows()
                }
            }
        }
    }

    @Composable
    private fun GridDots(cols: Int, rows: Int, color: Color) {
        Canvas(Modifier.size((cols * 5).dp, (rows * 4).dp)) {
            val dx = size.width / cols
            val dy = size.height / rows
            for (r in 0 until rows) for (c in 0 until cols) {
                drawCircle(color, radius = minOf(dx, dy) * 0.32f, center = androidx.compose.ui.geometry.Offset(dx * (c + 0.5f), dy * (r + 0.5f)))
            }
        }
    }

    @Composable
    private fun PreviewGrid(
        apps: List<MiraIcon>,
        cols: Int,
        iconSize: Dp,
        shape: Shape,
        themed: Boolean,
        modifier: Modifier,
        labels: Boolean = true,
        dark: Boolean = false,
    ) {
        val cs = MaterialTheme.colorScheme
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        val name = app.label
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val full = remember(app) { app.full.asImageBitmap() }
                            val mono = remember(app) { app.mono?.asImageBitmap() }
                            Box(
                                Modifier.size(iconSize).clip(shape).background(if (themed) cs.secondaryContainer else Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (themed) {
                                    // Themed: the app's one-colour layer, or its icon shrunk and tinted.
                                    Image(
                                        mono ?: full,
                                        null,
                                        modifier = Modifier.size(if (mono != null) iconSize else iconSize * 0.62f),
                                        colorFilter = ColorFilter.tint(cs.onSecondaryContainer),
                                    )
                                } else {
                                    Image(full, null, modifier = Modifier.size(iconSize))
                                }
                            }
                            if (labels) {
                                Text(name, color = if (dark) cs.onSurface else Color.White, fontSize = if (cols >= 6) 7.sp else 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Mira Studio > Home screen & icons > Home search: on/off, pill or bar, what it searches, and
 * swipe down to search.
 */
class MiraStudioHomeSearchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {
    init {
        setContent { HomeSearch() }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        miraFlat(holder)
    }

    /** Apps that can take the search: web search, browsers and assistants first, then the rest. */
    private fun engines(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val first = LinkedHashMap<String, String>()
        fun add(i: Intent) {
            runCatching {
                pm.queryIntentActivities(i, 0).forEach { ri ->
                    val pkg = ri.activityInfo.packageName
                    if (pkg !in first && pkg != ctx.packageName) first[pkg] = ri.loadLabel(pm).toString()
                }
            }
        }
        add(Intent(Intent.ACTION_WEB_SEARCH))
        add(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE))
        add(Intent(Intent.ACTION_ASSIST))
        for (pkg in ASSISTANTS) {
            if (pkg in first) continue
            runCatching { first[pkg] = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }
        }
        val rest =
            runCatching {
                    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                        .filter { it.first !in first && it.first != ctx.packageName }
                        .distinctBy { it.first }
                        .sortedBy { it.second.lowercase() }
                }
                .getOrDefault(emptyList())
        return listOf("" to "Mira search") +
            first.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value } + rest
    }

    @Composable
    private fun HomeSearch() {
        val ctx = LocalContext.current
        val cr = ctx.contentResolver
        val cs = MaterialTheme.colorScheme
        var style by remember { mutableStateOf(Settings.Secure.getInt(cr, KEY_STYLE, 0)) }
        var lastStyle by remember { mutableStateOf(if (style == 0) 1 else style) }
        var scopes by remember {
            mutableStateOf((Settings.Secure.getString(cr, KEY_SCOPES) ?: DEFAULT_SCOPES).split(",").filter { it.isNotBlank() }.toSet())
        }
        var swipe by remember { mutableStateOf(Settings.Secure.getInt(cr, KEY_SWIPE, 0) != 0) }
        var engine by remember { mutableStateOf(Settings.Secure.getString(cr, KEY_APP).orEmpty()) }
        val apps = remember { engines(ctx) }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioCard {
                StudioSwitchRow("Search on home screen", "One tap to search, right from the home screen", style != 0) { on ->
                    style = if (on) lastStyle else 0
                    Settings.Secure.putInt(cr, KEY_STYLE, style)
                }
            }
            if (style != 0) {
                StudioCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Style", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(1 to "In the page dots", 2 to "Bar below the dock").forEach { (v, label) ->
                                val sel = style == v
                                Column(
                                    Modifier.weight(1f).height(118.dp).clip(RoundedCornerShape(20.dp))
                                        .background(if (sel) cs.primaryContainer else cs.surface)
                                        .clickable {
                                            style = v
                                            lastStyle = v
                                            Settings.Secure.putInt(cr, KEY_STYLE, v)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
                                ) {
                                    val ink = if (sel) cs.onPrimaryContainer else cs.outline
                                    if (v == 1) {
                                        // the pill between the apps and the dock
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            repeat(4) { Box(Modifier.size(12.dp).background(ink.copy(alpha = 0.5f), CircleShape)) }
                                        }
                                        Box(Modifier.size(54.dp, 14.dp).background(ink, RoundedCornerShape(7.dp)))
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            repeat(4) { Box(Modifier.size(12.dp).background(ink, CircleShape)) }
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            repeat(4) { Box(Modifier.size(12.dp).background(ink, CircleShape)) }
                                        }
                                        Box(Modifier.size(104.dp, 14.dp).background(ink, RoundedCornerShape(7.dp)))
                                    }
                                    Text(label, color = if (sel) cs.onPrimaryContainer else cs.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        if (style == 1) {
                            Text(
                                "The page dots turn into a search pill when the home screen is still, and back into dots while you swipe between pages.",
                                color = cs.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
                            )
                        }
                    }
                }
                StudioCard {
                    StudioDropdownRow(
                        "Search with",
                        if (engine.isEmpty()) "Apps, contacts, settings, files and the web" else "Opens this app to search",
                        apps,
                        engine,
                    ) {
                        engine = it
                        Settings.Secure.putString(cr, KEY_APP, it)
                    }
                }
                if (engine.isEmpty()) {
                    StudioCard {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)) {
                            Text("Search in", color = cs.onSurface, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                            listOf("apps" to "Apps", "contacts" to "Contacts", "settings" to "Settings", "files" to "Files", "messages" to "Messages", "web" to "Web")
                                .chunked(3)
                                .forEach { row ->
                                    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (k, label) ->
                                            val sel = k in scopes
                                            Box(
                                                Modifier.height(36.dp).clip(RoundedCornerShape(10.dp))
                                                    .background(if (sel) cs.secondaryContainer else Color.Transparent)
                                                    .border(1.dp, if (sel) cs.secondaryContainer else cs.outline, RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        scopes = if (sel) scopes - k else scopes + k
                                                        Settings.Secure.putString(cr, KEY_SCOPES, scopes.joinToString(","))
                                                    }
                                                    .padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text((if (sel) "✓ " else "") + label, color = if (sel) cs.onSecondaryContainer else cs.onSurfaceVariant, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
                StudioCard {
                    StudioSwitchRow("Swipe down to search", "Swipe down on empty home screen space", swipe) {
                        swipe = it
                        Settings.Secure.putInt(cr, KEY_SWIPE, if (it) 1 else 0)
                        // Same choice as Launcher › Gestures › Swipe down
                        Settings.Secure.putInt(cr, "miraclo_gesture_swipe_down", if (it) 1 else 0)
                    }
                }
            }
            Text(
                "With themed icons on, the search pill and bar take the theme's colours too.",
                color = cs.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }

    private companion object {
        const val KEY_STYLE = "miraclo_home_search"
        const val KEY_SCOPES = "miraclo_home_search_scopes"
        const val KEY_SWIPE = "miraclo_home_search_swipe"
        const val DEFAULT_SCOPES = "apps,contacts,settings,files,web"
        /** The app the home search opens; empty = Mira search. */
        const val KEY_APP = "miraclo_home_search_app"
        val ASSISTANTS =
            listOf(
                "com.google.android.googlequicksearchbox",
                "com.google.android.apps.bard",
                "com.openai.chatgpt",
                "ai.perplexity.app.android",
                "com.microsoft.copilot",
            )
    }
}

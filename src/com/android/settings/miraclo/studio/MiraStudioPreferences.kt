/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.app.settings.SettingsEnums
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settings.R
import com.android.settings.core.SubSettingLauncher
import com.android.settings.display.MiraCLOLiquidGlassSettings
import com.android.settings.notification.MiraCLOIslandSettings

/** Opens one Mira Studio page. */
internal fun Context.openStudioPage(fragment: String, @StringRes title: Int) {
    SubSettingLauncher(this)
        .setDestination(fragment)
        .setTitleRes(title)
        .setSourceMetricsCategory(SettingsEnums.DISPLAY)
        .launch()
}

/** Settings home: the Mira Studio card under the search bar. */
class MiraStudioBannerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.miraclo_studio_banner
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        (holder.findViewById(R.id.miraclo_banner_art1) as? MiraStudioArtView)?.kind =
            MiraStudioArtView.BANNER_ISLAND
        (holder.findViewById(R.id.miraclo_banner_art2) as? MiraStudioArtView)?.kind =
            MiraStudioArtView.BANNER_QS
        (holder.findViewById(R.id.miraclo_banner_art3) as? MiraStudioArtView)?.kind =
            MiraStudioArtView.BANNER_GLASS
        // The whole row is the click target (Settings home opens the fragment and keeps the
        // highlight in two-pane mode); the card only shows the pressed state.
    }
}

/** Mira Studio: the feature cards. */
class MiraStudioCardsPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private class Card(
        val id: Int,
        val art: Int,
        @StringRes val title: Int,
        @StringRes val summary: Int,
        val fragment: String,
    )

    private val cards =
        listOf(
            Card(R.id.miraclo_card_qs, MiraStudioArtView.QS, R.string.miraclo_studio_qs_title,
                R.string.miraclo_studio_qs_summary, MiraStudioQsFragment::class.java.name),
            Card(R.id.miraclo_card_glass, MiraStudioArtView.GLASS, R.string.miraclo_liquid_glass_title,
                R.string.miraclo_studio_glass_summary, MiraCLOLiquidGlassSettings::class.java.name),
            Card(R.id.miraclo_card_island, MiraStudioArtView.ISLAND, R.string.miraclo_island_title,
                R.string.miraclo_studio_island_summary, MiraCLOIslandSettings::class.java.name),
            Card(R.id.miraclo_card_volume, MiraStudioArtView.VOLUME, R.string.miraclo_studio_volume_title,
                R.string.miraclo_studio_volume_summary, MiraStudioVolumeFragment::class.java.name),
            Card(R.id.miraclo_card_shade, MiraStudioArtView.SHADE, R.string.miraclo_studio_shade_title,
                R.string.miraclo_studio_shade_summary, MiraStudioShadeFragment::class.java.name),
            Card(R.id.miraclo_card_home, MiraStudioArtView.HOME, R.string.miraclo_studio_home_title,
                R.string.miraclo_studio_home_summary, MiraStudioHomeFragment::class.java.name),
        )

    init {
        layoutResource = R.layout.miraclo_studio_cards
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        for (card in cards) {
            val view = holder.findViewById(card.id) ?: continue
            (view.findViewById<View>(R.id.miraclo_card_art) as? MiraStudioArtView)?.kind = card.art
            view.findViewById<TextView>(R.id.miraclo_card_title)?.setText(card.title)
            view.findViewById<TextView>(R.id.miraclo_card_summary)?.setText(card.summary)
            view.setOnClickListener { context.openStudioPage(card.fragment, card.title) }
        }
    }
}

/**
 * Mira Studio > Quick Settings: the style picker. Writes Settings.Secure "miraclo_qs_style"
 * (0 = Stock, 1 = ColorOS, 2 = iOS, 4 = One UI).
 */
class MiraStudioQsStylePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    /** Told when the style changes, so the page can show or hide the Stock options. */
    var onStyleChanged: ((Int) -> Unit)? = null

    private class Style(val value: Int, val card: Int, val art: Int, val radio: Int,
        @StringRes val name: Int, val kind: Int)

    private val styles =
        listOf(
            Style(STYLE_COLOROS, R.id.miraclo_qs_card_coloros, R.id.miraclo_qs_art_coloros,
                R.id.miraclo_qs_radio_coloros, R.string.miraclo_qs_style_coloros,
                MiraStudioArtView.THUMB_COLOROS),
            Style(STYLE_ONEUI, R.id.miraclo_qs_card_oneui, R.id.miraclo_qs_art_oneui,
                R.id.miraclo_qs_radio_oneui, R.string.miraclo_qs_style_oneui,
                MiraStudioArtView.THUMB_ONEUI),
            Style(STYLE_IOS, R.id.miraclo_qs_card_ios, R.id.miraclo_qs_art_ios,
                R.id.miraclo_qs_radio_ios, R.string.miraclo_qs_style_ios,
                MiraStudioArtView.THUMB_IOS),
            Style(STYLE_STOCK, R.id.miraclo_qs_card_stock, R.id.miraclo_qs_art_stock,
                R.id.miraclo_qs_radio_stock, R.string.miraclo_qs_style_default,
                MiraStudioArtView.THUMB_STOCK),
        )

    init {
        layoutResource = R.layout.miraclo_studio_qs_styles
        isSelectable = false
    }

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = notifyChanged()
        }

    override fun onAttached() {
        super.onAttached()
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(KEY_ROUND), false, observer)
    }

    override fun onDetached() {
        context.contentResolver.unregisterContentObserver(observer)
        super.onDetached()
    }

    val style: Int
        get() =
            Settings.Secure.getInt(context.contentResolver, KEY_STYLE, STYLE_STOCK).let {
                if (it == STYLE_COLOROS || it == STYLE_IOS || it == STYLE_ONEUI) it else STYLE_STOCK
            }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val current = style
        val round = Settings.Secure.getInt(context.contentResolver, KEY_ROUND, 0) != 0
        for (s in styles) {
            val card = holder.findViewById(s.card) ?: continue
            val selected = s.value == current
            card.setBackgroundResource(
                if (selected) R.drawable.miraclo_studio_card_selected_bg
                else R.drawable.miraclo_studio_card_bg
            )
            (holder.findViewById(s.art) as? MiraStudioArtView)?.let {
                it.kind = s.kind
                it.round = round
            }
            (holder.findViewById(s.radio) as? RadioButton)?.let {
                it.setText(s.name)
                it.isChecked = selected
            }
            card.contentDescription = context.getString(s.name)
            card.isSelected = selected
            card.setOnClickListener {
                Settings.Secure.putInt(context.contentResolver, KEY_STYLE, s.value)
                notifyChanged()
                onStyleChanged?.invoke(s.value)
            }
        }
    }

    companion object {
        const val KEY_STYLE = "miraclo_qs_style"
        const val KEY_ROUND = "miraclo_qs_round_tiles"
        const val STYLE_STOCK = 0
        const val STYLE_COLOROS = 1
        const val STYLE_IOS = 2
        const val STYLE_ONEUI = 4
    }
}

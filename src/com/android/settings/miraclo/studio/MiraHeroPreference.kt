/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/**
 * The live preview at the top of a Mira Studio page (the user's real wallpaper, time, icons,
 * palette, refresh rate or battery). The page is picked by the key: mira_hero_<kind>.
 */
class MiraHeroPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        isSelectable = false
    }

    private fun kind(): Int =
        when (key?.removePrefix("mira_hero_")) {
            "lock" -> MiraPreviewView.LOCK
            "home" -> MiraPreviewView.HOME
            "statusbar" -> MiraPreviewView.STATUS_BAR
            "colours" -> MiraPreviewView.COLOURS
            "display" -> MiraPreviewView.DISPLAY
            "battery" -> MiraPreviewView.BATTERY
            "motion" -> MiraPreviewView.ANIMATIONS
            "island" -> MiraPreviewView.ISLAND
            else -> MiraPreviewView.HERO
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val root = holder.itemView as? ViewGroup ?: return
        val d = context.resources.displayMetrics.density
        root.removeAllViews()
        root.setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
        val frame =
            FrameLayout(context).apply {
                background =
                    GradientDrawable().apply {
                        setColor(MiraInk(context).card)
                        cornerRadius = 28 * d
                    }
                setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                addView(
                    MiraPreviewView(context, kind()),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (150 * d).toInt()),
                )
            }
        root.addView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        miraFlat(holder)
    }
}

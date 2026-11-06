/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo

import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat

/**
 * Switches and lists for Android's own Settings.System and Settings.Global values, used by the
 * Mira Studio pages. The table comes from the key prefix: "system:" or "global:".
 */
private fun table(key: String) = key.substringBefore(':', "secure")

private fun name(key: String) = key.substringAfter(':')

private fun readInt(context: Context, key: String, def: Int): Int =
    try {
        when (table(key)) {
            "system" -> Settings.System.getInt(context.contentResolver, name(key), def)
            "global" -> Settings.Global.getInt(context.contentResolver, name(key), def)
            else -> Settings.Secure.getInt(context.contentResolver, name(key), def)
        }
    } catch (_: Exception) {
        def
    }

private fun writeInt(context: Context, key: String, value: Int) {
    try {
        when (table(key)) {
            "system" -> Settings.System.putInt(context.contentResolver, name(key), value)
            "global" -> Settings.Global.putInt(context.contentResolver, name(key), value)
            else -> Settings.Secure.putInt(context.contentResolver, name(key), value)
        }
    } catch (_: Exception) {}
}

class MiraTableSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwitchPreferenceCompat(context, attrs) {
    init {
        isPersistent = false
    }

    override fun onAttached() {
        super.onAttached()
        isChecked = readInt(context, key, 1) != 0
    }

    override fun onClick() {
        super.onClick()
        writeInt(context, key, if (isChecked) 1 else 0)
    }
}

class MiraTableListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ListPreference(context, attrs) {
    init {
        isPersistent = false
        setOnPreferenceChangeListener { _, v ->
            writeInt(context, key, (v as String).toIntOrNull() ?: 0)
            true
        }
    }

    override fun onAttached() {
        super.onAttached()
        value = readInt(context, key, entryValues?.getOrNull(0)?.toString()?.toIntOrNull() ?: 0).toString()
    }
}

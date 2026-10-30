/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.os.SystemProperties
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.deviceinfo.MiraCLOVersionPreferenceController
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.preference.PreferenceBinding

/**
 * "MiraCLO version" row on the Android version page, e.g. "1.0 (Aurora)". Reads
 * ro.miraclo.version and ro.miraclo.codename.
 */
class MiraCLOAndroidVersionPreference :
    PersistentPreference<String>,
    PreferenceMetadata,
    PreferenceSummaryProvider,
    PreferenceBinding {

    override val key: String
        get() = "miraclo_android_version"

    override val purpose: Int
        get() = R.string.miraclo_android_version_title

    override val title: Int
        get() = R.string.miraclo_android_version_title

    override val indexable
        get() = false

    override val supportsWrite = false

    override val valueType = String::class.javaObjectType

    override fun storage(context: Context): KeyValueStore = createSummaryStorage(context, key)

    override fun getSummary(context: Context): CharSequence? =
        if (SystemProperties.get(MIRACLO_VERSION_PROP).isNotEmpty()) {
            // Same text as Settings > About phone > MiraCLO version, e.g. "1.0 (Aurora)".
            MiraCLOVersionPreferenceController.formatVersion()
        } else {
            context.getString(R.string.device_info_default)
        }

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isCopyingEnabled = true
    }

    override val sensitivityLevel
        get() = SensitivityLevel.NO_SENSITIVITY

    companion object {
        private const val MIRACLO_VERSION_PROP = "ro.miraclo.version"
    }
}

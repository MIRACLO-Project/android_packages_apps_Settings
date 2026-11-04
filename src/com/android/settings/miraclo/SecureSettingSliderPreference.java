/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.SeekBarPreference;

import com.android.settings.R;

/** A SeekBarPreference stored in Settings.Secure (read live by SystemUI). */
public class SecureSettingSliderPreference extends SeekBarPreference {
    public SecureSettingSliderPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public SecureSettingSliderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SecureSettingSliderPreference(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        // Our own row: the theme's slider row has no value view, and androidx needs one.
        setLayoutResource(R.layout.miraclo_seekbar_preference);
        setPreferenceDataStore(new SecureSettingsDataStore(context.getContentResolver()));
    }
}

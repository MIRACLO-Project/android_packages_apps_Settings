/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

/** A list whose chosen entry value lives in Settings.Secure under its key. */
public class SecureSettingListPreference extends ListPreference {

    public SecureSettingListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public SecureSettingListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SecureSettingListPreference(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setPreferenceDataStore(new SecureSettingsDataStore(context.getContentResolver()));
    }
}

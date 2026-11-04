/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.SwitchPreferenceCompat;

/** A switch whose value lives in Settings.Secure under its key (1 = on, 0 = off). */
public class SecureSettingSwitchPreference extends SwitchPreferenceCompat {

    public SecureSettingSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public SecureSettingSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SecureSettingSwitchPreference(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setPreferenceDataStore(new SecureSettingsDataStore(context.getContentResolver()));
    }
}

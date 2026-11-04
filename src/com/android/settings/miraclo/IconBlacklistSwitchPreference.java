/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.preference.SwitchPreferenceCompat;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shows or hides one status bar icon. The key is the icon's slot name (wifi, bluetooth, alarm_clock,
 * ...); hidden slots go into Settings.Secure "icon_blacklist", which SystemUI already honours.
 */
public class IconBlacklistSwitchPreference extends SwitchPreferenceCompat {
    private static final String LIST = "icon_blacklist";

    public IconBlacklistSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    private Set<String> read() {
        String v = Settings.Secure.getString(getContext().getContentResolver(), LIST);
        Set<String> out = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(v)) for (String s : v.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        setChecked(!read().contains(getKey()));
    }

    @Override
    protected void onClick() {
        super.onClick();
        Set<String> hidden = read();
        if (isChecked()) hidden.remove(getKey()); else hidden.add(getKey());
        Settings.Secure.putString(getContext().getContentResolver(), LIST, TextUtils.join(",", hidden));
    }
}

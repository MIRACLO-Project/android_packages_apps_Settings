/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The status bar icon style (Wi-Fi and mobile signal): Android 17's own, or the ColorOS, iOS or
 * One UI overlay. Stored as the theme's SystemUI icon pack (Settings.Secure
 * THEME_CUSTOMIZATION_OVERLAY_PACKAGES), which System UI applies at once.
 */
public class MiraIconStylePreference extends ListPreference {
    static final String CATEGORY = "android.theme.customization.icon_pack.systemui";
    static final String DEFAULT = "default";

    public MiraIconStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
        setValue(read(context));
        setOnPreferenceChangeListener((preference, value) -> {
            write(getContext(), String.valueOf(value));
            return true;
        });
    }

    private static JSONObject json(Context context) {
        try {
            String s = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES);
            return new JSONObject(s != null ? s : "{}");
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static String read(Context context) {
        return json(context).optString(CATEGORY, DEFAULT);
    }

    static void write(Context context, String value) {
        JSONObject j = json(context);
        try {
            if (DEFAULT.equals(value)) {
                j.remove(CATEGORY);
            } else {
                j.put(CATEGORY, value);
            }
            j.put("_applied_timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            return;
        }
        Settings.Secure.putString(context.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, j.toString());
    }
}

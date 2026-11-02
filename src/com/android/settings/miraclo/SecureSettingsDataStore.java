/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.content.ContentResolver;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

/**
 * Keeps a preference's value in Settings.Secure under the preference key, so other parts of the
 * system (SystemUI, the launcher) can read and follow it.
 */
public final class SecureSettingsDataStore extends PreferenceDataStore {

    private final ContentResolver mResolver;

    public SecureSettingsDataStore(ContentResolver resolver) {
        mResolver = resolver;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return Settings.Secure.getInt(mResolver, key, defValue ? 1 : 0) != 0;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        Settings.Secure.putInt(mResolver, key, value ? 1 : 0);
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        final String value = Settings.Secure.getString(mResolver, key);
        return value != null ? value : defValue;
    }

    @Override
    public void putString(String key, @Nullable String value) {
        Settings.Secure.putString(mResolver, key, value);
    }

    @Override
    public int getInt(String key, int defValue) {
        return Settings.Secure.getInt(mResolver, key, defValue);
    }

    @Override
    public void putInt(String key, int value) {
        Settings.Secure.putInt(mResolver, key, value);
    }
}

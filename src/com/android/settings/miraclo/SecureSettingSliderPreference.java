/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;

import com.android.settings.R;

/** A SeekBarPreference stored in Settings.Secure (read live by SystemUI). */
public class SecureSettingSliderPreference extends SeekBarPreference {
    /** The value MIRACLO ships with (android:defaultValue), for the reset button. */
    private Integer mDefault;

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

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        Object v = super.onGetDefaultValue(a, index);
        if (v instanceof Integer) mDefault = (Integer) v;
        return v;
    }

    /** The shipped value, or null when the XML gives none. */
    public Integer getDefaultInt() {
        return mDefault;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View reset = holder.findViewById(R.id.miraclo_reset);
        if (reset == null) return;
        boolean changed = mDefault != null && getValue() != mDefault;
        reset.setVisibility(changed ? View.VISIBLE : View.GONE);
        // The value is saved when the finger lifts; show or hide the button right after.
        View bar = holder.findViewById(R.id.seekbar);
        if (bar != null) {
            bar.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP
                        || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.post(() -> reset.setVisibility(mDefault != null && getValue() != mDefault
                            ? View.VISIBLE : View.GONE));
                }
                return false;
            });
        }
        reset.setOnClickListener(v -> {
            if (mDefault != null && callChangeListener(mDefault)) {
                setValue(mDefault);
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
            }
        });
    }
}

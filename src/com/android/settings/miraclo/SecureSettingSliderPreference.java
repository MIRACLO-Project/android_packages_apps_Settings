/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo;

import android.content.Context;
import android.content.res.TypedArray;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

/**
 * A slider whose value lives in Settings.Secure under its key, with its own layout (the AndroidX
 * SeekBarPreference expects views the Settings theme does not provide). XML: android:max,
 * settings:min, settings:seekBarIncrement, android:defaultValue; the value is shown in dp.
 */
public class SecureSettingSliderPreference extends Preference {

    private final int mMin;
    private final int mMax;
    private final int mStep;
    private int mDefault;

    public SecureSettingSliderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.miraclo_studio_slider);
        setSelectable(false);
        mMax = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "max", 100);
        mMin = attrs.getAttributeIntValue("http://schemas.android.com/apk/res-auto", "min", 0);
        mStep = Math.max(1, attrs.getAttributeIntValue(
                "http://schemas.android.com/apk/res-auto", "seekBarIncrement", 1));
        mDefault = attrs.getAttributeIntValue(
                "http://schemas.android.com/apk/res/android", "defaultValue", mMin);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        mDefault = a.getInt(index, mDefault);
        return mDefault;
    }

    private int read() {
        final int v = Settings.Secure.getInt(getContext().getContentResolver(), getKey(), mDefault);
        return Math.max(mMin, Math.min(mMax, v));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final SeekBar bar = (SeekBar) holder.findViewById(R.id.miraclo_slider);
        final TextView value = (TextView) holder.findViewById(R.id.miraclo_slider_value);
        if (bar == null || value == null) return;
        bar.setOnSeekBarChangeListener(null);
        bar.setMax((mMax - mMin) / mStep);
        bar.setProgress((read() - mMin) / mStep);
        bar.setContentDescription(getTitle());
        value.setText(read() + " dp");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText((mMin + progress * mStep) + " dp");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Settings.Secure.putInt(getContext().getContentResolver(), getKey(),
                        mMin + seekBar.getProgress() * mStep);
            }
        });
    }
}

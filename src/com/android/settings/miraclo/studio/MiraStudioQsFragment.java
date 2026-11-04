/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio;

import android.app.StatusBarManager;
import android.os.Bundle;

import androidx.preference.Preference;

import com.android.settings.R;

/**
 * Mira Studio > Quick Settings: style picker; for Stock, round tiles and tile size; and a
 * shortcut to Edit controls in the shade.
 */
public class MiraStudioQsFragment extends MiraStudioFragments.Page {

    private static final String KEY_STYLES = "miraclo_qs_styles";
    private static final String KEY_ROUND = "miraclo_qs_round_tiles";
    private static final String KEY_SIZE = "miraclo_qs_tile_size";
    private static final String KEY_EDIT = "miraclo_qs_edit_controls";

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.miraclo_studio_qs;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        final MiraStudioQsStylePreference styles = findPreference(KEY_STYLES);
        if (styles != null) {
            styles.setOnStyleChanged(style -> {
                updateStockOptions(style);
                return kotlin.Unit.INSTANCE;
            });
            updateStockOptions(styles.getStyle());
        }
        final Preference edit = findPreference(KEY_EDIT);
        if (edit != null) {
            edit.setOnPreferenceClickListener(p -> {
                final StatusBarManager sbm = getContext().getSystemService(StatusBarManager.class);
                if (sbm != null) sbm.expandSettingsPanel();
                return true;
            });
        }
    }

    private void updateStockOptions(int style) {
        final boolean stock = style == MiraStudioQsStylePreference.STYLE_STOCK;
        final Preference round = findPreference(KEY_ROUND);
        final Preference size = findPreference(KEY_SIZE);
        if (round != null) round.setVisible(stock);
        if (size != null) size.setVisible(stock);
    }
}

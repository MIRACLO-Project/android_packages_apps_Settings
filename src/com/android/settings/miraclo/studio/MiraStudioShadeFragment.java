/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio;

import com.android.settings.R;

/** Mira Studio page backed by R.xml.miraclo_studio_shade. */
public class MiraStudioShadeFragment extends MiraStudioFragments.Page {
    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.miraclo_studio_shade;
    }
}

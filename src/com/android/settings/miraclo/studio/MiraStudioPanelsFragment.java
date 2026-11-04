/*
 * SPDX-FileCopyrightText: MiraCLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio;

import com.android.settings.R;

/** Mira Studio › Panels. */
public class MiraStudioPanelsFragment extends MiraStudioFragments.Page {
    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.miraclo_studio_branch_panels;
    }
}

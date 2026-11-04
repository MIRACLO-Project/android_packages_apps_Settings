/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio;

import android.app.settings.SettingsEnums;

import com.android.settings.dashboard.DashboardFragment;

/** Mira Studio pages. Each one is a small preference screen. */
final class MiraStudioFragments {
    private MiraStudioFragments() {}

    /** Base: every Mira Studio page logs as Display and has a static screen. */
    abstract static class Page extends DashboardFragment {
        @Override
        public int getMetricsCategory() {
            return SettingsEnums.DISPLAY;
        }

        @Override
        protected String getLogTag() {
            return getClass().getSimpleName();
        }
    }
}

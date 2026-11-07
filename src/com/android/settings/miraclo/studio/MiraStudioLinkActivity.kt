/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.app.Activity
import android.os.Bundle
import com.android.settings.R

/**
 * Opens Mira Studio > Home screen & icons from the launcher's Home settings ("Advanced settings").
 * Shows nothing itself.
 */
class MiraStudioLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            openStudioPage(MiraStudioHomeFragment::class.java.name, R.string.miraclo_studio_home_title)
        }
        finish()
    }
}

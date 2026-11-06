/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.miraclo.studio

import android.content.Context

/** Lets the Java reset button reach the launcher settings Mira Studio keeps in the launcher. */
class MiraStudioResetBridge(private val context: Context) {
    fun resetLauncher() {
        val bridge = MiraLauncherBridge(context)
        bridge.setShape("circle")
        bridge.setThemed(false)
    }
}

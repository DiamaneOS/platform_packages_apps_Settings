/*
 * Copyright (C) 2026 The DiamaneOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.gestures

import android.app.settings.SettingsEnums
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.provider.Settings.Secure
import androidx.preference.SwitchPreferenceCompat
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settingslib.widget.TopIntroPreference

/**
 * Picks apps for Moments: the apps Home shows ([LIST_HOME]) or the apps it pauses
 * ([LIST_PAUSED]), from the apps of this user that have a launcher entry. Each change is saved
 * at once.
 */
class MomentsAppsFragment : SettingsPreferenceFragment() {

    private val list: String
        get() = arguments?.getString(ARG_LIST) ?: LIST_HOME

    override fun getMetricsCategory() = SettingsEnums.PAGE_UNKNOWN

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = prefContext
        val screen = preferenceManager.createPreferenceScreen(context)
        preferenceScreen = screen
        if (list == LIST_PAUSED) {
            screen.addPreference(
                TopIntroPreference(context).apply {
                    setTitle(R.string.tally_moments_paused_apps_intro)
                }
            )
        }

        val chosen = read(context, list).toMutableSet()
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return
        val excluded = if (list == LIST_PAUSED) setOf(context.packageName) else emptySet()
        launcherApps
            .getActivityList(null, Process.myUserHandle())
            .distinctBy { it.componentName.packageName }
            .filter { it.componentName.packageName !in excluded }
            .sortedBy { it.label.toString().lowercase() }
            .forEach { app ->
                val pkg = app.componentName.packageName
                screen.addPreference(
                    SwitchPreferenceCompat(context).apply {
                        key = pkg
                        title = app.label
                        icon = app.getBadgedIcon(0)
                        isChecked = pkg in chosen
                        setOnPreferenceChangeListener { _, value ->
                            if (value as Boolean) chosen += pkg else chosen -= pkg
                            write(context, list, chosen)
                            true
                        }
                    }
                )
            }
    }

    companion object {
        const val ARG_LIST = "moments_list"
        const val LIST_HOME = "home"
        const val LIST_PAUSED = "paused"

        private fun key(list: String) =
            if (list == LIST_PAUSED) Secure.TALLY_MOMENTS_PAUSED_APPS
            else Secure.TALLY_MOMENTS_HOME_APPS

        /** The packages on [list], for this user. */
        fun read(context: Context, list: String): Set<String> =
            Secure.getString(context.contentResolver, key(list))
                ?.split(',')
                ?.filter { it.isNotBlank() }
                ?.toSet() ?: emptySet()

        private fun write(context: Context, list: String, packages: Set<String>) {
            Secure.putString(context.contentResolver, key(list), packages.sorted().joinToString(","))
        }
    }
}

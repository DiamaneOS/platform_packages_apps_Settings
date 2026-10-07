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

import android.app.KeyguardManager
import android.app.settings.SettingsEnums
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.os.Bundle
import android.provider.Settings.Secure
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.core.SubSettingLauncher
import com.android.settingslib.widget.FooterPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.TopIntroPreference

/**
 * The Moments switch page: what the switch on the side of the phone does when it is on. The
 * choices are kept in system settings that only Settings, SystemUI and Launcher can read; SystemUI
 * does the rest. Nothing is chosen until the user picks an action here.
 */
class MomentsSwitchSettings : SettingsPreferenceFragment() {

    data class Action(val value: Int, val title: Int, val summary: Int)

    private val radios = mutableListOf<Pair<Action, SelectorWithWidgetPreference>>()
    private lateinit var momentsCategory: PreferenceCategory
    private lateinit var offlineCategory: PreferenceCategory
    private lateinit var homeApps: Preference
    private lateinit var pausedApps: Preference
    private lateinit var greyscale: SwitchPreferenceCompat
    private lateinit var airplane: SwitchPreferenceCompat
    private lateinit var lockdown: SwitchPreferenceCompat

    override fun getMetricsCategory() = SettingsEnums.PAGE_UNKNOWN

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = prefContext
        val screen = preferenceManager.createPreferenceScreen(context)
        preferenceScreen = screen

        screen.addPreference(
            TopIntroPreference(context).apply { setTitle(R.string.tally_moments_intro) }
        )

        val actions =
            PreferenceCategory(context).apply {
                key = "actions"
                setTitle(R.string.tally_moments_action_header)
            }
        screen.addPreference(actions)
        for (action in ACTIONS) {
            val radio =
                SelectorWithWidgetPreference(context).apply {
                    key = "action_${action.value}"
                    setTitle(action.title)
                    setSummary(action.summary)
                    setOnClickListener { setAction(action.value) }
                }
            actions.addPreference(radio)
            radios += action to radio
        }

        momentsCategory =
            PreferenceCategory(context).apply {
                key = "moments"
                setTitle(R.string.tally_moments_options_header)
            }
        screen.addPreference(momentsCategory)
        homeApps =
            Preference(context).apply {
                key = "home_apps"
                setTitle(R.string.tally_moments_home_apps)
                setOnPreferenceClickListener {
                    openApps(MomentsAppsFragment.LIST_HOME, R.string.tally_moments_home_apps_title)
                    true
                }
            }
        pausedApps =
            Preference(context).apply {
                key = "paused_apps"
                setTitle(R.string.tally_moments_paused_apps)
                setOnPreferenceClickListener {
                    openApps(
                        MomentsAppsFragment.LIST_PAUSED,
                        R.string.tally_moments_paused_apps_title,
                    )
                    true
                }
            }
        greyscale =
            SwitchPreferenceCompat(context).apply {
                key = "greyscale"
                setTitle(R.string.tally_moments_greyscale)
                setSummary(R.string.tally_moments_greyscale_summary)
                setOnPreferenceChangeListener { _, value ->
                    putInt(Secure.TALLY_MOMENTS_GREYSCALE, if (value as Boolean) 1 else 0)
                    true
                }
            }
        momentsCategory.addPreference(homeApps)
        momentsCategory.addPreference(pausedApps)
        momentsCategory.addPreference(greyscale)

        offlineCategory =
            PreferenceCategory(context).apply {
                key = "offline"
                setTitle(R.string.tally_moments_offline_header)
            }
        screen.addPreference(offlineCategory)
        airplane = offlineSwitch("airplane", Secure.MOMENTS_OFFLINE_AIRPLANE)
        airplane.setTitle(R.string.tally_moments_offline_airplane)
        lockdown = offlineSwitch("lockdown", Secure.MOMENTS_OFFLINE_LOCKDOWN)
        lockdown.setTitle(R.string.tally_moments_offline_lockdown)
        lockdown.setSummary(R.string.tally_moments_offline_lockdown_summary)
        offlineCategory.addPreference(airplane)
        offlineCategory.addPreference(lockdown)

        screen.addPreference(
            FooterPreference(context).apply {
                key = "footer"
                setTitle(R.string.tally_moments_footer)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // One of the two must stay on, so the action always does something. Lockdown counts only
    // with a screen lock.
    private fun offlineSwitch(key: String, bit: Int) =
        SwitchPreferenceCompat(prefContext).apply {
            this.key = key
            setOnPreferenceChangeListener { _, value ->
                val flags = offlineFlags()
                val next = if (value as Boolean) flags or bit else flags and bit.inv()
                val usable =
                    if (hasScreenLock()) next
                    else next and Secure.MOMENTS_OFFLINE_LOCKDOWN.inv()
                if (usable == 0) return@setOnPreferenceChangeListener false
                putInt(Secure.TALLY_MOMENTS_OFFLINE, next)
                true
            }
        }

    private fun hasScreenLock() =
        requireContext().getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    private fun setAction(value: Int) {
        // Saving a choice is what lets the switch act (SystemUI applies it at once if it is on).
        putInt(Secure.TALLY_MOMENTS_ACTION, value)
        refresh()
    }

    private fun refresh() {
        val resolver = contentResolver
        val action = Secure.getString(resolver, Secure.TALLY_MOMENTS_ACTION)?.toIntOrNull()
        for ((a, radio) in radios) {
            radio.isChecked = a.value == action
            // Offered only where the phone has the software camera and microphone toggles.
            if (a.value == Secure.MOMENTS_ACTION_SENSORS_OFF) {
                radio.isVisible = hasSensorToggles(requireContext())
            }
        }
        momentsCategory.isVisible = action == Secure.MOMENTS_ACTION_MOMENTS
        offlineCategory.isVisible = action == Secure.MOMENTS_ACTION_OFFLINE

        val home = MomentsAppsFragment.read(requireContext(), MomentsAppsFragment.LIST_HOME)
        homeApps.summary =
            if (home.isEmpty()) getString(R.string.tally_moments_home_apps_none)
            else countText(home.size)
        val paused = MomentsAppsFragment.read(requireContext(), MomentsAppsFragment.LIST_PAUSED)
        pausedApps.summary =
            if (paused.isEmpty()) getString(R.string.tally_moments_paused_apps_none)
            else countText(paused.size)
        greyscale.isChecked = Secure.getInt(resolver, Secure.TALLY_MOMENTS_GREYSCALE, 0) == 1

        val flags = offlineFlags()
        val screenLock = hasScreenLock()
        airplane.isChecked = flags and Secure.MOMENTS_OFFLINE_AIRPLANE != 0
        // Lockdown needs a PIN, pattern or password, as in the power menu.
        lockdown.isEnabled = screenLock
        lockdown.isChecked = screenLock && flags and Secure.MOMENTS_OFFLINE_LOCKDOWN != 0
        lockdown.setSummary(
            if (screenLock) R.string.tally_moments_offline_lockdown_summary
            else R.string.tally_moments_offline_lockdown_needs_lock
        )
    }

    private fun countText(count: Int) =
        resources.getQuantityString(R.plurals.tally_moments_apps_count, count, count)

    private fun offlineFlags() =
        Secure.getInt(contentResolver, Secure.TALLY_MOMENTS_OFFLINE, Secure.MOMENTS_OFFLINE_AIRPLANE)

    private fun putInt(key: String, value: Int) {
        Secure.putInt(contentResolver, key, value)
    }

    private fun openApps(list: String, title: Int) {
        SubSettingLauncher(requireContext())
            .setDestination(MomentsAppsFragment::class.java.name)
            .setArguments(Bundle().apply { putString(MomentsAppsFragment.ARG_LIST, list) })
            .setTitleRes(title)
            .setSourceMetricsCategory(metricsCategory)
            .launch()
    }

    companion object {
        val ACTIONS =
            listOf(
                Action(
                    Secure.MOMENTS_ACTION_MOMENTS,
                    R.string.tally_moments_action_moments,
                    R.string.tally_moments_action_moments_summary,
                ),
                Action(
                    Secure.MOMENTS_ACTION_SENSORS_OFF,
                    R.string.tally_moments_action_sensors,
                    R.string.tally_moments_action_sensors_summary,
                ),
                Action(
                    Secure.MOMENTS_ACTION_SILENT,
                    R.string.tally_moments_action_silent,
                    R.string.tally_moments_action_silent_summary,
                ),
                Action(
                    Secure.MOMENTS_ACTION_OFFLINE,
                    R.string.tally_moments_action_offline,
                    R.string.tally_moments_action_offline_summary,
                ),
                Action(
                    Secure.MOMENTS_ACTION_NOTHING,
                    R.string.tally_moments_action_nothing,
                    R.string.tally_moments_action_nothing_summary,
                ),
            )

        /** Whether this phone offers the camera or microphone access toggle. */
        fun hasSensorToggles(context: Context): Boolean {
            val manager = context.getSystemService(SensorPrivacyManager::class.java) ?: return false
            return manager.supportsSensorToggle(SensorPrivacyManager.Sensors.CAMERA) ||
                manager.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE)
        }

        /** Whether this phone has a Moments switch (its framework config names one). */
        fun hasSwitch(context: Context): Boolean =
            context.resources.getInteger(com.android.internal.R.integer.config_momentsSwitchCode) >=
                0
    }
}

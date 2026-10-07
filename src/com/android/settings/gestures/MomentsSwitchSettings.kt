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
import android.os.SystemProperties
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
    private lateinit var homeApps: Preference
    private lateinit var pausedApps: Preference
    private lateinit var greyscale: SwitchPreferenceCompat
    private lateinit var kernelCategory: PreferenceCategory
    private lateinit var kernelNote: Preference
    private lateinit var emergencyNote: Preference

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

        // The kernel floor: shown where the kernel blocks the microphones (see refresh()).
        kernelCategory =
            PreferenceCategory(context).apply {
                key = "kernel"
                setTitle(R.string.tally_moments_kernel_header)
            }
        screen.addPreference(kernelCategory)
        kernelNote =
            Preference(context).apply {
                key = "kernel_note"
                isSelectable = false
                setTitle(R.string.tally_moments_kernel_title)
            }
        emergencyNote =
            Preference(context).apply {
                key = "kernel_emergency"
                isSelectable = false
                setTitle(R.string.tally_moments_kernel_emergency_title)
                setSummary(R.string.tally_moments_kernel_emergency)
            }
        kernelCategory.addPreference(kernelNote)
        kernelCategory.addPreference(emergencyNote)

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

        screen.addPreference(
            FooterPreference(context).apply {
                key = "footer"
                setTitle(R.string.tally_moments_footer)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        migrateOfflineChoice()
        refresh()
    }

    // An earlier "airplane mode and/or Lockdown" choice (action 3 with flags) becomes Lockdown
    // if Lockdown was on and there is a screen lock, else airplane mode; SystemUI reads it the
    // same way until this runs.
    private fun migrateOfflineChoice() {
        val flags = Secure.getString(contentResolver, Secure.TALLY_MOMENTS_OFFLINE)?.toIntOrNull()
            ?: return
        val action = Secure.getString(contentResolver, Secure.TALLY_MOMENTS_ACTION)?.toIntOrNull()
        if (
            action == Secure.MOMENTS_ACTION_AIRPLANE &&
                flags and Secure.MOMENTS_OFFLINE_LOCKDOWN != 0 &&
                hasScreenLock()
        ) {
            putInt(Secure.TALLY_MOMENTS_ACTION, Secure.MOMENTS_ACTION_LOCKDOWN)
        }
        Secure.putString(contentResolver, Secure.TALLY_MOMENTS_OFFLINE, null)
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
        val screenLock = hasScreenLock()
        for ((a, radio) in radios) {
            radio.isChecked = a.value == action
            when (a.value) {
                // Offered only where the phone has the software camera and microphone toggles.
                Secure.MOMENTS_ACTION_SENSORS_OFF -> {
                    radio.isVisible = hasSensorToggles(requireContext())
                    if (hasKernelFloor(requireContext())) {
                        radio.setSummary(R.string.tally_moments_action_sensors_summary_kernel)
                    }
                }
                // Lockdown needs a PIN, pattern or password, as in the power menu.
                Secure.MOMENTS_ACTION_LOCKDOWN -> {
                    radio.isEnabled = screenLock
                    radio.setSummary(
                        if (screenLock) a.summary
                        else R.string.tally_moments_action_lockdown_needs_lock
                    )
                }
            }
        }
        momentsCategory.isVisible = action == Secure.MOMENTS_ACTION_MOMENTS
        refreshKernelFloor(action)

        val home = MomentsAppsFragment.read(requireContext(), MomentsAppsFragment.LIST_HOME)
        homeApps.summary =
            if (home.isEmpty()) getString(R.string.tally_moments_home_apps_none)
            else countText(home.size)
        val paused = MomentsAppsFragment.read(requireContext(), MomentsAppsFragment.LIST_PAUSED)
        pausedApps.summary =
            if (paused.isEmpty()) getString(R.string.tally_moments_paused_apps_none)
            else countText(paused.size)
        greyscale.isChecked = Secure.getInt(resolver, Secure.TALLY_MOMENTS_GREYSCALE, 0) == 1
    }

    // The kernel learns the choice once per boot; Android's block follows at once.
    private fun refreshKernelFloor(action: Int?) {
        val chosen = action == Secure.MOMENTS_ACTION_SENSORS_OFF
        val enforced = kernelBlocksMic()
        val note = kernelNoteFor(chosen, enforced)
        kernelCategory.isVisible = hasKernelFloor(requireContext()) && note != null
        note?.let { kernelNote.setSummary(it) }
    }

    private fun countText(count: Int) =
        resources.getQuantityString(R.plurals.tally_moments_apps_count, count, count)

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
                    Secure.MOMENTS_ACTION_AIRPLANE,
                    R.string.tally_moments_action_airplane,
                    R.string.tally_moments_action_airplane_summary,
                ),
                Action(
                    Secure.MOMENTS_ACTION_LOCKDOWN,
                    R.string.tally_moments_action_lockdown,
                    R.string.tally_moments_action_lockdown_summary,
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

        /**
         * The kernel note for the page, or null for none: [chosen] is "Camera and microphone off",
         * [enforced] whether the kernel blocks the microphones in this boot (its own report).
         */
        fun kernelNoteFor(chosen: Boolean, enforced: Boolean): Int? =
            when {
                chosen && enforced -> R.string.tally_moments_kernel_on
                // Only Android blocks the microphone until a restart arms the kernel.
                chosen -> R.string.tally_moments_kernel_after_restart
                // Another choice, but the kernel keeps blocking until the next start.
                enforced -> R.string.tally_moments_kernel_until_restart
                else -> null
            }

        /** Whether the kernel can block the microphones from the switch (see the framework). */
        fun hasKernelFloor(context: Context): Boolean =
            context.resources
                .getString(com.android.internal.R.string.config_momentsKernelFloorPath)
                .isNotEmpty()

        /** Whether the kernel blocks the microphones from the switch in this boot. */
        fun kernelBlocksMic(): Boolean =
            SystemProperties.getInt(KERNEL_ENFORCED_PROPERTY, 0) and KERNEL_MIC != 0

        // Set by the input service from the kernel (MomentsKernelFloor).
        private const val KERNEL_ENFORCED_PROPERTY = "diamaneos.privacy_switch.enforced"
        private const val KERNEL_MIC = 1

        /** Whether this phone has a Moments switch (its framework config names one). */
        fun hasSwitch(context: Context): Boolean =
            context.resources.getInteger(com.android.internal.R.integer.config_momentsSwitchCode) >=
                0
    }
}

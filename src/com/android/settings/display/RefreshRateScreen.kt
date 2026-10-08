/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.display

import android.app.settings.SettingsEnums
import android.content.Context
import android.provider.Settings.System.PEAK_REFRESH_RATE
import androidx.fragment.app.Fragment
import com.android.settings.R
import com.android.settings.Settings.RefreshRateActivity
import com.android.settings.core.PreferenceScreenMixin
import com.android.settings.utils.makeLaunchIntent
import com.android.settingslib.datastore.HandlerExecutor
import com.android.settingslib.datastore.KeyedObserver
import com.android.settingslib.datastore.SettingsSystemStore
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceLifecycleContext
import com.android.settingslib.metadata.PreferenceLifecycleProvider
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.ProvidePreferenceScreen
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen.Companion.APP_FUNCTION_UNCATEGORIZED
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import kotlinx.coroutines.CoroutineScope

/**
 * DiamaneOS: Display > Maximum refresh rate. Replaces the Smooth display switch (60 Hz or the
 * panel's highest) with a choice of every supported rate from 30 Hz up; see [RefreshRates].
 */
@ProvidePreferenceScreen(RefreshRateScreen.KEY)
open class RefreshRateScreen :
    PreferenceScreenMixin,
    PreferenceAvailabilityProvider,
    PreferenceSummaryProvider,
    PreferenceLifecycleProvider {
    override fun tags(context: Context) = arrayOf(APP_FUNCTION_UNCATEGORIZED)

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.tally_refresh_rate_purpose

    override val title: Int
        get() = R.string.tally_refresh_rate_title

    override fun getMetricsCategory() = SettingsEnums.DISPLAY

    override fun hasCompleteHierarchy() = false

    override fun fragmentClass(): Class<out Fragment>? = RefreshRatePreferenceFragment::class.java

    override val highlightMenuKey: Int
        get() = R.string.menu_key_display

    override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
        preferenceHierarchy(context) {}

    override val availabilityDescription =
        "The display must offer more than one refresh rate from 30 Hz up."

    override fun getAvailabilityStability() = PreconditionStability.STABLE_UNTIL_APK_UPDATE

    override fun isAvailable(context: Context) = RefreshRates.supported(context).size > 1

    override fun getSummary(context: Context): CharSequence? =
        context.getString(R.string.tally_refresh_rate_summary, RefreshRates.current(context))

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?) =
        makeLaunchIntent(context, RefreshRateActivity::class.java, metadata?.key)

    private var observer: KeyedObserver<String>? = null

    override fun onStart(context: PreferenceLifecycleContext) {
        if (isEntryPoint(context)) {
            val keyedObserver = KeyedObserver<String> { _, _ -> context.notifyPreferenceChange(KEY) }
            observer = keyedObserver
            SettingsSystemStore.get(context)
                .addObserver(PEAK_REFRESH_RATE, keyedObserver, HandlerExecutor.main)
        }
    }

    override fun onStop(context: PreferenceLifecycleContext) {
        observer?.let {
            SettingsSystemStore.get(context).removeObserver(PEAK_REFRESH_RATE, it)
            observer = null
        }
    }

    companion object {
        const val KEY = "diamaneos_refresh_rate"
    }
}

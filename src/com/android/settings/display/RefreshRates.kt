/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.DeviceConfig
import android.provider.Settings
import android.view.Display
import kotlin.math.roundToInt

/**
 * DiamaneOS: the user's maximum refresh rate, kept in Settings.System.PEAK_REFRESH_RATE.
 *
 * Only the peak is set: MIN_REFRESH_RATE stays as it is, so content detection and the panel's idle
 * drop (10 Hz on the FP6) keep saving battery. The highest supported rate is stored as infinity, as
 * the AOSP Smooth display switch does, so the platform treats it as uncapped.
 */
object RefreshRates {
    private const val LOWEST_CHOICE = 30
    private const val INVALID = -1f

    /** Supported rates of the default display, at least 30 Hz, highest first. */
    @JvmStatic
    fun supported(context: Context): List<Int> {
        val display =
            context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return emptyList()
        return display.supportedModes
            .map { it.refreshRate.roundToInt() }
            .filter { it >= LOWEST_CHOICE }
            .distinct()
            .sortedDescending()
    }

    /** The current maximum, as one of [supported]. */
    @JvmStatic
    fun current(context: Context): Int {
        val rates = supported(context)
        if (rates.isEmpty()) return 60
        val peak =
            Settings.System.getFloat(
                context.contentResolver,
                Settings.System.PEAK_REFRESH_RATE,
                defaultPeak(context),
            )
        if (peak.isInfinite() || peak <= 0f) return rates.first()
        return rates.firstOrNull { it <= peak.roundToInt() } ?: rates.last()
    }

    /** Stores [rate] as the maximum; the highest supported rate means uncapped. */
    @JvmStatic
    fun set(context: Context, rate: Int) {
        val rates = supported(context)
        if (rate !in rates) return
        val value = if (rate == rates.first()) Float.POSITIVE_INFINITY else rate.toFloat()
        Settings.System.putFloat(context.contentResolver, Settings.System.PEAK_REFRESH_RATE, value)
    }

    private fun defaultPeak(context: Context): Float {
        val fromConfig =
            DeviceConfig.getFloat(
                DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                DisplayManager.DeviceConfig.KEY_PEAK_REFRESH_RATE_DEFAULT,
                INVALID,
            )
        if (fromConfig != INVALID) return fromConfig
        return context.resources
            .getInteger(com.android.internal.R.integer.config_defaultPeakRefreshRate)
            .toFloat()
    }
}

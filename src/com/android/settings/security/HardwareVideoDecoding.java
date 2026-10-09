/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.security;

import android.content.Context;
import android.ext.settings.BoolSysProperty;
import android.os.SystemProperties;

import com.android.settings.R;

/**
 * Hardware video decoding: whether apps may decode video with the device's hardware decoders
 * instead of only the sandboxed software decoders. Off by default. The device applies the
 * owner's choice at the next boot and reports the state of the running boot, so a change
 * waits for a restart.
 */
public final class HardwareVideoDecoding {
    /** The owner's choice, which only Settings may set ("1" on, "0" off, unset off). */
    public static final BoolSysProperty SETTING =
            new BoolSysProperty("persist.diamaneos.hw_video_decode", false);

    // The state of the running boot, set by the device at boot ("1" on, "0" off).
    private static final String BOOT_STATE = "ro.vendor.diamaneos.hw_video_decode";

    private HardwareVideoDecoding() {}

    /** Whether the device can switch hardware video decoding (a device overlay sets it). */
    public static boolean isSupported(Context context) {
        return context.getResources().getBoolean(R.bool.config_show_hardware_video_decoding);
    }

    /** Whether the owner's choice differs from the state of the running boot. */
    public static boolean isRestartPending(Context context) {
        String boot = SystemProperties.get(BOOT_STATE);
        if (boot.isEmpty()) {
            return false;
        }
        return SETTING.get(context) != "1".equals(boot);
    }
}

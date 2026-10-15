/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.security;

import android.content.Context;
import android.ext.settings.IntSysProperty;
import android.os.SystemProperties;

import com.android.settings.R;

/**
 * Hardware video decoding: which video apps may decode with the device's hardware decoders
 * instead of the sandboxed software decoders. Protected video only by default where the device
 * offers it, else never. The device applies the user's choice at the next boot and reports the
 * state of the running boot, so a change waits for a restart.
 */
public final class HardwareVideoDecoding {
    /** No hardware decoder: protected video is then protected in software only. */
    public static final int NEVER = 0;
    /** The hardware decoders for all video. */
    public static final int ALL_VIDEO = 1;
    /** The hardware decoders for protected video only. */
    public static final int PROTECTED_VIDEO = 2;

    /** The user's choice, which only Settings may set ("0", "1" or "2"). */
    public static final IntSysProperty SETTING = new IntSysProperty(
            "persist.diamaneos.hw_video_decode",
            context -> offersProtectedVideo(context) ? PROTECTED_VIDEO : NEVER,
            NEVER, ALL_VIDEO, PROTECTED_VIDEO);

    // The state of the running boot, set by the device at boot ("0", "1" or "2").
    private static final String BOOT_STATE = "ro.vendor.diamaneos.hw_video_decode";

    private HardwareVideoDecoding() {}

    /** Whether the device applies the choice (a device overlay sets it). */
    public static boolean isSupported(Context context) {
        return context.getResources().getBoolean(R.bool.config_show_hardware_video_decoding);
    }

    /** Whether the device offers protected video only (a device overlay sets it). */
    public static boolean offersProtectedVideo(Context context) {
        return context.getResources().getBoolean(
                R.bool.config_hardware_video_decoding_protected_video);
    }

    /** Whether the user's choice differs from the state of the running boot. */
    public static boolean isRestartPending(Context context) {
        String boot = SystemProperties.get(BOOT_STATE);
        if (boot.isEmpty()) {
            return false;
        }
        return !Integer.toString(SETTING.get(context)).equals(boot);
    }
}

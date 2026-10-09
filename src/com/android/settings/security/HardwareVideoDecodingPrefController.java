/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.security;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.ext.BoolSettingFragmentPrefController;

/** Exploit protection > Hardware video decoding, on devices that can switch it. */
public class HardwareVideoDecodingPrefController extends BoolSettingFragmentPrefController {

    public HardwareVideoDecodingPrefController(Context ctx, String key) {
        super(ctx, key, HardwareVideoDecoding.SETTING);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!HardwareVideoDecoding.isSupported(mContext)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return super.getAvailabilityStatus();
    }

    @Override
    protected CharSequence getSummaryOn() {
        return resText(HardwareVideoDecoding.isRestartPending(mContext)
                ? R.string.tally_hw_video_decoding_summary_on_after_restart
                : R.string.bool_setting_enabled);
    }

    @Override
    protected CharSequence getSummaryOff() {
        return resText(HardwareVideoDecoding.isRestartPending(mContext)
                ? R.string.tally_hw_video_decoding_summary_off_after_restart
                : R.string.bool_setting_disabled);
    }
}

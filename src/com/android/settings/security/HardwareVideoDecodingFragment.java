/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.security;

import android.ext.settings.BoolSetting;

import com.android.settings.R;
import com.android.settings.ext.BoolSettingFragment;
import com.android.settingslib.widget.FooterPreference;

/** The Hardware video decoding page: the switch and what it trades. */
public class HardwareVideoDecodingFragment extends BoolSettingFragment {

    @Override
    protected BoolSetting getSetting() {
        return HardwareVideoDecoding.SETTING;
    }

    @Override
    protected CharSequence getTitle() {
        return resText(R.string.tally_hw_video_decoding_title);
    }

    @Override
    protected CharSequence getMainSwitchTitle() {
        return resText(R.string.tally_hw_video_decoding_main_switch);
    }

    @Override
    protected CharSequence getMainSwitchSummary() {
        // Cleared when the choice matches the running boot again.
        return HardwareVideoDecoding.isRestartPending(requireContext())
                ? resText(R.string.tally_hw_video_decoding_restart) : "";
    }

    @Override
    protected void onMainSwitchChanged(boolean state) {
        refreshMainSwitch();
    }

    @Override
    protected FooterPreference makeFooterPref(FooterPreference.Builder builder) {
        return builder.setTitle(R.string.tally_hw_video_decoding_footer).build();
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.display;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * DiamaneOS: the Display > Maximum refresh rate entry in display_settings.xml, which the Display
 * screen still inflates; see {@link RefreshRates} and {@link RefreshRateScreen}.
 */
public class RefreshRatePreferenceController extends BasePreferenceController {
    public RefreshRatePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return RefreshRates.supported(mContext).size() > 1 ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getString(R.string.tally_refresh_rate_summary,
                RefreshRates.current(mContext));
    }
}

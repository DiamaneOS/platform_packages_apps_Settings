/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.settings.fuelgauge.batterytip.detectors;

import android.content.Context;
import android.ext.power.BatteryChargeLimit;

import com.android.settings.fuelgauge.BatteryInfo;
import com.android.settings.fuelgauge.batterytip.tips.BatteryDefenderTip;
import com.android.settings.fuelgauge.batterytip.tips.BatteryTip;

/** Detect whether the battery is overheated */
public class BatteryDefenderDetector implements BatteryTipDetector {
    private final BatteryInfo mBatteryInfo;
    private final Context mContext;

    public BatteryDefenderDetector(BatteryInfo batteryInfo, Context context) {
        mBatteryInfo = batteryInfo;
        mContext = context;
    }

    @Override
    public BatteryTip detect() {
        // Outside Pixels, a hold with the charge limit on is the user's own limit: the battery
        // header says charging is on hold, and the tip's "Charge anyway" cannot lift the limit.
        final boolean chargeLimitHold = !BatteryChargeLimit.isGoogleDevice()
                && BatteryChargeLimit.getSetting().get(mContext);
        final int state =
                mBatteryInfo.isBatteryDefender && !chargeLimitHold
                        ? BatteryTip.StateType.NEW
                        : BatteryTip.StateType.INVISIBLE;
        final boolean isPluggedIn = mBatteryInfo.pluggedStatus != 0;
        return new BatteryDefenderTip(state, isPluggedIn);
    }
}

package com.android.settings.fuelgauge;

import android.content.Context;
import android.ext.power.BatteryChargeLimit;
import android.icu.text.NumberFormat;

import com.android.settings.R;
import com.android.settings.ext.BoolSettingFragmentPrefController;
import com.android.settings.ext.ExtSettingControllerHelper;

public class BatteryChargingOptimizationPrefController extends BoolSettingFragmentPrefController {
    private static final String TAG = "BatteryChargeLimitPrefController";

    public BatteryChargingOptimizationPrefController(Context ctx, String key) {
        super(ctx, key, BatteryChargeLimit.getSetting());
    }

    @Override
    protected CharSequence getSummaryOn() {
        return mContext.getString(R.string.charging_optimization_entry_summary_charge_limit,
                        NumberFormat.getPercentInstance().format(BatteryChargeLimit.CHARGE_LEVEL / 100f));
    }

    @Override
    protected CharSequence getSummaryOff() {
        return mContext.getString(R.string.charging_optimization_summary_off);
    }

    static int getAvailabilityStatus(Context ctx) {
        if (!BatteryChargeLimit.isSupported(ctx)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return ExtSettingControllerHelper.getGlobalSettingAvailability(ctx);
    }

    @Override
    public int getAvailabilityStatus() {
        return getAvailabilityStatus(mContext);
    }
}

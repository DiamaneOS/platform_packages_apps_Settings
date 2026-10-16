package com.android.settings.fuelgauge;

import android.ext.power.BatteryChargeLimit;
import android.ext.settings.BoolSetting;
import android.icu.text.NumberFormat;
import android.os.RemoteException;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.ext.BoolSettingFragment;
import com.android.settingslib.widget.FooterPreference;
import com.google.android.settings.fuelgauge.GoogleBattery;

import vendor.google.google_battery.IGoogleBattery;

public class BatteryChargingOptimizationFragment extends BoolSettingFragment {
    private static final String TAG = "BatteryChargingOptimizationFragment";

    @Override
    protected boolean interceptMainSwitchChange(boolean newValue) {
        if (BatteryChargeLimit.isHealthHalLimit(requireContext())) {
            // BatteryService passes the setting to the health HAL.
            return false;
        }
        int policy = newValue ?
                IGoogleBattery.BatteryChargingPolicy.LONGLIFE :
                IGoogleBattery.BatteryChargingPolicy.DEFAULT;

        // block setting update if policy update fails
        return !setChargingPolicy(policy);
    }

    static boolean setChargingPolicy(int policy) {
        IGoogleBattery service = GoogleBattery.getService();
        if (service == null) {
            return false;
        }
        try {
            service.setChargingPolicy(policy);
            Log.d(TAG, "setChargingPolicy to " + policy);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
            return false;
        }
    }

    @Override
    protected BoolSetting getSetting() {
        return BatteryChargeLimit.getSetting();
    }

    @Override
    protected CharSequence getTitle() {
        return getText(R.string.charging_optimization_title);
    }

    @Override
    protected CharSequence getMainSwitchTitle() {
        return requireContext().getString(
                R.string.charging_optimization_entry_summary_charge_limit,
                NumberFormat.getPercentInstance().format(BatteryChargeLimit.CHARGE_LEVEL / 100f));
    }

    @Override
    protected void onMainSwitchChanged(boolean state) {
        footer.setVisible(getSetting().get(requireContext()));
    }

    private FooterPreference footer;

    @Override
    protected FooterPreference makeFooterPref(FooterPreference.Builder builder) {
        String text = requireContext().getString(
                R.string.charging_optimization_footer_message_charging_limit,
                NumberFormat.getPercentInstance().format(BatteryChargeLimit.CHARGE_LEVEL / 100f),
                NumberFormat.getPercentInstance().format(1f));

        builder.setTitle(text);

        FooterPreference pref = builder.build();
        pref.setVisible(getSetting().get(requireContext()));
        this.footer = pref;
        return pref;
    }
}

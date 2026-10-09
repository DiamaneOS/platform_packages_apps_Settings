/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.settings.display;

import android.content.Context;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;

public class TapToWakePreferenceController extends AbstractPreferenceController implements
        PreferenceControllerMixin, Preference.OnPreferenceChangeListener {

    private static final String KEY_TAP_TO_WAKE = "tap_to_wake";

    private AmbientDisplayConfiguration mAmbientConfig;

    public TapToWakePreferenceController(Context context) {
        super(context);
    }

    @VisibleForTesting
    TapToWakePreferenceController setConfig(AmbientDisplayConfiguration config) {
        mAmbientConfig = config;
        return this;
    }

    /**
     * Whether Tap to wake switches Android's doze double tap (DOZE_DOUBLE_TAP_GESTURE) instead
     * of the power HAL mode (DOUBLE_TAP_TO_WAKE): the device supports double tap to wake and
     * reports the double tap as a doze sensor (config_dozeDoubleTapSensorType). SystemUI then
     * wakes the device on it after its doze checks, such as the proximity sensor, and
     * "Double-tap to check phone" is not offered as a second switch for the same setting.
     */
    public static boolean usesDozeDoubleTap(Context context, AmbientDisplayConfiguration config) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_supportDoubleTapWake)
                && config.doubleTapSensorAvailable();
    }

    @Override
    public String getPreferenceKey() {
        return KEY_TAP_TO_WAKE;
    }

    @Override
    public boolean isAvailable() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportDoubleTapWake);
    }

    @Override
    public void updateState(Preference preference) {
        final boolean checked;
        if (usesDozeDoubleTap(mContext, getAmbientConfig())) {
            checked = getAmbientConfig().doubleTapGestureEnabled(UserHandle.myUserId());
        } else {
            checked = Settings.Secure.getInt(
                    mContext.getContentResolver(), Settings.Secure.DOUBLE_TAP_TO_WAKE, 0) != 0;
        }
        ((TwoStatePreference) preference).setChecked(checked);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = (Boolean) newValue;
        Settings.Secure.putInt(mContext.getContentResolver(),
                usesDozeDoubleTap(mContext, getAmbientConfig())
                        ? Settings.Secure.DOZE_DOUBLE_TAP_GESTURE
                        : Settings.Secure.DOUBLE_TAP_TO_WAKE,
                value ? 1 : 0);
        return true;
    }

    private AmbientDisplayConfiguration getAmbientConfig() {
        if (mAmbientConfig == null) {
            mAmbientConfig = new AmbientDisplayConfiguration(mContext);
        }
        return mAmbientConfig;
    }
}

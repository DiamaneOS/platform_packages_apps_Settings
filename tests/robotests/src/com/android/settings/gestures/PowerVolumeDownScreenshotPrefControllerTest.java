/*
 * Copyright (C) 2026 The DiamaneOS Project
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

package com.android.settings.gestures;

import static com.android.settings.core.BasePreferenceController.AVAILABLE;
import static com.android.settings.core.BasePreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.provider.Settings;

import com.android.settings.testutils.shadow.SettingsShadowResources;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = SettingsShadowResources.class)
public class PowerVolumeDownScreenshotPrefControllerTest {

    private static final String KEY = "gesture_power_volume_down_screenshot";

    private Context mContext;
    private PowerVolumeDownScreenshotPrefController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mController = new PowerVolumeDownScreenshotPrefController(mContext, KEY);
    }

    @After
    public void tearDown() {
        SettingsShadowResources.reset();
    }

    @Test
    public void getAvailabilityStatus_deviceHasChord_available() {
        SettingsShadowResources.overrideResource(
                com.android.internal.R.bool.config_enableScreenshotChord, Boolean.TRUE);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_deviceHasNoChord_unsupported() {
        SettingsShadowResources.overrideResource(
                com.android.internal.R.bool.config_enableScreenshotChord, Boolean.FALSE);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void isChecked_unset_isOn() {
        assertThat(mController.isChecked()).isTrue();
    }

    @Test
    public void setChecked_false_storesZero() {
        mController.setChecked(false);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.POWER_VOLUME_DOWN_SCREENSHOT, -1)).isEqualTo(0);
        assertThat(mController.isChecked()).isFalse();
    }
}

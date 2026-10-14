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

package com.android.settings.network;

import static com.android.settings.core.BasePreferenceController.AVAILABLE;
import static com.android.settings.core.BasePreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.settings.core.BasePreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
public class ManageEsimsPreferenceControllerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private void installLpa(String packageName, boolean enabled) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        info.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        info.applicationInfo.enabled = enabled;
        Shadows.shadowOf(mContext.getPackageManager()).installPackage(info);
    }

    private int status() {
        return new ManageEsimsPreferenceController(mContext, "manage_esims").getAvailabilityStatus();
    }

    @Test
    public void getAvailabilityStatus_noLpa_unsupported() {
        assertThat(status()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_eSimSupportOff_unavailable() {
        installLpa("de.diamaneos.euicc", false);
        assertThat(status()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_eSimSupportOn_available() {
        installLpa("de.diamaneos.euicc", true);
        assertThat(status()).isEqualTo(AVAILABLE);
    }
}

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

package com.android.settings.deviceinfo.firmwareversion;

import static com.android.settings.core.BasePreferenceController.AVAILABLE;
import static com.android.settings.core.BasePreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;

import androidx.test.core.app.ApplicationProvider;

import com.android.settingslib.DeviceInfoUtils;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;

import java.util.Locale;

// LINT.IfChange
@RunWith(RobolectricTestRunner.class)
public class VendorSecurityPatchLevelPreferenceControllerTest {

    private static final String PROPERTY = "ro.vendor.build.security_patch";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @After
    public void tearDown() {
        SystemProperties.set(PROPERTY, "");
    }

    @Test
    public void getAvailabilityStatus_noPatch_unavailable() {
        SystemProperties.set(PROPERTY, "");
        final VendorSecurityPatchLevelPreferenceController controller =
                new VendorSecurityPatchLevelPreferenceController(mContext, "key");

        assertThat(controller.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_patchIsNotADate_unavailable() {
        for (String patch : new String[] {
                "foobar", "2026-9-05", "20260905", "2026-09-05 ", "2026-02-30", "2026-13-05"}) {
            SystemProperties.set(PROPERTY, patch);
            final VendorSecurityPatchLevelPreferenceController controller =
                    new VendorSecurityPatchLevelPreferenceController(mContext, "key");

            assertThat(controller.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
        }
    }

    @Test
    public void getAvailabilityStatus_hasPatch_available() {
        SystemProperties.set(PROPERTY, "2026-09-05");
        final VendorSecurityPatchLevelPreferenceController controller =
                new VendorSecurityPatchLevelPreferenceController(mContext, "key");

        assertThat(controller.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getSummary_patchIsDate() {
        SystemProperties.set(PROPERTY, "2026-09-05");
        final VendorSecurityPatchLevelPreferenceController controller =
                new VendorSecurityPatchLevelPreferenceController(mContext, "key");

        assertThat(controller.getSummary().toString()).isEqualTo("September 5, 2026");
    }

    @Test
    public void formatPatch_matchesTheAndroidSecurityPatchFormat() {
        final String previous = Build.VERSION.SECURITY_PATCH;
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SECURITY_PATCH", "2024-09-24");
        try {
            assertThat(VendorSecurityPatchLevelPreferenceController.formatPatch(
                    "2024-09-24", Locale.getDefault()))
                    .isEqualTo(DeviceInfoUtils.getSecurityPatch());
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SECURITY_PATCH", previous);
        }
    }
}
// LINT.ThenChange(VendorSecurityPatchLevelPreferenceTest.kt)

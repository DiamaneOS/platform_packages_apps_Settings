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
import android.os.SystemProperties;

import androidx.test.core.app.ApplicationProvider;

import com.android.settings.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

// LINT.IfChange
@RunWith(RobolectricTestRunner.class)
public class FirmwareReleasePreferenceControllerTest {

    private static final String PROPERTY = "ro.vendor.diamaneos.firmware_release";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @After
    public void tearDown() {
        SystemProperties.set(PROPERTY, "");
    }

    private FirmwareReleasePreferenceController controller(String value) {
        SystemProperties.set(PROPERTY, value);
        return new FirmwareReleasePreferenceController(mContext, "key");
    }

    @Test
    public void getAvailabilityStatus_noRelease_unavailable() {
        assertThat(controller("").getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_malformedValue_unavailable() {
        for (String value : new String[] {
                "foobar", "16.111", "16.111.0.1", "v16.111.0", "FP6.QREL.16.111.0", "16.111.0 ",
                " 16.111.0", "16.111.x", "16..0", "16.111.123456", "-16.111.0", "Mixed", "MIXED",
                "mixed ", "Unknown", "unknown releases"}) {
            assertThat(controller(value).getAvailabilityStatus())
                    .isEqualTo(CONDITIONALLY_UNAVAILABLE);
        }
    }

    @Test
    public void getSummary_release_showsTheReleaseNumber() {
        final FirmwareReleasePreferenceController controller = controller("16.111.0");

        assertThat(controller.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        assertThat(controller.getSummary().toString()).isEqualTo("16.111.0");
    }

    @Test
    public void getSummary_mixed_showsMixedReleases() {
        final FirmwareReleasePreferenceController controller = controller("mixed");

        assertThat(controller.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        assertThat(controller.getSummary().toString()).isEqualTo("Mixed releases");
    }

    @Test
    public void getSummary_unknown_showsTheTranslatedUnknown() {
        final FirmwareReleasePreferenceController controller = controller("unknown");

        assertThat(controller.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        assertThat(controller.getSummary().toString())
                .isEqualTo(mContext.getString(R.string.device_info_default));
        assertThat(controller.getSummary().toString()).isEqualTo("Unknown");
    }
}
// LINT.ThenChange(FirmwareReleasePreferenceTest.kt)

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

// LINT.IfChange
import android.content.Context;
import android.os.SystemProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * The device maker's firmware release the phone runs, shown next to the vendor security update.
 * The device's vendor partition sets the property: the release in the maker's own form, "mixed"
 * when the firmware parts come from different releases, or "unknown" when one matches no release.
 * The row is hidden while the property is empty and when it holds anything else. A device overlay
 * may name its maker in the row's title.
 */
public class FirmwareReleasePreferenceController extends BasePreferenceController {

    static final String FIRMWARE_RELEASE_PROPERTY = "ro.vendor.diamaneos.firmware_release";
    static final String MIXED = "mixed";
    static final String UNKNOWN = "unknown";
    // A release in any maker's form: one short token of letters, digits, dots, plus, minus and
    // underscores, starting with a letter or digit.
    private static final String RELEASE = "[0-9A-Za-z][0-9A-Za-z._+-]{0,39}";

    private final String mRelease;

    public FirmwareReleasePreferenceController(Context context, String key) {
        super(context, key);
        mRelease = getFirmwareRelease(context);
    }

    @Override
    public int getAvailabilityStatus() {
        return mRelease != null ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return mRelease;
    }

    /** Returns the firmware release to show, or null to hide the row. */
    public static @Nullable String getFirmwareRelease(@NonNull Context context) {
        return describe(context, SystemProperties.get(FIRMWARE_RELEASE_PROPERTY));
    }

    /**
     * The text for a property value: "Mixed releases", the translated "Unknown" or the release
     * itself. Null for an empty or malformed value.
     */
    static @Nullable String describe(@NonNull Context context, @Nullable String value) {
        if (value == null) {
            return null;
        }
        if (MIXED.equals(value)) {
            return context.getString(R.string.tally_firmware_release_mixed);
        }
        if (UNKNOWN.equals(value)) {
            return context.getString(R.string.device_info_default);
        }
        if (value.matches(RELEASE)) {
            return value;
        }
        return null;
    }
}
// LINT.ThenChange(FirmwareReleasePreference.kt)

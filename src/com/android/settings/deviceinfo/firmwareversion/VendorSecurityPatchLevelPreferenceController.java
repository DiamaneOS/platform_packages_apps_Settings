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
import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.core.BasePreferenceController;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;

/**
 * The vendor security patch level, shown next to the Android security update. The row is hidden
 * when the vendor partition sets no patch level or one that is not a real date.
 */
public class VendorSecurityPatchLevelPreferenceController extends BasePreferenceController {

    static final String VENDOR_SECURITY_PATCH_PROPERTY = "ro.vendor.build.security_patch";

    private final String mCurrentPatch;

    public VendorSecurityPatchLevelPreferenceController(Context context, String key) {
        super(context, key);
        mCurrentPatch = getVendorSecurityPatch(Locale.getDefault());
    }

    @Override
    public int getAvailabilityStatus() {
        return mCurrentPatch != null ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return mCurrentPatch;
    }

    /** Returns the vendor security patch level in the given locale, or null. */
    public static @Nullable String getVendorSecurityPatch(@NonNull Locale locale) {
        return formatPatch(SystemProperties.get(VENDOR_SECURITY_PATCH_PROPERTY), locale);
    }

    /**
     * Formats a YYYY-MM-DD patch level as SettingsLib's DeviceInfoUtils.getSecurityPatch formats
     * the Android one. Returns null when the value is empty or not a real date.
     */
    static @Nullable String formatPatch(@Nullable String patch, @NonNull Locale locale) {
        if (patch == null || !patch.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            return null;
        }
        try {
            // Strict check first: SimpleDateFormat would roll 2026-02-30 over into March.
            LocalDate.parse(patch);
            SimpleDateFormat template = new SimpleDateFormat("yyyy-MM-dd");
            Date patchDate = template.parse(patch);
            String format = DateFormat.getBestDateTimePattern(locale, "dMMMMyyyy");
            return DateFormat.format(format, patchDate).toString();
        } catch (DateTimeParseException | ParseException e) {
            return null;
        }
    }
}
// LINT.ThenChange(VendorSecurityPatchLevelPreference.kt)

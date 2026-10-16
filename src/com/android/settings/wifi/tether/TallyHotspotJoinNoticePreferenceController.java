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

package com.android.settings.wifi.tether;

import static com.android.settings.wifi.WifiUtils.canShowWifiHotspot;

import android.content.Context;
import android.os.UserManager;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.wifi.WifiEnterpriseRestrictionUtils;

/**
 * Switch for a notification when a device joins the Wi-Fi hotspot. Off by default.
 *
 * <p>Stores the choice only. SystemUI posts the notification. Shown to an admin user who may
 * set up the hotspot, the rule SystemUI applies too.
 */
public class TallyHotspotJoinNoticePreferenceController extends TogglePreferenceController {

    public TallyHotspotJoinNoticePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!canShowWifiHotspot(mContext)
                || !WifiEnterpriseRestrictionUtils.isWifiTetheringAllowed(mContext)) {
            return DISABLED_FOR_USER;
        }
        final UserManager userManager = mContext.getSystemService(UserManager.class);
        if (userManager == null || !userManager.isAdminUser()
                || userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING)) {
            return DISABLED_FOR_USER;
        }
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TALLY_HOTSPOT_JOIN_NOTICE, 0) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.TALLY_HOTSPOT_JOIN_NOTICE, isChecked ? 1 : 0);
    }

    @Override
    public boolean isSliceable() {
        // Changed on the hotspot page only.
        return false;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_network;
    }
}

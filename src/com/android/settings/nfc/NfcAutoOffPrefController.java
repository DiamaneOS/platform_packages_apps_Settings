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

package com.android.settings.nfc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.ext.settings.ExtSettings;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.ext.AutoOffSetting;
import com.android.settings.ext.IntSettingPrefController;
import com.android.settings.ext.RadioButtonPickerFragment2;

/** NFC page: the NFC_AUTO_OFF timeout, like BluetoothAutoOffPrefController. */
public class NfcAutoOffPrefController extends IntSettingPrefController {

    public NfcAutoOffPrefController(Context ctx, String key) {
        super(ctx, key, ExtSettings.NFC_AUTO_OFF);
    }

    @Override
    public int getAvailabilityStatus() {
        int r = super.getAvailabilityStatus();
        if (r == AVAILABLE) {
            return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC) ?
                    AVAILABLE : UNSUPPORTED_ON_DEVICE;
        }
        return r;
    }

    @Override
    public void addPrefsBeforeList(RadioButtonPickerFragment2 fragment, PreferenceScreen screen) {
        addFooterPreference(screen, R.string.tally_nfc_auto_off_footer);
    }

    @Override
    protected void getEntries(Entries entries) {
        AutoOffSetting.getEntries(entries);
    }
}

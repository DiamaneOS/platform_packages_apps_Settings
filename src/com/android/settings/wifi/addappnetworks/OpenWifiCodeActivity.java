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

package com.android.settings.wifi.addappnetworks;

import android.content.Intent;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * DiamaneOS: "Open with" for a scanned Wi-Fi code. The camera app's QR result offers the apps
 * that open its text as a link, so a "WIFI:" code reaches Settings without the camera app
 * holding a Wi-Fi permission and without the user retyping the password.
 * - Shows the "Save this network?" panel of ACTION_WIFI_ADD_NETWORKS, with its checks: nothing
 *   is saved or joined before the user taps Save. Any app can already ask for that panel, so
 *   this adds no new way in. Not browsable, so web pages can't open it.
 * - Takes only the code from the caller's intent.
 */
public class OpenWifiCodeActivity extends AddAppNetworksActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setIntent(toAddNetworksIntent(getIntent()));
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(toAddNetworksIntent(intent));
    }

    /**
     * AddAppNetworksFragment closes the panel without a word when the network is already saved.
     * Show the Wi-Fi networks then, so that "Open with" does not look as if it did nothing.
     */
    @Override
    public void finish() {
        final Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(AddAppNetworksActivity.TAG);
        if (fragment instanceof AddAppNetworksFragment) {
            final List<Integer> results = ((AddAppNetworksFragment) fragment).mResultCodeArrayList;
            if (results != null
                    && results.contains(AddAppNetworksFragment.RESULT_NETWORK_ALREADY_EXISTS)) {
                startActivity(
                        new Intent(Settings.ACTION_WIFI_SETTINGS).setPackage(getPackageName()));
            }
        }
        super.finish();
    }

    /** The scanned code as the request AddAppNetworksActivity reads: a list of one network. */
    private Intent toAddNetworksIntent(Intent intent) {
        final Intent request = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
        final WifiNetworkSuggestion network = Intent.ACTION_VIEW.equals(intent.getAction())
                ? toNetwork(WifiCode.parse(intent.getDataString())) : null;
        if (network == null) {
            // Without a list AddAppNetworksFragment closes the panel.
            Toast.makeText(this, R.string.wifi_dpp_qr_code_is_not_valid_format, Toast.LENGTH_LONG)
                    .show();
            return request;
        }
        final ArrayList<WifiNetworkSuggestion> networks = new ArrayList<>();
        networks.add(network);
        return request.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, networks);
    }

    @Nullable
    private static WifiNetworkSuggestion toNetwork(@Nullable WifiCode code) {
        if (code == null) {
            return null;
        }
        try {
            final WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder()
                    .setSsid(code.ssid)
                    .setIsHiddenSsid(code.hidden);
            if (code.security == WifiCode.WPA) {
                builder.setWpa2Passphrase(code.password);
            } else if (code.security == WifiCode.SAE) {
                builder.setWpa3Passphrase(code.password);
            }
            return builder.build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            // The builder takes no password with characters outside ASCII.
            return null;
        }
    }
}

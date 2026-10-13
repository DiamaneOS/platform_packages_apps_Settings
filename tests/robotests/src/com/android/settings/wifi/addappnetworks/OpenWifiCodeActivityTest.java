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

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiNetworkSuggestion;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public class OpenWifiCodeActivityTest {

    private static Intent openWith(String code) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(code));
    }

    /** The request the activity hands to AddAppNetworksActivity. */
    private static Intent requestFor(Intent openWith) {
        return Robolectric.buildActivity(FakeOpenWifiCodeActivity.class, openWith)
                .create().get().getIntent();
    }

    @Test
    public void wpa3CodeOfHiddenNetwork_asksToSaveThatNetwork() {
        Intent request = requestFor(openWith("WIFI:T:SAE;S:mynetwork;P:mypass123;H:true;;"));

        assertThat(request.getAction()).isEqualTo(Settings.ACTION_WIFI_ADD_NETWORKS);
        ArrayList<WifiNetworkSuggestion> networks =
                request.getParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST);
        assertThat(networks).hasSize(1);
        WifiConfiguration network = networks.get(0).getWifiConfiguration();
        assertThat(network.SSID).isEqualTo("\"mynetwork\"");
        assertThat(network.preSharedKey).isEqualTo("\"mypass123\"");
        assertThat(network.getAuthType()).isEqualTo(WifiConfiguration.KeyMgmt.SAE);
        assertThat(network.hiddenSSID).isTrue();
        assertThat(ShadowToast.shownToastCount()).isEqualTo(0);
    }

    @Test
    public void codeSettingsCantSave_saysSoAndAsksNothing() {
        // The password has a character outside ASCII.
        Intent request = requestFor(openWith("WIFI:T:WPA;S:mynetwork;P:mypäss123;;"));

        assertThat(request.hasExtra(Settings.EXTRA_WIFI_NETWORK_LIST)).isFalse();
        assertThat(ShadowToast.shownToastCount()).isEqualTo(1);
    }

    @Test
    public void networkListFromTheCaller_isIgnored() {
        ArrayList<WifiNetworkSuggestion> networks = new ArrayList<>();
        networks.add(new WifiNetworkSuggestion.Builder().setSsid("other").build());
        Intent openWith = openWith("https://example.com/")
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, networks);

        assertThat(requestFor(openWith).hasExtra(Settings.EXTRA_WIFI_NETWORK_LIST)).isFalse();
    }

    private static class FakeOpenWifiCodeActivity extends OpenWifiCodeActivity {
        @Override
        boolean isAddWifiConfigAllow() {
            return true;
        }
    }
}

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
import static com.android.settings.wifi.WifiUtils.isWifiMultiuserEnabled;

import android.content.Context;
import android.net.LinkAddress;
import android.net.TetheredClient;
import android.net.TetheringManager;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiManager;
import android.os.UserManager;
import android.text.BidiFormatter;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settingslib.wifi.WifiEnterpriseRestrictionUtils;
import com.android.settingslib.wifi.WifiUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Lists the devices connected to the Wi-Fi hotspot while the hotspot is on.
 *
 * <p>Names and addresses are shown only: never logged or stored, and dropped when the page stops.
 */
public class TallyHotspotClientsPreferenceController extends BasePreferenceController
        implements LifecycleObserver, OnStart, OnStop {
    private static final String KEY_NOTE = "tally_hotspot_clients_note";

    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    /** Rows by MAC address. Rows have no preference key, so no key holds an address. */
    private final Map<String, Preference> mRows = new HashMap<>();

    private PreferenceGroup mCategory;
    private WifiTetherSoftApManager mSoftApManager;
    private boolean mListening;
    private int mApState = WifiManager.WIFI_AP_STATE_DISABLED;
    private List<String> mConnectedMacs = Collections.emptyList();
    private List<TallyHotspotClients.Lease> mLeases = Collections.emptyList();

    private final WifiTetherSoftApManager.WifiTetherSoftApCallback mSoftApCallback =
            new WifiTetherSoftApManager.WifiTetherSoftApCallback() {
                @Override
                public void onStateChanged(int state, int failureReason) {
                    if (!mListening) return;
                    mApState = state;
                    if (state != WifiManager.WIFI_AP_STATE_ENABLED) {
                        mConnectedMacs = Collections.emptyList();
                    }
                    refresh();
                }

                @Override
                public void onConnectedClientsChanged(List<WifiClient> clients) {
                    if (!mListening) return;
                    final List<String> macs = new ArrayList<>();
                    if (clients != null) {
                        for (WifiClient client : clients) {
                            macs.add(client.getMacAddress().toString());
                        }
                    }
                    mConnectedMacs = macs;
                    refresh();
                }
            };

    private final TetheringManager.TetheringEventCallback mLeaseCallback =
            new TetheringManager.TetheringEventCallback() {
                @Override
                public void onClientsChanged(@NonNull Collection<TetheredClient> clients) {
                    if (!mListening) return;
                    final List<TallyHotspotClients.Lease> leases = new ArrayList<>();
                    for (TetheredClient client : clients) {
                        final String mac = client.getMacAddress().toString();
                        for (TetheredClient.AddressInfo info : client.getAddresses()) {
                            final LinkAddress address = info.getAddress();
                            leases.add(new TallyHotspotClients.Lease(mac,
                                    address.getAddress().getHostAddress(), info.getHostname(),
                                    address.getExpirationTime()));
                        }
                    }
                    mLeases = leases;
                    refresh();
                }
            };

    public TallyHotspotClientsPreferenceController(Context context, String key) {
        super(context, key);
        mWifiManager = context.getApplicationContext().getSystemService(WifiManager.class);
        mTetheringManager =
                context.getApplicationContext().getSystemService(TetheringManager.class);
        if (mWifiManager != null) {
            mApState = mWifiManager.getWifiApState();
        }
    }

    @Override
    public int getAvailabilityStatus() {
        if (!canListClients()) {
            return DISABLED_FOR_USER;
        }
        return mApState == WifiManager.WIFI_AP_STATE_ENABLED
                ? AVAILABLE_UNSEARCHABLE : CONDITIONALLY_UNAVAILABLE;
    }

    /** Same conditions as the hotspot page's own settings. */
    private boolean canListClients() {
        if (mWifiManager == null || mTetheringManager == null) return false;
        if (!canShowWifiHotspot(mContext)) return false;
        if (!WifiEnterpriseRestrictionUtils.isWifiTetheringAllowed(mContext)) return false;
        final UserManager userManager = mContext.getSystemService(UserManager.class);
        if (userManager == null
                || userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING)) {
            return false;
        }
        return isWifiMultiuserEnabled() || userManager.isAdminUser();
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        // A rebuilt screen has none of the old rows.
        mCategory = screen.findPreference(getPreferenceKey());
        mRows.clear();
        refresh();
    }

    @Override
    public void onStart() {
        if (mListening || mCategory == null || !canListClients()) return;
        mListening = true;
        // Both report the current state right after registering.
        mSoftApManager = new WifiTetherSoftApManager(mWifiManager, mSoftApCallback);
        mSoftApManager.registerSoftApCallback();
        mTetheringManager.registerTetheringEventCallback(mContext.getMainExecutor(),
                mLeaseCallback);
    }

    @Override
    public void onStop() {
        if (!mListening) return;
        mListening = false;
        mSoftApManager.unRegisterSoftApCallback();
        mSoftApManager = null;
        mTetheringManager.unregisterTetheringEventCallback(mLeaseCallback);
        mConnectedMacs = Collections.emptyList();
        mLeases = Collections.emptyList();
    }

    private void refresh() {
        if (mCategory == null) return;
        final boolean hotspotOn = isAvailable();
        mCategory.setVisible(hotspotOn);
        final List<TallyHotspotClients.Client> clients = hotspotOn
                ? TallyHotspotClients.list(mConnectedMacs, mLeases)
                : Collections.<TallyHotspotClients.Client>emptyList();
        if (hotspotOn) {
            mCategory.setTitle(
                    WifiUtils.getWifiTetherSummaryForConnectedDevices(mContext, clients.size()));
        }

        final Map<String, Preference> gone = new HashMap<>(mRows);
        int order = 0;
        for (TallyHotspotClients.Client client : clients) {
            gone.remove(client.mac);
            Preference row = mRows.get(client.mac);
            if (row == null) {
                row = new Preference(mCategory.getContext());
                row.setPersistent(false);
                row.setSelectable(false);
                row.setOrder(order);
                mRows.put(client.mac, row);
                mCategory.addPreference(row);
            } else {
                row.setOrder(order);
            }
            order++;
            row.setTitle(client.name != null
                    ? BidiFormatter.getInstance().unicodeWrap(client.name)
                    : mContext.getString(R.string.virtual_device_unknown));
            row.setSummary(client.address != null
                    ? mContext.getString(R.string.tally_hotspot_client_address, client.address,
                            client.mac)
                    : client.mac);
        }
        for (Iterator<Map.Entry<String, Preference>> it = gone.entrySet().iterator();
                it.hasNext(); ) {
            final Map.Entry<String, Preference> entry = it.next();
            mCategory.removePreference(entry.getValue());
            mRows.remove(entry.getKey());
        }

        final Preference note = mCategory.findPreference(KEY_NOTE);
        if (note != null) {
            note.setVisible(!clients.isEmpty());
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.android.settings.network;

import android.app.settings.SettingsEnums;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import de.diamaneos.downloads.DownloadPolicy;
import de.diamaneos.downloads.DownloadPolicyClient;

/** Both application links and setup use this page and the same global value. */
public final class DownloadServersFragment extends DashboardFragment {
    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_NETWORK_CATEGORY; }
    @Override protected String getLogTag() { return "DownloadServers"; }
    @Override protected int getPreferenceScreenResId() { return R.xml.download_servers; }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        DownloadPolicy policy = DownloadPolicyClient.read(requireContext());
        ListPreference server = findPreference("download_server");
        SwitchPreferenceCompat fallback = findPreference("download_fallback");
        Preference footer = findPreference("download_status");
        boolean writable = DownloadPolicyClient.canWrite(requireContext());
        server.setValue(policy.server);
        fallback.setChecked(policy.fallback);
        server.setEnabled(writable);
        fallback.setEnabled(writable);
        footer.setVisible(!writable);
        footer.setTitle(R.string.download_servers_main_user_only);
        server.setOnPreferenceChangeListener((preference, value) -> save(value, fallback.isChecked()));
        fallback.setOnPreferenceChangeListener((preference, value) -> save(server.getValue(), value));
    }

    private boolean save(Object server, Object fallback) {
        if (!DownloadPolicyClient.canWrite(requireContext())) return false;
        try {
            DownloadPolicy policy = DownloadPolicy.require(DownloadPolicy.VERSION, server, fallback);
            if (DownloadPolicyClient.write(requireContext(), policy)) return true;
        } catch (RuntimeException e) {
            // Keep the displayed choice unchanged when the platform refuses the write.
        }
        Preference footer = findPreference("download_status");
        footer.setVisible(true);
        footer.setTitle(R.string.download_servers_unavailable);
        return false;
    }
}

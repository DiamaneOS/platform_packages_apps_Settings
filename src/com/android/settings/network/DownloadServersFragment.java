// SPDX-License-Identifier: Apache-2.0
package com.android.settings.network;

import android.app.settings.SettingsEnums;
import java.io.IOException;
import androidx.preference.Preference;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settingslib.widget.SelectorWithWidgetPreference;
import de.diamaneos.downloads.DownloadPolicy;
import de.diamaneos.downloads.DownloadPolicyClient;

/** Application links use this page and the same global value. */
public final class DownloadServersFragment extends DashboardFragment
        implements SelectorWithWidgetPreference.OnClickListener {
    private static final String KEY_PREFIX = "download_server_";
    private static final String[] MODES =
            {DownloadPolicy.AUTOMATIC, DownloadPolicy.PRIMARY, DownloadPolicy.CANADA};

    @Override public int getMetricsCategory() { return SettingsEnums.SETTINGS_NETWORK_CATEGORY; }
    @Override protected String getLogTag() { return "DownloadServers"; }
    @Override protected int getPreferenceScreenResId() { return R.xml.download_servers; }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Preference footer = findPreference("download_status");
        final DownloadPolicy policy;
        try {
            policy = DownloadPolicyClient.read(requireContext());
        } catch (IOException e) {
            show(null, false);
            footer.setVisible(true);
            footer.setTitle(R.string.download_servers_unavailable);
            return;
        }
        boolean writable = DownloadPolicyClient.canWrite(requireContext());
        show(policy.mode, writable);
        footer.setVisible(!writable);
        footer.setTitle(R.string.download_servers_main_user_only);
    }

    private void show(String selected, boolean enabled) {
        for (String mode : MODES) {
            SelectorWithWidgetPreference option = findPreference(KEY_PREFIX + mode);
            option.setChecked(mode.equals(selected));
            option.setEnabled(enabled);
            option.setOnClickListener(this);
        }
    }

    @Override
    public void onRadioButtonClicked(SelectorWithWidgetPreference option) {
        if (save(option.getKey().substring(KEY_PREFIX.length()))) refresh();
    }

    private boolean save(String mode) {
        if (!DownloadPolicyClient.canWrite(requireContext())) return false;
        try {
            if (DownloadPolicyClient.write(requireContext(), DownloadPolicy.require(mode))) return true;
        } catch (RuntimeException e) {
            // Keep the displayed choice unchanged when the platform refuses the write.
        }
        Preference footer = findPreference("download_status");
        footer.setVisible(true);
        footer.setTitle(R.string.download_servers_unavailable);
        return false;
    }
}

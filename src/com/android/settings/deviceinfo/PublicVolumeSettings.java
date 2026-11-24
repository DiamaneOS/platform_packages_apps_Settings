/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import static com.android.settings.flags.Flags.enableRenameInPublicVolumeSettings;

import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.VolumeActions;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.util.Preconditions;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.deviceinfo.storage.StorageRenameFragment;
import com.android.settings.deviceinfo.storage.StorageUtils;
import com.android.settings.deviceinfo.storage.StorageUtils.MountTask;
import com.android.settings.deviceinfo.storage.StorageUtils.UnmountTask;
import com.android.settingslib.widget.UsageProgressBarPreference;

import java.io.File;
import java.util.Objects;

/**
 * Panel showing summary and actions for a {@link VolumeInfo#TYPE_PUBLIC}
 * storage volume.
 */
public class PublicVolumeSettings extends SettingsPreferenceFragment {
    // TODO: disable unmount when providing over MTP/PTP

    private StorageManager mStorageManager;

    private String mVolumeId;
    private VolumeInfo mVolume;
    private final View.OnClickListener mUnmountListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            new UnmountTask(getActivity(), mVolume).execute();
        }
    };
    private DiskInfo mDisk;
    private UsageProgressBarPreference mSummary;
    private Preference mMount;
    private Preference mFormatPublic;
    // DiamaneOS: the volume's status outside its ready state, and Explore (VolumeActions)
    private Preference mStatus;
    private Preference mBrowse;
    @Nullable private Preference mRename;
    private Button mUnmount;
    private final StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            if (Objects.equals(mVolume.getId(), vol.getId())) {
                mVolume = vol;
                update();
            }
        }

        @Override
        public void onVolumeRecordChanged(VolumeRecord rec) {
            if (Objects.equals(mVolume.getFsUuid(), rec.getFsUuid())) {
                mVolume = mStorageManager.findVolumeById(mVolumeId);
                update();
            }
        }
    };
    private boolean mIsPermittedToAdopt;

    private boolean isVolumeValid() {
        return (mVolume != null) && (mVolume.getType() == VolumeInfo.TYPE_PUBLIC)
                && VolumeActions.showsPage(mVolume.getState());  // DiamaneOS: recoverable too
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PUBLIC_VOLUME_SETTINGS;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Context context = getActivity();

        mIsPermittedToAdopt = UserManager.get(context).isAdminUser()
                && !ActivityManager.isUserAMonkey();

        mStorageManager = context.getSystemService(StorageManager.class);

        if (DocumentsContract.ACTION_DOCUMENT_ROOT_SETTINGS.equals(
                getActivity().getIntent().getAction())) {
            final Uri rootUri = getActivity().getIntent().getData();
            final String fsUuid = DocumentsContract.getRootId(rootUri);
            mVolume = mStorageManager.findVolumeByUuid(fsUuid);
        } else {
            String volId = getArguments() != null
                    ? getArguments().getString(VolumeInfo.EXTRA_VOLUME_ID) : null;
            if (volId == null) {
                // DiamaneOS: SystemUI's storage notices name the volume in the activity's intent.
                volId = getActivity().getIntent().getStringExtra(VolumeInfo.EXTRA_VOLUME_ID);
            }
            if (volId != null) {
                mVolume = mStorageManager.findVolumeById(volId);
            }
        }

        if (!isVolumeValid()) {
            getActivity().finish();
            return;
        }

        mDisk = mStorageManager.findDiskById(mVolume.getDiskId());
        Preconditions.checkNotNull(mDisk);

        mVolumeId = mVolume.getId();

        addPreferencesFromResource(R.xml.device_info_storage_volume);
        getPreferenceScreen().setOrderingAsAdded(true);

        mSummary = new UsageProgressBarPreference(getPrefContext());

        mMount = buildAction(R.string.storage_menu_mount);
        mUnmount = new Button(getActivity());
        mUnmount.setText(R.string.storage_menu_unmount);
        mUnmount.setOnClickListener(mUnmountListener);
        mFormatPublic = buildAction(R.string.storage_menu_format_option);
        mStatus = buildAction(com.android.internal.R.string.ext_media_status_mounted);
        mStatus.setSelectable(false);
        mBrowse = buildAction(com.android.internal.R.string.ext_media_browse_action);

        if (enableRenameInPublicVolumeSettings()) {
            mRename = buildAction(R.string.storage_menu_rename);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // If the volume isn't valid, we are not scaffolded to set up a view.
        if (!isVolumeValid()) {
            return;
        }

        final ViewGroup container = getActivity().findViewById(R.id.container_material);
        container.addView(mUnmount, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void update() {
        if (!isVolumeValid()) {
            getActivity().finish();
            return;
        }

        getActivity().setTitle(mStorageManager.getBestVolumeDescription(mVolume));

        final Context context = getActivity();
        final PreferenceScreen screen = getPreferenceScreen();

        screen.removeAll();

        // DiamaneOS: outside its ready state the page says what state the volume is in. The
        // actions are those of its state (VolumeActions), as in its notice: Mount again, Explore
        // when readable, Eject when mounted. Never Format for a read-only or recoverable volume.
        if (mVolume.getState() != VolumeInfo.STATE_MOUNTED) {
            mStatus.setTitle(VolumeActions.statusText(mVolume));
            addPreference(mStatus);
        }

        if (mVolume.isMountedReadable()) {
            addPreference(mSummary);

            final File file = mVolume.getPath();
            final long totalBytes = file.getTotalSpace();
            final long freeBytes = file.getFreeSpace();
            final long usedBytes = totalBytes - freeBytes;

            mSummary.setUsageSummary(StorageUtils.getStorageSummary(
                    context, R.string.storage_usage_summary, usedBytes));
            mSummary.setTotalSummary(StorageUtils.getStorageSummary(
                    context, R.string.storage_total_summary, totalBytes));
            mSummary.setPercent(usedBytes, totalBytes);
        }

        if (VolumeActions.allows(mVolume, VolumeActions.RETRY)) {
            addPreference(mMount);
        }
        if (VolumeActions.allows(mVolume, VolumeActions.BROWSE)) {
            addPreference(mBrowse);
        }
        // Shown again once the volume is mounted after a retry.
        mUnmount.setVisibility(VolumeActions.allows(mVolume, VolumeActions.EJECT)
                ? View.VISIBLE : View.GONE);
        // DiamaneOS: a fresh confirmation can replace a finished format awaiting verification.
        if (VolumeActions.allows(mVolume, VolumeActions.FORMAT) || canOfferEraseAgain()) {
            addPreference(mFormatPublic);
        }

        if (enableRenameInPublicVolumeSettings() && mRename != null) {
            addPreference(mRename);
        }
    }

    private boolean canOfferEraseAgain() {
        return StorageErase.canOfferRetry(mStorageManager, mVolume);
    }

    private void addPreference(Preference pref) {
        pref.setOrder(Preference.DEFAULT_ORDER);
        getPreferenceScreen().addPreference(pref);
    }

    private Preference buildAction(int titleRes) {
        final Preference pref = new Preference(getPrefContext());
        pref.setTitle(titleRes);
        return pref;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Refresh to verify that we haven't been formatted away
        mVolume = mStorageManager.findVolumeById(mVolumeId);
        if (!isVolumeValid()) {
            getActivity().finish();
            return;
        }

        mStorageManager.registerListener(mStorageListener);
        update();
    }

    @Override
    public void onPause() {
        super.onPause();
        mStorageManager.unregisterListener(mStorageListener);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference pref) {
        final Intent intent = new Intent(getActivity(), StorageWizardInit.class);
        intent.putExtra(VolumeInfo.EXTRA_VOLUME_ID, mVolume.getId());
        if (pref == mMount) {
            new MountTask(getActivity(), mVolume).execute();
        } else if (pref == mBrowse) {
            startActivity(mVolume.buildBrowseIntent());  // DiamaneOS
        } else if (pref == mFormatPublic) {
            if (canOfferEraseAgain()) {
                StorageWizardFormatConfirm.showPublicRetry(getActivity(), mVolume.getDiskId());
            } else {
                startActivity(intent);
            }
        } else if (enableRenameInPublicVolumeSettings() && pref == mRename) {
            StorageRenameFragment.show(this, mVolume);
        }

        return super.onPreferenceTreeClick(pref);
    }
}

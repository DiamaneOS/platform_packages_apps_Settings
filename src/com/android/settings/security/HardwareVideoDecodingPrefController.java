/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.security;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.ext.IntSettingPrefController;
import com.android.settings.ext.RadioButtonPickerFragment2;

/**
 * Exploit protection > Hardware video decoding, on devices that can apply the choice: the entry
 * and its page with the three choices. Protected video only is shown disabled where the device
 * does not offer it.
 */
public class HardwareVideoDecodingPrefController extends IntSettingPrefController {

    public HardwareVideoDecodingPrefController(Context ctx, String key) {
        super(ctx, key, HardwareVideoDecoding.SETTING);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!HardwareVideoDecoding.isSupported(mContext)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return super.getAvailabilityStatus();
    }

    @Override
    protected void getEntries(Entries entries) {
        boolean offersProtected = HardwareVideoDecoding.offersProtectedVideo(mContext);
        if (offersProtected) {
            entries.add(R.string.tally_hw_video_decoding_protected_title,
                    R.string.tally_hw_video_decoding_protected_summary,
                    HardwareVideoDecoding.PROTECTED_VIDEO);
        } else {
            entries.add(getText(R.string.tally_hw_video_decoding_protected_unavailable_title),
                    getText(R.string.screensaver_unavailable_due_to_mode),
                    HardwareVideoDecoding.PROTECTED_VIDEO, false);
        }
        entries.add(R.string.tally_hw_video_decoding_all_title,
                R.string.tally_hw_video_decoding_all_summary,
                HardwareVideoDecoding.ALL_VIDEO);
        if (offersProtected) {
            entries.add(R.string.tally_hw_video_decoding_never_title,
                    R.string.tally_hw_video_decoding_never_summary,
                    HardwareVideoDecoding.NEVER);
        } else {
            entries.add(R.string.tally_hw_video_decoding_never_default_title,
                    R.string.tally_hw_video_decoding_never_summary_without_protected,
                    HardwareVideoDecoding.NEVER);
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        // The entry names the choice; say so while the running boot still has another state.
        CharSequence choice = preference.getSummary();
        if (choice != null && HardwareVideoDecoding.isRestartPending(mContext)) {
            preference.setSummary(mContext.getString(
                    R.string.tally_hw_video_decoding_summary_after_restart, choice));
        }
    }

    @Override
    protected boolean setValue(int val) {
        boolean stored = super.setValue(val);
        // The choice is a system property, which has no observer: rebuild the page once the
        // tap is handled, so the restart note follows the choice.
        RadioButtonPickerFragment2 page = fragment;
        if (stored && page != null) {
            mContext.getMainThreadHandler().post(() -> {
                if (page.isAdded()) {
                    page.updateCandidates();
                }
            });
        }
        return stored;
    }

    @Override
    public void addPrefsBeforeList(RadioButtonPickerFragment2 fragment, PreferenceScreen screen) {
        // Shown until the choice matches the running boot again.
        if (HardwareVideoDecoding.isRestartPending(mContext)) {
            Preference note = new Preference(screen.getContext());
            note.setPersistent(false);
            note.setSelectable(false);
            note.setTitle(R.string.bluetooth_disable_hw_offload_dialog_message);
            screen.addPreference(note);
        }
    }

    @Override
    public void addPrefsAfterList(RadioButtonPickerFragment2 fragment, PreferenceScreen screen) {
        addFooterPreference(screen, HardwareVideoDecoding.offersProtectedVideo(mContext)
                ? R.string.tally_hw_video_decoding_footer
                : R.string.tally_hw_video_decoding_footer_without_protected);
    }
}

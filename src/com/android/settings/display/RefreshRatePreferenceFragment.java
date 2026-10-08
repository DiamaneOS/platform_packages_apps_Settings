/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settingslib.widget.CandidateInfo;
import com.android.settingslib.widget.TopIntroPreference;

import java.util.ArrayList;
import java.util.List;

/** DiamaneOS: the choices for Display > Maximum refresh rate; see {@link RefreshRates}. */
public class RefreshRatePreferenceFragment extends RadioButtonPickerFragment {
    private static final String KEY_PREFIX = "refresh_rate_";

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.refresh_rate_settings;
    }

    @Override
    protected void addStaticPreferences(PreferenceScreen screen) {
        TopIntroPreference intro = new TopIntroPreference(screen.getContext());
        intro.setTitle(R.string.tally_refresh_rate_intro);
        screen.addPreference(intro);
    }

    @Override
    protected List<? extends CandidateInfo> getCandidates() {
        Context context = getContext();
        List<RateCandidate> candidates = new ArrayList<>();
        for (int rate : RefreshRates.supported(context)) {
            candidates.add(new RateCandidate(
                    context.getString(R.string.tally_refresh_rate_option, rate), KEY_PREFIX + rate));
        }
        return candidates;
    }

    @Override
    protected String getDefaultKey() {
        return KEY_PREFIX + RefreshRates.current(getContext());
    }

    @Override
    protected boolean setDefaultKey(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) return false;
        try {
            RefreshRates.set(getContext(), Integer.parseInt(key.substring(KEY_PREFIX.length())));
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return RefreshRateScreen.KEY;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    static class RateCandidate extends CandidateInfo {
        private final CharSequence mLabel;
        private final String mKey;

        RateCandidate(CharSequence label, String key) {
            super(true);
            mLabel = label;
            mKey = key;
        }

        @Override
        public CharSequence loadLabel() {
            return mLabel;
        }

        @Override
        public Drawable loadIcon() {
            return null;
        }

        @Override
        public String getKey() {
            return mKey;
        }
    }
}

/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settings.widget;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.flags.Flags;
import com.android.settingslib.widget.SettingsThemeHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Helper for homepage preference to manage layout. */
public class HomepagePreferenceLayoutHelper {

    /**
     * Tally: the state lamp beside a homepage entry's summary. It shows only what the entry's
     * controller already reads, and only beside the summary that says it in words.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({LAMP_NONE, LAMP_OFF, LAMP_ON})
    public @interface LampState {}

    /** No lamp. */
    public static final int LAMP_NONE = 0;
    /** The outline ring: off. */
    public static final int LAMP_OFF = 1;
    /** The lit disc: on. */
    public static final int LAMP_ON = 2;

    private final Preference mPreference;
    private @LampState int mLampState = LAMP_NONE;

    private View mIcon;
    private View mText;
    private View mAlertFrame;
    private View mAlertUnnumbered;
    private View mAlertNumberedFrame;
    private TextView mAlertNumberText;
    private boolean mIconVisible = true;
    private int mIconPaddingStart = -1;
    private int mTextPaddingStart = -1;
    private int mAlertValue = -1;

    /** The interface for managing preference layouts on homepage */
    public interface HomepagePreferenceLayout {
        /** Returns a {@link HomepagePreferenceLayoutHelper}  */
        HomepagePreferenceLayoutHelper getHelper();

        /** Tally: sets the entry's state lamp and redraws the entry if it changed. */
        void setLampState(@LampState int state);
    }

    /** Tally: sets the state lamp of a homepage entry; does nothing for other preferences. */
    public static void setLamp(@Nullable Preference preference, @LampState int state) {
        if (preference instanceof HomepagePreferenceLayout) {
            ((HomepagePreferenceLayout) preference).setLampState(state);
        }
    }

    public HomepagePreferenceLayoutHelper(Preference preference) {
        mPreference = preference;
        // Tally: the expressive homepage (the phone's) uses Tally's row.
        preference.setLayoutResource(
                SettingsThemeHelper.isExpressiveTheme(preference.getContext())
                        ? R.layout.homepage_preference_tally
                        : R.layout.homepage_preference);
    }

    /** Sets whether the icon should be visible */
    public void setIconVisible(boolean visible) {
        mIconVisible = visible;
        if (mIcon != null) {
            mIcon.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** Sets the icon padding start */
    public void setIconPaddingStart(int paddingStart) {
        mIconPaddingStart = paddingStart;
        if (mIcon != null && paddingStart >= 0) {
            mIcon.setPaddingRelative(paddingStart, mIcon.getPaddingTop(), mIcon.getPaddingEnd(),
                    mIcon.getPaddingBottom());
        }
    }

    /** Sets the text padding start */
    public void setTextPaddingStart(int paddingStart) {
        mTextPaddingStart = paddingStart;
        if (mText != null && paddingStart >= 0) {
            mText.setPaddingRelative(paddingStart, mText.getPaddingTop(), mText.getPaddingEnd(),
                    mText.getPaddingBottom());
        }
    }

    /** Sets the alert value and view */
    public void setAlert(int value) {
        if (Flags.homepageTileAlert()) {
            mAlertValue = value;
            if (mAlertFrame != null && mAlertUnnumbered != null
                    && mAlertNumberedFrame != null && mAlertNumberText != null) {
                mAlertFrame.setVisibility((value > 0) ? View.VISIBLE : View.GONE);
                // only display number if it's single digit, more than 1
                if (value == 1 || value > 9) {
                    mAlertNumberedFrame.setVisibility(View.GONE);
                    mAlertUnnumbered.setVisibility(View.VISIBLE);
                    mAlertFrame.setContentDescription(mAlertFrame.getResources()
                            .getString(R.string.homepage_unnumbered_alert_description));
                } else if (value > 1) {
                    mAlertUnnumbered.setVisibility(View.GONE);
                    mAlertNumberedFrame.setVisibility(View.VISIBLE);
                    mAlertNumberText.setVisibility(View.VISIBLE);
                    mAlertNumberText.setText(String.valueOf(value));
                    mAlertFrame.setContentDescription(mAlertFrame.getResources()
                            .getString(R.string.homepage_numbered_alert_description, value));
                }
            }
        }
    }

    /**
     * Tally: stores the state lamp, to be drawn at the next bind.
     *
     * @return whether it changed, so the preference redraws its row
     */
    public boolean setLampState(@LampState int state) {
        if (mLampState == state) {
            return false;
        }
        mLampState = state;
        return true;
    }

    void onBindViewHolder(PreferenceViewHolder holder) {
        mIcon = holder.findViewById(R.id.icon_frame);
        mText = holder.findViewById(R.id.text_frame);
        mAlertFrame = holder.findViewById(R.id.alert_frame);
        mAlertUnnumbered = holder.findViewById(R.id.alert_unnumbered);
        mAlertNumberedFrame = holder.findViewById(R.id.alert_numbered_frame);
        mAlertNumberText = (TextView) holder.findViewById(R.id.alert_number_fg);
        setIconVisible(mIconVisible);
        setIconPaddingStart(mIconPaddingStart);
        setTextPaddingStart(mTextPaddingStart);
        setAlert(mAlertValue);
        bindLamp(holder);
    }

    /**
     * Draws the lamp as the prototype's static glyph (10 dp), which changes at once both ways. A
     * lamp always carries its words: it shows only beside the controller's own summary, never
     * beside text that took its place (for example "Controlled by admin"). Screen readers read
     * the summary; the lamp itself is not announced.
     */
    private void bindLamp(PreferenceViewHolder holder) {
        final View lampView = holder.findViewById(R.id.tally_state_lamp);
        if (!(lampView instanceof ImageView)) {
            return;
        }
        final View summaryView = holder.findViewById(android.R.id.summary);
        final CharSequence summary = mPreference.getSummary();
        final boolean withWords = summaryView instanceof TextView
                && summaryView.getVisibility() == View.VISIBLE
                && !TextUtils.isEmpty(summary)
                && TextUtils.equals(((TextView) summaryView).getText(), summary);
        final int glyph;
        if (!withWords || mLampState == LAMP_NONE) {
            glyph = 0;
        } else if (mLampState == LAMP_ON) {
            glyph = R.drawable.tally_lamp_on_10;
        } else {
            glyph = R.drawable.tally_lamp_off_10;
        }
        final ImageView lamp = (ImageView) lampView;
        lamp.setImageResource(glyph);
        lamp.setVisibility(glyph != 0 ? View.VISIBLE : View.GONE);
    }
}

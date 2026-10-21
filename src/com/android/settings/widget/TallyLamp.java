/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.settings.widget.HomepagePreferenceLayoutHelper.HomepagePreferenceLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Tally: the state lamp of the Settings homepage entries. */
public class TallyLamp {

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

    /** Tally: sets the state lamp of a homepage entry. Does nothing for other preferences. */
    public static void setLamp(@Nullable Preference preference, @LampState int state) {
        if (preference instanceof HomepagePreferenceLayout) {
            ((HomepagePreferenceLayout) preference).setLampState(state);
        }
    }

    TallyLamp(Preference preference) {
        mPreference = preference;
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

    /**
     * Draws the lamp as the prototype's static glyph (10 dp), which changes at once both ways. A
     * lamp always carries its words: it shows only beside the controller's own summary, never
     * beside text that took its place (for example "Controlled by admin"). Screen readers read
     * the summary. The lamp itself is not announced.
     */
    void bindLamp(PreferenceViewHolder holder) {
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
            // The token library's glyphs: Settings-core uses the resource processor, so a
            // library's resources are in its own R class.
            glyph = de.diamaneos.tally.R.drawable.tally_lamp_on_10;
        } else {
            glyph = de.diamaneos.tally.R.drawable.tally_lamp_off_10;
        }
        final ImageView lamp = (ImageView) lampView;
        lamp.setImageResource(glyph);
        lamp.setVisibility(glyph != 0 ? View.VISIBLE : View.GONE);
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.DrawableRes;

import com.android.settings.R;
import com.android.settingslib.widget.DrawableStateLayout;

import java.util.Map;
import java.util.WeakHashMap;

/** Tally: the own backgrounds of highlighted drawable-state rows, for the highlight adapter. */
final class TallyRowBackgrounds {

    private final Map<View, Drawable> mOwnBackgrounds = new WeakHashMap<>();

    /**
     * Tally: keeps a drawable-state row's own background (a switch row's card has no ripple) as
     * its highlight starts. Other rows take their background from the adapter on every bind, which
     * is the one stock's highlight puts back.
     */
    void rememberOwnBackground(View v) {
        if (v instanceof DrawableStateLayout
                && !Boolean.TRUE.equals(v.getTag(R.id.preference_highlighted))
                && !mOwnBackgrounds.containsKey(v)) {
            mOwnBackgrounds.put(v, v.getBackground());
        }
    }

    /** Tally: after a highlight, puts back the row's own background, or else stock's. */
    void setNormalBackground(View v, @DrawableRes int backgroundRes) {
        final Drawable own = mOwnBackgrounds.remove(v);
        if (own != null) {
            v.setBackground(own);
        } else {
            v.setBackgroundResource(backgroundRes);
        }
    }
}

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

package com.android.settings.homepage;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;

/**
 * Draws the Settings homepage as Tally's section cards (the prototype's Settings sections): each
 * category is one card with a hairline edge and 12 dp corners, its title inside the card's top,
 * hairline dividers between rows and 24 dp between cards. It changes only backgrounds, bottom
 * margins and whether a category item takes room; what every row shows and does stays the row's.
 */
public class TallyHomepageAdapter extends PreferenceGroupAdapter {

    @VisibleForTesting
    static final int SEGMENT_SINGLE = 0;
    @VisibleForTesting
    static final int SEGMENT_TOP = 1;
    @VisibleForTesting
    static final int SEGMENT_MIDDLE = 2;
    @VisibleForTesting
    static final int SEGMENT_MIDDLE_DIVIDER = 3;
    @VisibleForTesting
    static final int SEGMENT_BOTTOM = 4;
    @VisibleForTesting
    static final int SEGMENT_BOTTOM_DIVIDER = 5;

    public TallyHomepageAdapter(@NonNull PreferenceGroup preferenceGroup) {
        super(preferenceGroup);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        // A card segment keeps the edges it shares with its neighbours outside its own bounds, so
        // the list clips each row to its bounds (Settings' theme turns clipping off).
        recyclerView.setClipChildren(true);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        final Preference preference = getItem(position);
        if (preference == null) {
            return;
        }
        final View itemView = holder.itemView;
        final ViewGroup.LayoutParams params = itemView.getLayoutParams();
        final int background;
        int bottomMargin = 0;
        if (preference instanceof PreferenceGroup) {
            // The cards' own gaps space the page; a category layout's margin (the stock untitled
            // one has 12 dp) would add to them.
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) params).topMargin = 0;
            }
            // A category is the title at its card's top, or nothing: an untitled category, or a
            // title with no row to show, takes no room.
            final boolean shown = !TextUtils.isEmpty(preference.getTitle())
                    && hasVisibleChild((PreferenceGroup) preference);
            itemView.setVisibility(shown ? View.VISIBLE : View.GONE);
            params.height = shown ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
            background = shown ? R.drawable.tally_homepage_card_top : 0;
        } else {
            final int segment = segmentOf(preference);
            background = backgroundOf(segment);
            if (segment == SEGMENT_SINGLE || segment == SEGMENT_BOTTOM
                    || segment == SEGMENT_BOTTOM_DIVIDER) {
                bottomMargin = itemView.getResources().getDimensionPixelSize(
                        R.dimen.tally_homepage_card_gap);
            }
        }
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) params).bottomMargin = bottomMargin;
        }
        itemView.setLayoutParams(params);
        setBackgroundKeepingPadding(itemView, background);
    }

    /** Returns where a row sits in its card. */
    @VisibleForTesting
    static int segmentOf(@NonNull Preference row) {
        final PreferenceGroup parent = row.getParent();
        if (parent == null || parent instanceof PreferenceScreen) {
            // A row outside any category is a card of its own.
            return SEGMENT_SINGLE;
        }
        Preference first = null;
        Preference last = null;
        for (int i = 0; i < parent.getPreferenceCount(); i++) {
            final Preference sibling = parent.getPreference(i);
            // A category inside a category draws its own card.
            if (!sibling.isVisible() || sibling instanceof PreferenceGroup) {
                continue;
            }
            if (first == null) {
                first = sibling;
            }
            last = sibling;
        }
        final boolean isFirst = row == first;
        final boolean isLast = row == last;
        if (!TextUtils.isEmpty(parent.getTitle())) {
            // The title is the card's top, and no line runs between it and the first row.
            if (isFirst) {
                return isLast ? SEGMENT_BOTTOM : SEGMENT_MIDDLE;
            }
            return isLast ? SEGMENT_BOTTOM_DIVIDER : SEGMENT_MIDDLE_DIVIDER;
        }
        if (isFirst) {
            return isLast ? SEGMENT_SINGLE : SEGMENT_TOP;
        }
        return isLast ? SEGMENT_BOTTOM_DIVIDER : SEGMENT_MIDDLE_DIVIDER;
    }

    @DrawableRes
    private static int backgroundOf(int segment) {
        switch (segment) {
            case SEGMENT_TOP:
                return R.drawable.tally_homepage_row_top;
            case SEGMENT_MIDDLE:
                return R.drawable.tally_homepage_row_middle;
            case SEGMENT_MIDDLE_DIVIDER:
                return R.drawable.tally_homepage_row_middle_divider;
            case SEGMENT_BOTTOM:
                return R.drawable.tally_homepage_row_bottom;
            case SEGMENT_BOTTOM_DIVIDER:
                return R.drawable.tally_homepage_row_bottom_divider;
            case SEGMENT_SINGLE:
            default:
                return R.drawable.tally_homepage_row_single;
        }
    }

    private static boolean hasVisibleChild(@NonNull PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            if (group.getPreference(i).isVisible()) {
                return true;
            }
        }
        return false;
    }

    private static void setBackgroundKeepingPadding(@NonNull View view,
            @DrawableRes int background) {
        final int start = view.getPaddingStart();
        final int top = view.getPaddingTop();
        final int end = view.getPaddingEnd();
        final int bottom = view.getPaddingBottom();
        view.setBackgroundResource(background);
        view.setPaddingRelative(start, top, end, bottom);
    }
}

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

import android.app.Activity;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.settings.R;

/**
 * The Tally homepage title, as the prototype's Settings page has it: "Settings" in the headline
 * type, 56 dp below the top of a 48 dp bar, riding up with the list as it scrolls and shrinking
 * to the title type until it sits centred in the bar, where a hairline then shows under the bar.
 * It follows the scroll position only, so nothing moves by itself, and it stops growing at 130 %
 * text. The list's top padding keeps the first card 16 dp under the title at every text size.
 */
final class TallyHomepageTitle implements View.OnScrollChangeListener,
        View.OnLayoutChangeListener {

    /** Docked, the title is the title type (20 sp) against the headline's 28 sp. */
    @VisibleForTesting
    static final float DOCKED_SCALE = 20f / 28f;

    private final TextView mTitle;
    private final View mDivider;
    private final View mScroller;
    private final View mList;
    private final int mBarHeight;
    private final int mRestOffset;
    private final int mGap;

    private TallyHomepageTitle(TextView title, View divider, View scroller, View list) {
        mTitle = title;
        mDivider = divider;
        mScroller = scroller;
        mList = list;
        final Resources res = title.getResources();
        mBarHeight = res.getDimensionPixelSize(R.dimen.tally_homepage_bar_height);
        mRestOffset = res.getDimensionPixelSize(R.dimen.tally_homepage_title_rest_offset);
        mGap = res.getDimensionPixelSize(R.dimen.tally_homepage_title_gap);
    }

    /** Sets up the title in the homepage's layout; does nothing if the layout has none. */
    static void attach(@NonNull Activity activity) {
        final TextView title = activity.findViewById(R.id.tally_homepage_title);
        final View divider = activity.findViewById(R.id.tally_homepage_bar_divider);
        final View scroller = activity.findViewById(R.id.main_content_scrollable_container);
        final View list = activity.findViewById(R.id.homepage_container);
        if (title == null || divider == null || scroller == null || list == null) {
            return;
        }
        capToLargestSize(title);
        final TallyHomepageTitle helper = new TallyHomepageTitle(title, divider, scroller, list);
        scroller.setOnScrollChangeListener(helper);
        title.addOnLayoutChangeListener(helper);
    }

    /** Keeps the headline at no more than 130 % of its size, as the prototype does. */
    private static void capToLargestSize(TextView title) {
        final Resources res = title.getResources();
        final float maxSize = res.getDimension(R.dimen.tally_homepage_title_max_size);
        if (title.getTextSize() > maxSize) {
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, maxSize);
        }
        final int maxLineHeight =
                res.getDimensionPixelSize(R.dimen.tally_homepage_title_max_line_height);
        if (title.getLineHeight() > maxLineHeight) {
            title.setLineHeight(maxLineHeight);
        }
    }

    @Override
    public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX,
            int oldScrollY) {
        update(scrollY);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        // The first card starts 16 dp under the title at rest, whatever the text size.
        final int listTop = Math.max(0, mRestOffset + mTitle.getHeight() + mGap - mBarHeight);
        if (mList.getPaddingTop() != listTop) {
            mList.setPaddingRelative(mList.getPaddingStart(), listTop, mList.getPaddingEnd(),
                    mList.getPaddingBottom());
        }
        update(mScroller.getScrollY());
    }

    private void update(int scrollY) {
        final int height = mTitle.getHeight();
        if (height == 0) {
            return;
        }
        final float progress = progress(scrollY, mRestOffset, height, mBarHeight);
        final float scale = 1f - progress * (1f - DOCKED_SCALE);
        mTitle.setPivotX(mTitle.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? mTitle.getWidth() : 0f);
        mTitle.setPivotY(height / 2f);
        mTitle.setScaleX(scale);
        mTitle.setScaleY(scale);
        mTitle.setTranslationY(mRestOffset - progress * travel(mRestOffset, height, mBarHeight));
        mDivider.setVisibility(progress >= 1f ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * How far the title has gone from rest (0) to docked (1): it moves one for one with the list,
     * so it docks once the list has scrolled by its travel.
     */
    @VisibleForTesting
    static float progress(int scrollY, int restOffset, int titleHeight, int barHeight) {
        final float travel = travel(restOffset, titleHeight, barHeight);
        return Math.min(1f, Math.max(0f, scrollY / travel));
    }

    /** From rest to centred in the bar; scaling about its centre keeps it centred there. */
    @VisibleForTesting
    static float travel(int restOffset, int titleHeight, int barHeight) {
        return Math.max(1f, restOffset - (barHeight - titleHeight) / 2f);
    }
}

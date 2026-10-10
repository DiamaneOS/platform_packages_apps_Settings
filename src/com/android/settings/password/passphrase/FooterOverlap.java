/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

/**
 * Keeps the scrolling content of a setup-style screen above its footer bar.
 *
 * <p>In portrait the footer is its own bar under the scrolling content. In the landscape
 * layout of the same screen it lies over the bottom of the scrolling content instead, so a
 * screen with more than a field on it gets its last rows covered by the Next button. Wherever
 * the two overlap, the scrolling view gets that much padding at its bottom: its content then
 * ends, and scrolls, above the footer. In every orientation, and with the keyboard up.
 */
public final class FooterOverlap {

    // The footer bar of the setup layout, by the name it has in the app's resources.
    private static final String FOOTER_BAR = "suc_footer_button_bar";

    private FooterOverlap() {}

    /**
     * Watches the layout of {@code root}, a setup layout, for as long as it is attached.
     *
     * @param content a view inside the scrolling content of {@code root}
     */
    public static void keepContentAbove(View root, View content) {
        final ViewTreeObserver.OnGlobalLayoutListener listener = () -> adjust(root, content);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                v.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
            }
        });
        if (root.isAttachedToWindow()) {
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        }
    }

    private static void adjust(View root, View content) {
        final View scroll = scrollViewAround(content);
        final int footerId = root.getResources().getIdentifier(
                FOOTER_BAR, "id", root.getContext().getPackageName());
        final View footer = footerId != 0 ? root.findViewById(footerId) : null;
        if (scroll == null || footer == null) {
            return;
        }
        final int padding = footer.getVisibility() == View.VISIBLE && footer.getHeight() > 0
                ? overlap(scroll, footer) : 0;
        if (scroll.getPaddingBottom() != padding) {
            // Padding does not move the scrolling view itself, so the overlap found on the
            // next layout is the same and this settles at once.
            scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(),
                    scroll.getPaddingRight(), padding);
        }
    }

    // How far the footer reaches up into the scrolling view; 0 if it is beside or below it.
    private static int overlap(View scroll, View footer) {
        final int[] scrollAt = new int[2];
        final int[] footerAt = new int[2];
        scroll.getLocationInWindow(scrollAt);
        footer.getLocationInWindow(footerAt);
        final boolean sideBySide = footerAt[0] >= scrollAt[0] + scroll.getWidth()
                || footerAt[0] + footer.getWidth() <= scrollAt[0];
        if (sideBySide) {
            return 0;
        }
        return FooterInset.overlapHeight(
                scrollAt[1], scroll.getHeight(), footerAt[1], footer.getHeight());
    }

    // The scroll view that the content is in, whatever the layout calls it.
    @Nullable
    private static View scrollViewAround(View content) {
        ViewParent parent = content.getParent();
        while (parent != null) {
            if (parent instanceof ScrollView) {
                return (ScrollView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
}

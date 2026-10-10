/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/** The arithmetic of {@link FooterOverlap}: how much of a scrolling view a footer covers. */
final class FooterInset {

    private FooterInset() {}

    /**
     * The height by which a footer covers the bottom of a scrolling view, from their tops and
     * heights. 0 when the footer starts at or below the scrolling view's bottom, or lies
     * wholly above it. This is the padding the scrolling view needs at its bottom.
     */
    static int overlapHeight(int scrollTop, int scrollHeight, int footerTop, int footerHeight) {
        final int scrollBottom = scrollTop + scrollHeight;
        if (footerTop >= scrollBottom || footerTop + footerHeight <= scrollTop) {
            return 0;
        }
        return scrollBottom - Math.max(footerTop, scrollTop);
    }
}

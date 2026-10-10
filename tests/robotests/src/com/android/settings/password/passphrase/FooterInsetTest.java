/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FooterInsetTest {

    @Test
    public void portrait_footerUnderTheContent_noPadding() {
        // The scrolling view ends where the footer bar starts.
        assertEquals(0, FooterInset.overlapHeight(110, 2086, 2196, 216));
        assertEquals(0, FooterInset.overlapHeight(110, 2000, 2196, 216));
    }

    @Test
    public void landscape_footerOverTheContent_paddingUpToItsTop() {
        // The scrolling view runs to the bottom of a 1116 px high screen; the footer bar
        // starts at 858. The content has to end there.
        assertEquals(1116 - 858, FooterInset.overlapHeight(110, 1006, 858, 216));
    }

    @Test
    public void footerReachingBelowTheContent_onlyTheCoveredPartCounts() {
        assertEquals(100, FooterInset.overlapHeight(0, 1000, 900, 400));
    }

    @Test
    public void footerAboveOrCoveringAll() {
        assertEquals(0, FooterInset.overlapHeight(500, 400, 100, 300));
        // A footer that starts above the scrolling view covers all of it at most.
        assertEquals(400, FooterInset.overlapHeight(500, 400, 300, 900));
    }
}

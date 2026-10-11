/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * The learning period of a new strong screen lock, for text that tells the user about it.
 *
 * <p>While it runs, the lock is asked for about once a day even when a fingerprint is used, so
 * that it is practised. The lock settings service keeps the time; this only turns it into days.
 */
public final class LearningPeriod {

    private static final long DAY_MILLIS = 24 * 60 * 60 * 1000L;

    private LearningPeriod() {}

    /**
     * Whole days for a time in milliseconds, rounded up: one millisecond left is still "1 day".
     * 0 when nothing is left.
     */
    public static int daysRoundedUp(long millis) {
        if (millis <= 0) {
            return 0;
        }
        final long days = millis / DAY_MILLIS + (millis % DAY_MILLIS == 0 ? 0 : 1);
        return (int) Math.min(Integer.MAX_VALUE, days);
    }
}

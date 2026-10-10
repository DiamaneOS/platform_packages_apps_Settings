/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.GuessTime.Scale;

import org.junit.Test;

public class GuessTimeTest {

    private static final double MINUTE = 60;
    private static final double HOUR = 3600;
    private static final double DAY = 86400;
    private static final double YEAR = 31_557_600;   // 365.25 days

    private static void assertTime(Scale scale, int amount, double seconds) {
        final GuessTime time = GuessTime.fromSeconds(seconds);
        assertEquals(seconds + " s", scale, time.scale);
        assertEquals(seconds + " s", amount, time.amount);
    }

    @Test
    public void fromSeconds_underASecond() {
        assertTime(Scale.UNDER_A_SECOND, 0, 0);
        assertTime(Scale.UNDER_A_SECOND, 0, 0.999);
    }

    @Test
    public void fromSeconds_unitsChangeAtTheirBoundaries() {
        assertTime(Scale.SECONDS, 1, 1);
        assertTime(Scale.SECONDS, 50, 59.9);
        assertTime(Scale.MINUTES, 1, MINUTE);
        assertTime(Scale.MINUTES, 50, HOUR - 1);
        assertTime(Scale.HOURS, 1, HOUR);
        assertTime(Scale.HOURS, 20, DAY - 1);
        assertTime(Scale.DAYS, 1, DAY);
        assertTime(Scale.DAYS, 300, YEAR - 1);
        assertTime(Scale.YEARS, 1, YEAR);
        assertTime(Scale.YEARS, 900, 999.9 * YEAR);
        assertTime(Scale.THOUSANDS_OF_YEARS, 1, 1e3 * YEAR);
        assertTime(Scale.THOUSANDS_OF_YEARS, 900, 999.9e3 * YEAR);
        assertTime(Scale.MILLIONS_OF_YEARS, 1, 1e6 * YEAR);
        assertTime(Scale.MILLIONS_OF_YEARS, 900, 999.9e6 * YEAR);
        assertTime(Scale.BILLIONS_OF_YEARS, 1, 1e9 * YEAR);
        assertTime(Scale.BILLIONS_OF_YEARS, 900, 999.9e9 * YEAR);
        assertTime(Scale.BEYOND_BILLIONS_OF_YEARS, 0, 1e12 * YEAR);
        assertTime(Scale.BEYOND_BILLIONS_OF_YEARS, 0, Double.MAX_VALUE);
        assertTime(Scale.BEYOND_BILLIONS_OF_YEARS, 0, Double.POSITIVE_INFINITY);
    }

    @Test
    public void fromSeconds_roundsDownToOneFigure() {
        assertTime(Scale.SECONDS, 9, 9.99);
        assertTime(Scale.SECONDS, 10, 10);
        assertTime(Scale.SECONDS, 10, 19.99);
        assertTime(Scale.SECONDS, 20, 20);
        assertTime(Scale.DAYS, 90, 99.9 * DAY);
        assertTime(Scale.DAYS, 100, 100 * DAY);
        assertTime(Scale.DAYS, 100, 199.9 * DAY);
        assertTime(Scale.YEARS, 3, 3.99 * YEAR);
        assertTime(Scale.MILLIONS_OF_YEARS, 10, 15e6 * YEAR);
        assertTime(Scale.BILLIONS_OF_YEARS, 100, 120e9 * YEAR);
    }

    @Test
    public void fromSeconds_neverShowsMoreThanTheEstimateOrASecondFigure() {
        final double[] unitSeconds = {1, MINUTE, HOUR, DAY, YEAR, 1e3 * YEAR, 1e6 * YEAR,
                1e9 * YEAR};
        // From one second to past the end of the scale, in steps of 7 percent.
        for (double seconds = 1; seconds < 1e13 * YEAR; seconds *= 1.07) {
            final GuessTime time = GuessTime.fromSeconds(seconds);
            if (time.scale == Scale.BEYOND_BILLIONS_OF_YEARS) {
                assertTrue(seconds >= 1e12 * YEAR);
                continue;
            }
            final int amount = time.amount;
            assertTrue(amount + "", amount >= 1 && amount <= 900
                    && (amount < 10 || (amount < 100 && amount % 10 == 0) || amount % 100 == 0));
            final double shown = amount * unitSeconds[time.scale.ordinal() - 1];
            assertTrue(seconds + " s shown as " + time, shown <= seconds);
            // Rounding down to one figure never halves the value.
            assertTrue(seconds + " s shown as " + time, shown > seconds / 2);
        }
    }

    @Test
    public void fromSeconds_refusesWhatIsNotATime() {
        assertThrows(IllegalArgumentException.class, () -> GuessTime.fromSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> GuessTime.fromSeconds(Double.NaN));
    }

    @Test
    public void equals_comparesScaleAndAmount() {
        assertEquals(GuessTime.fromSeconds(30), GuessTime.fromSeconds(39));
        assertEquals(GuessTime.fromSeconds(30).hashCode(), GuessTime.fromSeconds(39).hashCode());
        assertNotEquals(GuessTime.fromSeconds(30), GuessTime.fromSeconds(40));
        assertNotEquals(GuessTime.fromSeconds(30), GuessTime.fromSeconds(30 * MINUTE));
        assertNotEquals(GuessTime.fromSeconds(30), null);
    }
}

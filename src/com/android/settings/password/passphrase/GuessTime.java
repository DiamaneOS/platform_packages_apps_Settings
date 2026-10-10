/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * A time to guess on a coarse scale: a unit and an amount of one significant figure, such as
 * "3 thousand years" or "20 seconds".
 *
 * <p>The estimate behind it is rough, so more figures would claim a precision that is not there.
 * The amount is always rounded down: a shown time is never longer than the estimate.
 */
public final class GuessTime {

    /** The unit of a {@link GuessTime}. */
    public enum Scale {
        /** Less than one second. Has no amount. */
        UNDER_A_SECOND,
        SECONDS,
        MINUTES,
        HOURS,
        DAYS,
        YEARS,
        THOUSANDS_OF_YEARS,
        MILLIONS_OF_YEARS,
        BILLIONS_OF_YEARS,
        /** A thousand billion years or more. Has no amount. */
        BEYOND_BILLIONS_OF_YEARS,
    }

    private static final double MINUTE = 60;
    private static final double HOUR = 60 * MINUTE;
    private static final double DAY = 24 * HOUR;
    private static final double YEAR = 365.25 * DAY;

    /** The unit. */
    public final Scale scale;

    /**
     * How many of the unit: 1 to 9, 10 to 90 in tens, or 100 to 900 in hundreds. 0 for the two
     * scales without an amount.
     */
    public final int amount;

    private GuessTime(Scale scale, int amount) {
        this.scale = scale;
        this.amount = amount;
    }

    /**
     * Puts a number of seconds on the coarse scale.
     *
     * @param seconds 0 or more; may be infinite
     */
    public static GuessTime fromSeconds(double seconds) {
        if (!(seconds >= 0)) {
            throw new IllegalArgumentException("seconds must be a number from 0 up");
        }
        if (seconds < 1) {
            return new GuessTime(Scale.UNDER_A_SECOND, 0);
        }
        if (seconds < MINUTE) {
            return of(Scale.SECONDS, seconds);
        }
        if (seconds < HOUR) {
            return of(Scale.MINUTES, seconds / MINUTE);
        }
        if (seconds < DAY) {
            return of(Scale.HOURS, seconds / HOUR);
        }
        if (seconds < YEAR) {
            return of(Scale.DAYS, seconds / DAY);
        }
        final double years = seconds / YEAR;
        if (years < 1e3) {
            return of(Scale.YEARS, years);
        }
        if (years < 1e6) {
            return of(Scale.THOUSANDS_OF_YEARS, years / 1e3);
        }
        if (years < 1e9) {
            return of(Scale.MILLIONS_OF_YEARS, years / 1e6);
        }
        if (years < 1e12) {
            return of(Scale.BILLIONS_OF_YEARS, years / 1e9);
        }
        return new GuessTime(Scale.BEYOND_BILLIONS_OF_YEARS, 0);
    }

    // value is from 1 to below 1000.
    private static GuessTime of(Scale scale, double value) {
        final int whole = (int) value;
        final int step = whole >= 100 ? 100 : whole >= 10 ? 10 : 1;
        return new GuessTime(scale, whole / step * step);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GuessTime
                && ((GuessTime) other).scale == scale
                && ((GuessTime) other).amount == amount;
    }

    @Override
    public int hashCode() {
        return scale.hashCode() * 31 + amount;
    }

    @Override
    public String toString() {
        return amount + " " + scale;
    }
}

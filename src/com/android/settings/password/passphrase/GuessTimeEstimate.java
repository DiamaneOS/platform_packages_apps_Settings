/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * An estimated time to guess a credential, in the only form it may be shown: a wording, an
 * amount of two significant figures and a coarse unit, such as "about 15 million years".
 *
 * <p>It is an estimate and has to be shown as one. The amount cannot be read on its own:
 * {@link #describe} hands it out together with the {@link Wording}, and the text a screen builds
 * must carry that wording ("about", "less than", "more than"). The screen also states the
 * {@link #assumptions} the estimate rests on.
 *
 * <p>The figure behind it is rough, so more than two figures would claim a precision that is not
 * there. The amount is always rounded down: a shown time is never longer than the estimate.
 */
public final class GuessTimeEstimate {

    /** How the amount relates to the estimate. A shown time always includes it. */
    public enum Wording {
        /** Less than the amount: used for "less than 1 second". */
        LESS_THAN,
        /** About the amount: the estimate, rounded down to two figures. */
        ABOUT,
        /** More than the amount: used for "more than 1,000 billion years". */
        MORE_THAN,
    }

    /** The unit of the amount. */
    public enum Unit {
        SECONDS,
        MINUTES,
        HOURS,
        DAYS,
        YEARS,
        THOUSANDS_OF_YEARS,
        MILLIONS_OF_YEARS,
        BILLIONS_OF_YEARS,
    }

    /** Turns an estimate into what a screen shows. */
    public interface Formatter<T> {
        /**
         * @param wording has to be part of the result
         * @param amount two significant figures: one decimal below 10 (4.6), whole from 10
         *     (15), tens from 100 (190). 1 with {@link Wording#LESS_THAN}, 1000 with
         *     {@link Wording#MORE_THAN}.
         * @param unit the unit of the amount
         */
        T format(Wording wording, double amount, Unit unit);
    }

    private static final double MINUTE = 60;
    private static final double HOUR = 60 * MINUTE;
    private static final double DAY = 24 * HOUR;
    private static final double YEAR = 365.25 * DAY;

    /** What the estimate assumes about the attacker. To be stated wherever it is shown. */
    public final GuessingAssumptions assumptions;

    private final Wording mWording;
    private final double mAmount;
    private final Unit mUnit;

    private GuessTimeEstimate(GuessingAssumptions assumptions, Wording wording, double amount,
            Unit unit) {
        this.assumptions = assumptions;
        mWording = wording;
        mAmount = amount;
        mUnit = unit;
    }

    /** Hands the wording, the amount and the unit to {@code formatter}, all at once. */
    public <T> T describe(Formatter<T> formatter) {
        return formatter.format(mWording, mAmount, mUnit);
    }

    /**
     * Puts an estimated number of seconds on the coarse scale.
     *
     * @param seconds 0 or more; may be infinite
     */
    static GuessTimeEstimate of(double seconds, GuessingAssumptions assumptions) {
        if (!(seconds >= 0)) {
            throw new IllegalArgumentException("seconds must be a number from 0 up");
        }
        if (seconds < 1) {
            return new GuessTimeEstimate(assumptions, Wording.LESS_THAN, 1, Unit.SECONDS);
        }
        final Unit unit;
        final double value;
        if (seconds < MINUTE) {
            unit = Unit.SECONDS;
            value = seconds;
        } else if (seconds < HOUR) {
            unit = Unit.MINUTES;
            value = seconds / MINUTE;
        } else if (seconds < DAY) {
            unit = Unit.HOURS;
            value = seconds / HOUR;
        } else if (seconds < YEAR) {
            unit = Unit.DAYS;
            value = seconds / DAY;
        } else if (seconds < 1e3 * YEAR) {
            unit = Unit.YEARS;
            value = seconds / YEAR;
        } else if (seconds < 1e6 * YEAR) {
            unit = Unit.THOUSANDS_OF_YEARS;
            value = seconds / YEAR / 1e3;
        } else if (seconds < 1e9 * YEAR) {
            unit = Unit.MILLIONS_OF_YEARS;
            value = seconds / YEAR / 1e6;
        } else if (seconds < 1e12 * YEAR) {
            unit = Unit.BILLIONS_OF_YEARS;
            value = seconds / YEAR / 1e9;
        } else {
            return new GuessTimeEstimate(
                    assumptions, Wording.MORE_THAN, 1000, Unit.BILLIONS_OF_YEARS);
        }
        return new GuessTimeEstimate(assumptions, Wording.ABOUT, twoFiguresDown(value), unit);
    }

    // value is from 1 to below 1000.
    private static double twoFiguresDown(double value) {
        if (value < 10) {
            return Math.floor(value * 10) / 10;
        }
        if (value < 100) {
            return Math.floor(value);
        }
        // Never 1000 of a unit, whatever rounding the division above did.
        return Math.min(Math.floor(value / 10) * 10, 990);
    }

    @Override
    public String toString() {
        return mWording + " " + mAmount + " " + mUnit + " (estimate)";
    }
}

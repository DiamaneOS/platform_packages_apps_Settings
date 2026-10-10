/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.GuessTimeEstimate.Unit;
import com.android.settings.password.passphrase.GuessTimeEstimate.Wording;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class GuessTimeEstimateTest {

    private static final double MINUTE = 60;
    private static final double HOUR = 3600;
    private static final double DAY = 86400;
    private static final double YEAR = 31_557_600;   // 365.25 days

    private static final GuessingAssumptions ASSUMED = new GuessingAssumptions(1, 1, 0.5);

    private static String shown(double seconds) {
        return GuessTimeEstimate.of(seconds, ASSUMED)
                .describe((wording, amount, unit) -> wording + " " + amount + " " + unit);
    }

    @Test
    public void of_underASecond_isLessThanOneSecond() {
        assertEquals("LESS_THAN 1.0 SECONDS", shown(0));
        assertEquals("LESS_THAN 1.0 SECONDS", shown(0.999));
    }

    @Test
    public void of_unitsChangeAtTheirBoundaries() {
        assertEquals("ABOUT 1.0 SECONDS", shown(1));
        assertEquals("ABOUT 59.0 SECONDS", shown(59.9));
        assertEquals("ABOUT 1.0 MINUTES", shown(MINUTE));
        assertEquals("ABOUT 59.0 MINUTES", shown(HOUR - 1));
        assertEquals("ABOUT 1.0 HOURS", shown(HOUR));
        assertEquals("ABOUT 23.0 HOURS", shown(DAY - 1));
        assertEquals("ABOUT 1.0 DAYS", shown(DAY));
        assertEquals("ABOUT 360.0 DAYS", shown(YEAR - 1));
        assertEquals("ABOUT 1.0 YEARS", shown(YEAR));
        assertEquals("ABOUT 990.0 YEARS", shown(999.9 * YEAR));
        assertEquals("ABOUT 1.0 THOUSANDS_OF_YEARS", shown(1e3 * YEAR));
        assertEquals("ABOUT 990.0 THOUSANDS_OF_YEARS", shown(999.9e3 * YEAR));
        assertEquals("ABOUT 1.0 MILLIONS_OF_YEARS", shown(1e6 * YEAR));
        assertEquals("ABOUT 990.0 MILLIONS_OF_YEARS", shown(999.9e6 * YEAR));
        assertEquals("ABOUT 1.0 BILLIONS_OF_YEARS", shown(1e9 * YEAR));
        assertEquals("ABOUT 990.0 BILLIONS_OF_YEARS", shown(999.9e9 * YEAR));
    }

    @Test
    public void of_aThousandBillionYearsOrMore_isMoreThan() {
        assertEquals("MORE_THAN 1000.0 BILLIONS_OF_YEARS", shown(1e12 * YEAR));
        assertEquals("MORE_THAN 1000.0 BILLIONS_OF_YEARS", shown(Double.MAX_VALUE));
        assertEquals("MORE_THAN 1000.0 BILLIONS_OF_YEARS", shown(Double.POSITIVE_INFINITY));
    }

    @Test
    public void of_roundsDownToTwoFigures() {
        // Below 10: one decimal.
        assertEquals("ABOUT 9.9 SECONDS", shown(9.99));
        assertEquals("ABOUT 3.9 YEARS", shown(3.99 * YEAR));
        assertEquals("ABOUT 4.6 HOURS", shown(4.63 * HOUR));
        assertEquals("ABOUT 1.5 MILLIONS_OF_YEARS", shown(1.59e6 * YEAR));
        // From 10: whole.
        assertEquals("ABOUT 10.0 SECONDS", shown(10));
        assertEquals("ABOUT 19.0 SECONDS", shown(19.99));
        assertEquals("ABOUT 15.0 MILLIONS_OF_YEARS", shown(15e6 * YEAR));
        assertEquals("ABOUT 15.0 MILLIONS_OF_YEARS", shown(15.99e6 * YEAR));
        // From 100: tens.
        assertEquals("ABOUT 100.0 DAYS", shown(100 * DAY));
        assertEquals("ABOUT 100.0 DAYS", shown(109.9 * DAY));
        assertEquals("ABOUT 190.0 DAYS", shown(192.9 * DAY));
        assertEquals("ABOUT 190.0 DAYS", shown(199.9 * DAY));
        assertEquals("ABOUT 120.0 BILLIONS_OF_YEARS", shown(120e9 * YEAR));
    }

    @Test
    public void of_neverShowsMoreThanTheEstimateOrAThirdFigure() {
        final double[] unitSeconds = {1, MINUTE, HOUR, DAY, YEAR, 1e3 * YEAR, 1e6 * YEAR,
                1e9 * YEAR};
        // From one second to past the end of the scale, in steps of 0.7 percent.
        for (double seconds = 1; seconds < 1e13 * YEAR; seconds *= 1.007) {
            final double estimated = seconds;
            GuessTimeEstimate.of(seconds, ASSUMED).describe((wording, amount, unit) -> {
                final String what = estimated + " s shown as " + wording + " " + amount + " "
                        + unit;
                final double shown = amount * unitSeconds[unit.ordinal()];
                if (wording == Wording.MORE_THAN) {
                    assertTrue(what, estimated >= 1e12 * YEAR && amount == 1000
                            && unit == Unit.BILLIONS_OF_YEARS);
                    return what;
                }
                assertSame(what, Wording.ABOUT, wording);
                // Two figures: tenths below 10, whole below 100, tens below 1000.
                final double step = amount < 10 ? 0.1 : amount < 100 ? 1 : 10;
                final double steps = amount / step;
                assertTrue(what, amount >= 1 && amount <= 990
                        && Math.abs(steps - Math.rint(steps)) < 1e-9);
                // Never more than the estimate, and rounding down loses less than a tenth.
                assertTrue(what, shown <= estimated * (1 + 1e-12));
                assertTrue(what, shown > estimated * 0.9);
                return what;
            });
        }
    }

    @Test
    public void of_refusesWhatIsNotATime() {
        assertThrows(IllegalArgumentException.class, () -> GuessTimeEstimate.of(-1, ASSUMED));
        assertThrows(IllegalArgumentException.class,
                () -> GuessTimeEstimate.of(Double.NaN, ASSUMED));
    }

    @Test
    public void estimate_carriesItsAssumptions() {
        assertSame(ASSUMED, GuessTimeEstimate.of(30, ASSUMED).assumptions);
    }

    @Test
    public void estimate_toString_saysItIsAnEstimate() {
        assertEquals("ABOUT 30.0 SECONDS (estimate)",
                GuessTimeEstimate.of(30, ASSUMED).toString());
    }

    @Test
    public void estimate_givesNoAmountWithoutTheWording() throws Exception {
        // No public field or method hands out a number: only describe(), which passes the
        // wording along with it.
        for (Field field : GuessTimeEstimate.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                assertSame(field.getName(), GuessingAssumptions.class, field.getType());
            }
        }
        for (Method method : GuessTimeEstimate.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                assertFalse(method.getName(), method.getReturnType().isPrimitive());
            }
        }
        // The same for the figure it is made from.
        final Method seconds = CredentialStrength.class.getDeclaredMethod(
                "secondsToGuess", double.class, GuessingAssumptions.class);
        assertFalse(Modifier.isPublic(seconds.getModifiers()));
    }
}

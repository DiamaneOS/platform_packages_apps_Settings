/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * The one set of assumptions every time-to-guess estimate on the setup screens is made under.
 *
 * <p><b>These are placeholders, not measurements.</b> They are to be replaced by values measured
 * for this device. This is the only place to change: the screens take the numbers they print
 * from here as well, so the stated assumptions cannot drift from the ones used.
 */
public final class PlaceholderGuessingAssumptions {

    /** Number of machines the attacker is taken to guess with at the same time. */
    public static final int MACHINES = 1000;

    /**
     * Guesses one such machine (a high-end graphics card) is taken to try each second, with
     * the cost one guess has on this device. PLACEHOLDER.
     */
    public static final int GUESSES_PER_SECOND_PER_MACHINE = 30;

    /** The average case: the right guess is found after half of all possible ones. */
    public static final double SEARCHED_SHARE = 0.5;

    private PlaceholderGuessingAssumptions() {}

    /** The assumptions as the estimate takes them. */
    public static GuessingAssumptions get() {
        return new GuessingAssumptions(
                1.0 / GUESSES_PER_SECOND_PER_MACHINE, MACHINES, SEARCHED_SHARE);
    }
}

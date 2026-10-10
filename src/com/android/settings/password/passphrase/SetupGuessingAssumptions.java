/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * The one set of assumptions every time-to-guess estimate on the setup screens is made under:
 * an attacker who tries a million guesses every second and finds the right one after half of
 * all possible ones.
 *
 * <p>Where the rate comes from: one guess costs the attacker what unlocking costs this phone,
 * the same memory-hard key stretching. Published benchmarks of that work, read in October
 * 2026, put a top graphics card at no more than about 100 guesses a second, and processors at
 * about the same cost for each guess. A million guesses every second therefore takes more
 * than 10,000 top graphics cards today. Machines built for the purpose, which only a
 * government could pay for, might be a thousand times faster; nothing of the kind is publicly
 * known.
 *
 * <p>This is the only place to change. The screens take the numbers they print from here as
 * well, so the stated assumptions cannot drift from the ones used.
 */
public final class SetupGuessingAssumptions {

    /** Guesses the attacker is taken to try each second, on all machines together. */
    public static final int GUESSES_PER_SECOND = 1_000_000;

    /**
     * Top graphics cards it takes today to reach {@link #GUESSES_PER_SECOND}: more than this
     * many. Stated next to the rate; not used in the estimate.
     */
    public static final int GRAPHICS_CARDS_NEEDED = 10_000;

    /**
     * How many times faster than {@link #GUESSES_PER_SECOND} a government with purpose-built
     * machines could be. Stated as a caveat; not used in the estimate.
     */
    public static final int PURPOSE_BUILT_SPEEDUP = 1_000;

    /** The average case: the right guess is found after half of all possible ones. */
    public static final double SEARCHED_SHARE = 0.5;

    private SetupGuessingAssumptions() {}

    /** The assumptions as the estimate takes them. */
    public static GuessingAssumptions get() {
        return new GuessingAssumptions(1.0 / GUESSES_PER_SECOND, 1, SEARCHED_SHARE);
    }
}

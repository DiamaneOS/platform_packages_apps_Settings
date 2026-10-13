/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * What a time-to-guess estimate assumes about the attacker. The values are data: this code holds
 * no defaults, because the cost of a guess depends on the device and is measured there.
 *
 * <p>The attacker is taken to have a copy of the storage and no limit on attempts other than the
 * work each guess costs.
 */
public final class GuessingAssumptions {

    /** Seconds one guess takes on one of the attacker's machines. */
    public final double secondsPerGuess;

    /** Number of such machines guessing at the same time. */
    public final double units;

    /**
     * Share of all possible credentials the attacker tries before finding the right one, above 0
     * and at most 1. One half is the average case, 1 the attacker's worst case.
     */
    public final double searchedShare;

    public GuessingAssumptions(double secondsPerGuess, double units, double searchedShare) {
        if (!(secondsPerGuess > 0) || Double.isInfinite(secondsPerGuess)) {
            throw new IllegalArgumentException("seconds per guess must be above 0");
        }
        if (!(units >= 1) || Double.isInfinite(units)) {
            throw new IllegalArgumentException("units must be at least 1");
        }
        if (!(searchedShare > 0 && searchedShare <= 1)) {
            throw new IllegalArgumentException("searched share must be above 0 and at most 1");
        }
        this.secondsPerGuess = secondsPerGuess;
        this.units = units;
        this.searchedShare = searchedShare;
    }
}

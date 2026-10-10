/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * Rates a passphrase a person chose, on a coarse scale.
 *
 * <p>There is no implementation yet. Entropy cannot be computed for a chosen phrase, so a rater
 * gives a coarse {@link Rating} and never a number of bits or a time to guess.
 *
 * <p>Rules for an implementation:
 * <ul>
 *   <li>A phrase that does not meet {@link PassphraseFloor} is {@link Rating#BELOW_MINIMUM}.
 *       A rater may be stricter than the floor, never looser.
 *   <li>The characters are not kept, copied into a String or logged. Every working copy is
 *       wiped before {@link #rate} returns.
 * </ul>
 */
public interface ChosenPassphraseRater {

    /** The coarse rating of a chosen passphrase. */
    enum Rating {
        /** Too short or too simple to be saved as a passphrase. */
        BELOW_MINIMUM,
        /** Can be saved, but looks like something an attacker would try early. */
        GUESSABLE,
        /** Can be saved, and nothing the rater knows makes it easy to guess. */
        NOT_EASILY_GUESSED,
    }

    /** Rates the first {@code length} characters of {@code phrase}. */
    Rating rate(char[] phrase, int length);
}

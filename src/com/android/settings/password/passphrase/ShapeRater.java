/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * Rates a chosen passphrase by its shape only: its length and its characters.
 *
 * <p>It knows no words and no quotes, so it can be wrong in both directions. A random password
 * of 12 characters is {@link Rating#BELOW_MINIMUM} although it may be hard to guess. A long line
 * from a song is {@link Rating#NOT_EASILY_GUESSED} although an attacker would try it early. A
 * screen that shows the rating has to say so in plain words.
 *
 * <p>Nothing of the phrase is kept: the checks read the array in place.
 */
public final class ShapeRater implements ChosenPassphraseRater {

    /** Fewer different characters than this in a phrase of 20 or more look like a pattern. */
    static final int FEW_DISTINCT_CHARS = 8;

    @Override
    public Rating rate(char[] phrase, int length) {
        if (!PassphraseFloor.isMet(phrase, length)) {
            return Rating.BELOW_MINIMUM;
        }
        if (repeatsABlock(phrase, length) || isMostlyOneRun(phrase, length)
                || distinctChars(phrase, length) < FEW_DISTINCT_CHARS) {
            return Rating.GUESSABLE;
        }
        return Rating.NOT_EASILY_GUESSED;
    }

    // Whether the phrase is one block of at most half its length, written again and again:
    // "horse horse horse horse".
    private static boolean repeatsABlock(char[] phrase, int length) {
        for (int period = 1; period <= length / 2; period++) {
            boolean repeats = true;
            for (int i = period; i < length && repeats; i++) {
                repeats = phrase[i] == phrase[i - period];
            }
            if (repeats) {
                return true;
            }
        }
        return false;
    }

    // Whether more than half of the steps from one character to the next go up by one or down
    // by one: "abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba".
    private static boolean isMostlyOneRun(char[] phrase, int length) {
        int up = 0;
        int down = 0;
        for (int i = 1; i < length; i++) {
            final int step = phrase[i] - phrase[i - 1];
            if (step == 1) {
                up++;
            } else if (step == -1) {
                down++;
            }
        }
        return Math.max(up, down) * 2 > length - 1;
    }

    // The floor has already refused characters outside ASCII.
    private static int distinctChars(char[] phrase, int length) {
        long low = 0;
        long high = 0;
        for (int i = 0; i < length; i++) {
            final char c = phrase[i];
            if (c < 64) {
                low |= 1L << c;
            } else {
                high |= 1L << (c - 64);
            }
        }
        return Long.bitCount(low) + Long.bitCount(high);
    }
}

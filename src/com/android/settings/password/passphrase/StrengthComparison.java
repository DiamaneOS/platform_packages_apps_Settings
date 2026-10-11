/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen locks a user can choose between, side by side: how many equally likely values each
 * has at best, and an estimated time to guess under one set of assumptions.
 */
public final class StrengthComparison {

    /** A screen lock to compare. */
    public enum Choice {
        /** A PIN of six digits. At best: if every digit was picked at random. */
        PIN_6_DIGITS(true),
        /** An unlock pattern. At best: if it was picked at random among all patterns. */
        PATTERN(true),
        WORDS_5(false),
        WORDS_6(false),
        WORDS_7(false),
        WORDS_8(false),
        /** A PIN of twelve digits picked at random. Not offered; shown for comparison. */
        RANDOM_PIN_12(false),
        /** A PIN of twenty digits that the phone generated. */
        RANDOM_PIN_20(false);

        /**
         * Whether the figures are an upper limit. A PIN or pattern a person picks is easier to
         * guess than a random one.
         */
        public final boolean atBest;

        Choice(boolean atBest) {
            this.atBest = atBest;
        }
    }

    /** One line of the comparison. */
    public static final class Row {
        public final Choice choice;
        /** Entropy in bits; an upper limit when {@link Choice#atBest}. */
        public final double entropyBits;
        /** Estimated time to guess; an upper limit when {@link Choice#atBest}. */
        public final GuessTimeEstimate estimate;

        Row(Choice choice, double entropyBits, GuessTimeEstimate estimate) {
            this.choice = choice;
            this.entropyBits = entropyBits;
            this.estimate = estimate;
        }
    }

    // Unlock patterns of four to nine dots on the 3 x 3 grid.
    private static final int PATTERNS = 389_112;

    private StrengthComparison() {}

    /** The rows for the given choices, in the order given, all under the same assumptions. */
    public static List<Row> rows(GuessingAssumptions assumptions, Choice... choices) {
        final List<Row> rows = new ArrayList<>(choices.length);
        for (Choice choice : choices) {
            final double bits = entropyBits(choice);
            rows.add(new Row(choice, bits,
                    CredentialStrength.estimateTimeToGuess(bits, assumptions)));
        }
        return rows;
    }

    /**
     * Entropy in bits of a choice. For words it is {@code words * log2(7772)}: the phrases the
     * generator throws away for being too short change it by less than a billionth of a bit,
     * which is left out here. {@link PassphraseGenerator#entropyBits} gives the exact figure.
     */
    public static double entropyBits(Choice choice) {
        switch (choice) {
            case PIN_6_DIGITS:
                return CredentialStrength.generatedPinEntropyBits(6);
            case PATTERN:
                return Math.log(PATTERNS) / Math.log(2);
            case WORDS_5:
                return wordsBits(5);
            case WORDS_6:
                return wordsBits(6);
            case WORDS_7:
                return wordsBits(7);
            case WORDS_8:
                return wordsBits(8);
            case RANDOM_PIN_12:
                return CredentialStrength.generatedPinEntropyBits(12);
            case RANDOM_PIN_20:
                return CredentialStrength.generatedPinEntropyBits(20);
            default:
                throw new IllegalArgumentException("unknown choice");
        }
    }

    /** The choice for a generated passphrase of {@code words} words. */
    public static Choice forWords(int words) {
        switch (words) {
            case 5:
                return Choice.WORDS_5;
            case 6:
                return Choice.WORDS_6;
            case 7:
                return Choice.WORDS_7;
            case 8:
                return Choice.WORDS_8;
            default:
                throw new IllegalArgumentException("words must be from 5 to 8");
        }
    }

    private static double wordsBits(int words) {
        return CredentialStrength.passphraseEntropyBits(
                words, WordList.EFF_LARGE_LETTERS_ONLY_SIZE, BigInteger.ZERO);
    }
}

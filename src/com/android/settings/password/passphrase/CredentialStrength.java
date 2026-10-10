/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * Strength figures for generated credentials.
 *
 * <p>Entropy is exact, because a generated credential is one of a known number of equally likely
 * values. The time to guess is an estimate that is only as good as the assumptions passed in.
 *
 * <p>Nothing here rates a credential a person chose: see {@link ChosenPassphraseRater}.
 */
public final class CredentialStrength {

    private static final double LN_2 = Math.log(2);

    private CredentialStrength() {}

    /**
     * Entropy in bits of a generated passphrase: the base-2 logarithm of the number of phrases
     * the generator can return, each equally likely.
     *
     * @param words number of words in the phrase
     * @param listSize number of words in the list
     * @param rejected number of word sequences the generator never returns, see
     *     {@link PassphraseFloor#countRejected}
     */
    public static double passphraseEntropyBits(int words, int listSize, BigInteger rejected) {
        return words * log2(listSize) - rejectionLossBits(words, listSize, rejected);
    }

    /**
     * Entropy in bits that is lost because the generator never returns {@code rejected} of the
     * {@code listSize^words} word sequences.
     */
    public static double rejectionLossBits(int words, int listSize, BigInteger rejected) {
        if (words < 1 || listSize < 1) {
            throw new IllegalArgumentException("words and list size must be at least 1");
        }
        final BigInteger total = BigInteger.valueOf(listSize).pow(words);
        if (rejected.signum() < 0 || rejected.compareTo(total) >= 0) {
            throw new IllegalArgumentException("rejected must be from 0 to below listSize^words");
        }
        final double share = new BigDecimal(rejected)
                .divide(new BigDecimal(total), MathContext.DECIMAL64).doubleValue();
        // -log2(1 - share), computed so that a tiny share is not rounded away.
        return -Math.log1p(-share) / LN_2;
    }

    /**
     * Entropy in bits of a PIN of {@code digits} digits in which every digit was drawn
     * independently and uniformly, as the lock settings service generates them.
     */
    public static double generatedPinEntropyBits(int digits) {
        if (digits < 1) {
            throw new IllegalArgumentException("digits must be at least 1");
        }
        return digits * log2(10);
    }

    /**
     * Estimated seconds an attacker needs to guess a credential of the given entropy, under the
     * given assumptions. Can be infinite when the number is too large for a double.
     */
    public static double secondsToGuess(double entropyBits, GuessingAssumptions assumptions) {
        if (!(entropyBits >= 0) || Double.isInfinite(entropyBits)) {
            throw new IllegalArgumentException("entropy must be a number from 0 up");
        }
        // guesses = 2^bits * share searched; seconds = guesses * seconds per guess / units.
        final double log2Seconds = entropyBits
                + log2(assumptions.searchedShare)
                + log2(assumptions.secondsPerGuess)
                - log2(assumptions.units);
        return Math.pow(2, log2Seconds);
    }

    /** {@link #secondsToGuess} on the coarse scale that is fit to show. */
    public static GuessTime timeToGuess(double entropyBits, GuessingAssumptions assumptions) {
        return GuessTime.fromSeconds(secondsToGuess(entropyBits, assumptions));
    }

    private static double log2(double value) {
        return Math.log(value) / LN_2;
    }
}

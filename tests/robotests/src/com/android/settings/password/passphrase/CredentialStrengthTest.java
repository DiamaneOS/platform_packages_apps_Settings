/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.settings.password.passphrase.GuessTime.Scale;

import org.junit.Test;

import java.math.BigInteger;

public class CredentialStrengthTest {

    // log2(7776) = 5 * log2(6) = 5 * 2.584962500721156...
    private static final double BITS_PER_WORD = 12.92481250360578;
    // log2(10)
    private static final double BITS_PER_DIGIT = 3.321928094887362;

    private static final BigInteger NONE = BigInteger.ZERO;

    // Example assumptions for the tests only. The app has none built in.
    // 150,000 guesses a second on each of 1,000 machines, average case.
    private static final GuessingAssumptions FAST_GUESSES =
            new GuessingAssumptions(1 / 1.5e5, 1000, 0.5);
    // 30 guesses a second on each of 1,000 machines, average case.
    private static final GuessingAssumptions SLOW_GUESSES =
            new GuessingAssumptions(1 / 30.0, 1000, 0.5);

    @Test
    public void passphraseEntropyBits_nothingRejected_isWordsTimesBitsPerWord() {
        assertEquals(BITS_PER_WORD, CredentialStrength.passphraseEntropyBits(1, 7776, NONE), 1e-12);
        assertEquals(64.6240625180289, CredentialStrength.passphraseEntropyBits(5, 7776, NONE),
                1e-12);
        assertEquals(77.5488750216347, CredentialStrength.passphraseEntropyBits(6, 7776, NONE),
                1e-12);
        assertEquals(90.4736875252405, CredentialStrength.passphraseEntropyBits(7, 7776, NONE),
                1e-12);
        assertEquals(103.3985000288462, CredentialStrength.passphraseEntropyBits(8, 7776, NONE),
                1e-12);
    }

    @Test
    public void rejectionLossBits_simpleShares() {
        assertEquals(0.0, CredentialStrength.rejectionLossBits(5, 7776, NONE), 0.0);
        // Half of the phrases gone: one bit. A quarter gone: log2(4/3).
        assertEquals(1.0, CredentialStrength.rejectionLossBits(3, 2, BigInteger.valueOf(4)), 1e-15);
        assertEquals(0.4150374992788438,
                CredentialStrength.rejectionLossBits(2, 2, BigInteger.ONE), 1e-15);
        assertEquals(3.0, CredentialStrength.passphraseEntropyBits(4, 2, BigInteger.valueOf(8)),
                1e-15);
    }

    @Test
    public void rejectionLossBits_fiveEffWords() {
        // 3,707,401,898 of 7776^5 = 28,430,288,029,929,701,376 phrases are never returned.
        // The share is 1.30403e-10; divided by ln 2 that is 1.88132119e-10 bits.
        final BigInteger rejected = new BigInteger("3707401898");

        assertEquals(1.88132119e-10, CredentialStrength.rejectionLossBits(5, 7776, rejected),
                1e-18);
        assertEquals(64.62406251784077,
                CredentialStrength.passphraseEntropyBits(5, 7776, rejected), 1e-12);
    }

    @Test
    public void rejectionLossBits_sixEffWords_isNotRoundedToZero() {
        // 10,860 of 7776^6: a share of 4.912384e-20, 7.087072e-20 bits.
        final double loss =
                CredentialStrength.rejectionLossBits(6, 7776, BigInteger.valueOf(10860));

        assertEquals(7.087072e-20, loss, 1e-25);
    }

    @Test
    public void rejectionLossBits_refusesBadArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> CredentialStrength.rejectionLossBits(0, 7776, NONE));
        assertThrows(IllegalArgumentException.class,
                () -> CredentialStrength.rejectionLossBits(5, 0, NONE));
        assertThrows(IllegalArgumentException.class,
                () -> CredentialStrength.rejectionLossBits(5, 7776, BigInteger.valueOf(-1)));
        // Everything rejected leaves nothing to generate.
        assertThrows(IllegalArgumentException.class,
                () -> CredentialStrength.rejectionLossBits(2, 2, BigInteger.valueOf(4)));
    }

    @Test
    public void generatedPinEntropyBits_isDigitsTimesBitsPerDigit() {
        assertEquals(BITS_PER_DIGIT, CredentialStrength.generatedPinEntropyBits(1), 1e-12);
        assertEquals(19.931568569324174, CredentialStrength.generatedPinEntropyBits(6), 1e-12);
        assertEquals(66.43856189774725, CredentialStrength.generatedPinEntropyBits(20), 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> CredentialStrength.generatedPinEntropyBits(0));
    }

    @Test
    public void secondsToGuess_followsTheAssumptions() {
        // 2^10 = 1,024 values, all of them tried, one second each, on one machine.
        assertEquals(1024, CredentialStrength.secondsToGuess(10,
                new GuessingAssumptions(1, 1, 1)), 1e-9);
        // Half of them on average.
        assertEquals(512, CredentialStrength.secondsToGuess(10,
                new GuessingAssumptions(1, 1, 0.5)), 1e-9);
        // A thousandth of a second each, on four machines.
        assertEquals(0.128, CredentialStrength.secondsToGuess(10,
                new GuessingAssumptions(0.001, 4, 0.5)), 1e-12);
        assertEquals(1, CredentialStrength.secondsToGuess(0, new GuessingAssumptions(1, 1, 1)),
                1e-12);
    }

    @Test
    public void secondsToGuess_fiveWords() {
        // 7776^5 / 2 guesses at 1.5e8 a second: 94,767,626,766 seconds, about 3,003 years.
        final double seconds = CredentialStrength.secondsToGuess(5 * BITS_PER_WORD, FAST_GUESSES);

        assertEquals(94_767_626_766.0, seconds, 10);
    }

    @Test
    public void secondsToGuess_hugeEntropy_isInfiniteNotAnError() {
        final double seconds = CredentialStrength.secondsToGuess(5000, FAST_GUESSES);

        assertEquals(Double.POSITIVE_INFINITY, seconds, 0.0);
        assertEquals(Scale.BEYOND_BILLIONS_OF_YEARS, GuessTime.fromSeconds(seconds).scale);
    }

    @Test
    public void secondsToGuess_refusesBadEntropy() {
        assertThrows(IllegalArgumentException.class,
                () -> CredentialStrength.secondsToGuess(-1, FAST_GUESSES));
        assertThrows(IllegalArgumentException.class,
                () -> CredentialStrength.secondsToGuess(Double.NaN, FAST_GUESSES));
        assertThrows(IllegalArgumentException.class,
                () -> CredentialStrength.secondsToGuess(Double.POSITIVE_INFINITY, FAST_GUESSES));
    }

    private static void assertTime(Scale scale, int amount, double bits,
            GuessingAssumptions assumptions) {
        final GuessTime time = CredentialStrength.timeToGuess(bits, assumptions);
        assertEquals(scale, time.scale);
        assertEquals(amount, time.amount);
    }

    @Test
    public void timeToGuess_passphrases() {
        // Worked by hand from seconds = 7776^words / 2 / guesses per second, 31,557,600 seconds
        // to the year, rounded down to one figure.
        assertTime(Scale.THOUSANDS_OF_YEARS, 3, 5 * BITS_PER_WORD, FAST_GUESSES);   // 3.0e3
        assertTime(Scale.MILLIONS_OF_YEARS, 10, 5 * BITS_PER_WORD, SLOW_GUESSES);   // 1.5e7
        assertTime(Scale.MILLIONS_OF_YEARS, 20, 6 * BITS_PER_WORD, FAST_GUESSES);   // 2.3e7
        assertTime(Scale.BILLIONS_OF_YEARS, 100, 6 * BITS_PER_WORD, SLOW_GUESSES);  // 1.2e11
        assertTime(Scale.BILLIONS_OF_YEARS, 100, 7 * BITS_PER_WORD, FAST_GUESSES);  // 1.8e11
        assertTime(Scale.BEYOND_BILLIONS_OF_YEARS, 0, 7 * BITS_PER_WORD, SLOW_GUESSES);
        assertTime(Scale.BEYOND_BILLIONS_OF_YEARS, 0, 8 * BITS_PER_WORD, FAST_GUESSES);
    }

    @Test
    public void timeToGuess_pins() {
        final GuessingAssumptions oneFastMachine = new GuessingAssumptions(1 / 1.5e5, 1, 0.5);
        final GuessingAssumptions oneSlowMachine = new GuessingAssumptions(1 / 30.0, 1, 0.5);

        // 6 digits: 500,000 guesses on average.
        assertTime(Scale.UNDER_A_SECOND, 0, 6 * BITS_PER_DIGIT, FAST_GUESSES);      // 0.003 s
        assertTime(Scale.SECONDS, 3, 6 * BITS_PER_DIGIT, oneFastMachine);           // 3.3 s
        assertTime(Scale.SECONDS, 10, 6 * BITS_PER_DIGIT, SLOW_GUESSES);            // 16.7 s
        assertTime(Scale.HOURS, 4, 6 * BITS_PER_DIGIT, oneSlowMachine);             // 4.6 h
        // 12, 16 and 20 digits at 30,000 guesses a second.
        assertTime(Scale.DAYS, 100, 12 * BITS_PER_DIGIT, SLOW_GUESSES);             // 193 days
        assertTime(Scale.THOUSANDS_OF_YEARS, 5, 16 * BITS_PER_DIGIT, SLOW_GUESSES); // 5.3e3
        assertTime(Scale.MILLIONS_OF_YEARS, 50, 20 * BITS_PER_DIGIT, SLOW_GUESSES); // 5.3e7
    }

    @Test
    public void guessingAssumptions_refuseValuesThatMakeNoSense() {
        assertThrows(IllegalArgumentException.class, () -> new GuessingAssumptions(0, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new GuessingAssumptions(-1, 1, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new GuessingAssumptions(Double.NaN, 1, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new GuessingAssumptions(Double.POSITIVE_INFINITY, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new GuessingAssumptions(1, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new GuessingAssumptions(1, Double.NaN, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new GuessingAssumptions(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new GuessingAssumptions(1, 1, 1.5));
        assertThrows(IllegalArgumentException.class,
                () -> new GuessingAssumptions(1, 1, Double.NaN));
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/** Shared by the passphrase tests. None of them needs Android. */
final class PassphraseTestUtils {

    // The shipped list is put on the test class path under the same path it has in the assets.
    private static final String EFF_LARGE_RESOURCE = "/" + WordList.EFF_LARGE_ASSET;

    private PassphraseTestUtils() {}

    /** The bytes of the word list file that ships. */
    static byte[] effLargeBytes() throws IOException {
        try (InputStream in = PassphraseTestUtils.class.getResourceAsStream(EFF_LARGE_RESOURCE)) {
            assertNotNull("word list is not on the class path at " + EFF_LARGE_RESOURCE, in);
            return in.readAllBytes();
        }
    }

    /** The word list that ships, loaded the way the app loads it. */
    static WordList effLarge() throws IOException {
        return WordList.loadEffLarge(new ByteArrayInputStream(effLargeBytes()));
    }

    /**
     * Pearson's chi-square statistic of {@code counts} against the same expected count in every
     * cell.
     */
    static double chiSquare(long[] counts) {
        long total = 0;
        for (long count : counts) {
            total += count;
        }
        final double expected = (double) total / counts.length;
        double sum = 0;
        for (long count : counts) {
            sum += (count - expected) * (count - expected) / expected;
        }
        return sum;
    }

    /**
     * Asserts that {@code counts} look uniform: the chi-square statistic is within five standard
     * deviations of its mean. For uniform counts the statistic has mean {@code cells - 1} and
     * variance {@code 2 * (cells - 1)}. The samples come from a fixed seed, so a pass or a fail
     * is the same on every run.
     */
    static void assertUniform(String what, long[] counts) {
        final int freedom = counts.length - 1;
        final double statistic = chiSquare(counts);
        final double slack = 5 * Math.sqrt(2.0 * freedom);
        assertTrue(what + ": chi-square " + statistic + " is not within " + slack + " of "
                + freedom, Math.abs(statistic - freedom) <= slack);
    }

    /** Whether {@link #assertUniform} would fail. */
    static boolean looksBiased(long[] counts) {
        final int freedom = counts.length - 1;
        return Math.abs(chiSquare(counts) - freedom) > 5 * Math.sqrt(2.0 * freedom);
    }

    /**
     * A source that gives the same good-quality bytes on every run: SplitMix64, one step for
     * every eight bytes. {@code java.util.Random} with a seed is not good enough here: the low
     * bits of its output repeat after a few hundred thousand draws, which shows up as a bias
     * that the code under test does not have.
     */
    static class SeededRandom extends Random {
        private long mState;

        SeededRandom(long seed) {
            mState = seed;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; ) {
                mState += 0x9E3779B97F4A7C15L;
                long z = mState;
                z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
                z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
                z ^= z >>> 31;
                for (int n = 0; n < 8 && i < bytes.length; n++, z >>>= 8) {
                    bytes[i++] = (byte) z;
                }
            }
        }
    }

    /**
     * A source whose {@code nextBytes} hands out the given values in turn, each as big-endian
     * bytes filling the buffer. With a two-byte buffer and the EFF list, a value below 7,776 is
     * the index of the word that is drawn.
     */
    static final class ScriptedRandom extends Random {
        private final int[] mValues;
        private int mNext;

        ScriptedRandom(int... values) {
            mValues = values;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            int value = mValues[mNext++];
            for (int i = bytes.length - 1; i >= 0; i--) {
                bytes[i] = (byte) value;
                value >>>= 8;
            }
        }

        boolean isUsedUp() {
            return mNext == mValues.length;
        }
    }
}

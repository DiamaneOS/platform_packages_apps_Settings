/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.util.Random;

/**
 * Draws an index below a bound so that every index is exactly as likely as any other.
 *
 * <p>How: take just enough random bytes, keep the lowest {@code b} bits, where {@code 2^b} is the
 * smallest power of two that is not below the bound, and draw again if the value is not below
 * the bound. If the bytes are uniform, the kept bits are uniform over {@code 0..2^b-1}; every
 * index below the bound is one of those values, so each has the same chance, {@code 1/2^b} per
 * draw, and a draw that is thrown away tells nothing about the next one. Nothing is reduced with
 * a remainder, so there is no modulo bias.
 *
 * <p>For the 7,772 words a passphrase is drawn from: 13 bits, 8,192 values, 420 thrown away, so
 * about one draw in twenty is repeated.
 */
final class UniformIndex {

    /** Largest bound supported. */
    static final int MAX_BOUND = 1 << 30;

    private UniformIndex() {}

    /** Number of random bytes one draw below {@code bound} takes. */
    static int bytesNeeded(int bound) {
        return (bitsNeeded(bound) + 7) / 8;
    }

    /**
     * Returns an index from 0 to {@code bound} - 1, each equally likely.
     *
     * @param buffer filled with random bytes on each draw; it must be {@link #bytesNeeded} long.
     *     The caller wipes it: it holds the bytes the index came from.
     */
    static int next(Random random, int bound, byte[] buffer) {
        final int bits = bitsNeeded(bound);
        if (buffer.length != (bits + 7) / 8) {
            throw new IllegalArgumentException("wrong buffer size");
        }
        final int mask = (1 << bits) - 1;
        while (true) {
            random.nextBytes(buffer);
            int value = 0;
            for (byte b : buffer) {
                value = (value << 8) | (b & 0xff);
            }
            value &= mask;
            if (value < bound) {
                return value;
            }
        }
    }

    private static int bitsNeeded(int bound) {
        if (bound < 1 || bound > MAX_BOUND) {
            throw new IllegalArgumentException("bound out of range");
        }
        return 32 - Integer.numberOfLeadingZeros(bound - 1);
    }
}

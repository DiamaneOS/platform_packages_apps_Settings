/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.PassphraseTestUtils.SeededRandom;

import org.junit.Test;

import java.util.Random;

public class UniformIndexTest {

    // The number of words a passphrase is drawn from.
    private static final int EFF = WordList.EFF_LARGE_LETTERS_ONLY_SIZE;

    /** Hands out every possible content of the buffer exactly once, then stops the test loop. */
    private static final class EveryValueOnce extends Random {
        private long mNext;

        @Override
        public void nextBytes(byte[] bytes) {
            if (mNext == 1L << (8 * bytes.length)) {
                throw new UsedUp();
            }
            long value = mNext++;
            for (int i = bytes.length - 1; i >= 0; i--) {
                bytes[i] = (byte) value;
                value >>>= 8;
            }
        }
    }

    private static final class UsedUp extends RuntimeException {}

    /**
     * Feeds a draw every possible content of its random bytes once and counts what comes out.
     * If each index comes out equally often, uniform bytes give uniform indices: that is the
     * whole proof, by going through all cases.
     */
    private static long[] countOverAllRandomBytes(int bound) {
        final long[] counts = new long[bound];
        final byte[] buffer = new byte[UniformIndex.bytesNeeded(bound)];
        final Random everyValue = new EveryValueOnce();
        try {
            while (true) {
                counts[UniformIndex.next(everyValue, bound, buffer)]++;
            }
        } catch (UsedUp done) {
            return counts;
        }
    }

    @Test
    public void next_effList_everyIndexFromTheSameNumberOfByteValues() {
        final long[] counts = countOverAllRandomBytes(EFF);

        // Two bytes, 65,536 values. 13 bits are kept, so each index is hit by 8 values, and
        // the 420 * 8 values from 7,772 up are thrown away.
        long accepted = 0;
        for (long count : counts) {
            assertEquals(8, count);
            accepted += count;
        }
        assertEquals(7772, counts.length);
        assertEquals(65536 - 420 * 8, accepted);
    }

    @Test
    public void next_otherBounds_everyIndexFromTheSameNumberOfByteValues() {
        final int[] bounds = {2, 3, 5, 6, 7, 10, 100, 255, 256, 257, 1000, 4096, 65535, 65536,
                65537, 70000};
        for (int bound : bounds) {
            final long[] counts = countOverAllRandomBytes(bound);
            for (long count : counts) {
                assertTrue("bound " + bound, count > 0);
                assertEquals("bound " + bound, counts[0], count);
            }
        }
    }

    @Test
    public void next_boundOne_isZeroWithoutRandomBytes() {
        assertEquals(0, UniformIndex.bytesNeeded(1));
        assertEquals(0, UniformIndex.next(new SeededRandom(1), 1, new byte[0]));
    }

    @Test
    public void bytesNeeded_isJustEnough() {
        assertEquals(1, UniformIndex.bytesNeeded(2));
        assertEquals(1, UniformIndex.bytesNeeded(256));
        assertEquals(2, UniformIndex.bytesNeeded(257));
        assertEquals(2, UniformIndex.bytesNeeded(EFF));
        assertEquals(2, UniformIndex.bytesNeeded(65536));
        assertEquals(3, UniformIndex.bytesNeeded(65537));
        assertEquals(4, UniformIndex.bytesNeeded(UniformIndex.MAX_BOUND));
    }

    @Test
    public void next_refusesBadArguments() {
        final Random random = new SeededRandom(1);
        assertThrows(IllegalArgumentException.class,
                () -> UniformIndex.next(random, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> UniformIndex.next(random, -5, new byte[1]));
        assertThrows(IllegalArgumentException.class,
                () -> UniformIndex.next(random, UniformIndex.MAX_BOUND + 1, new byte[4]));
        assertThrows(IllegalArgumentException.class,
                () -> UniformIndex.next(random, EFF, new byte[1]));
        assertThrows(IllegalArgumentException.class,
                () -> UniformIndex.next(random, EFF, new byte[3]));
    }

    @Test
    public void next_staysBelowTheBound() {
        final Random random = new SeededRandom(7);
        final byte[] buffer = new byte[UniformIndex.bytesNeeded(EFF)];
        for (int i = 0; i < 1_000_000; i++) {
            final int index = UniformIndex.next(random, EFF, buffer);
            assertTrue(index >= 0 && index < EFF);
        }
    }

    @Test
    public void next_effList_largeSampleLooksUniform() {
        // 1,000 draws expected per word.
        final Random random = new SeededRandom(20261010);
        final byte[] buffer = new byte[UniformIndex.bytesNeeded(EFF)];
        final long[] counts = new long[EFF];
        for (int i = 0; i < EFF * 1000; i++) {
            counts[UniformIndex.next(random, EFF, buffer)]++;
        }

        PassphraseTestUtils.assertUniform("index draw", counts);
    }

    @Test
    public void uniformityCheck_catchesModuloBias() {
        // The same sample size with the mistake this class exists to avoid: 13 random bits
        // reduced with a remainder, which makes the first 420 words twice as likely.
        final Random random = new SeededRandom(20261010);
        final byte[] buffer = new byte[2];
        final long[] counts = new long[EFF];
        for (int i = 0; i < EFF * 1000; i++) {
            random.nextBytes(buffer);
            final int thirteenBits = ((buffer[0] & 0x1f) << 8) | (buffer[1] & 0xff);
            counts[thirteenBits % EFF]++;
        }

        assertTrue(PassphraseTestUtils.looksBiased(counts));
    }
}

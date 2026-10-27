/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.PassphraseTestUtils.ScriptedRandom;
import com.android.settings.password.passphrase.PassphraseTestUtils.SeededRandom;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public class ShortListGeneratorTest {

    private static final int SEED = 20261027;

    private WordList mDrawn;

    @Before
    public void setUp() throws Exception {
        mDrawn = PassphraseTestUtils.effShort().lettersOnly();
    }

    /** The index of the drawn word with the first letters at {@code offset}, or -1. */
    private int wordAt(char[] letters, int offset) {
        return mDrawn.indexOfPrefix(letters, offset, ShortListGenerator.PREFIX_LENGTH);
    }

    @Test
    public void credential_isEighteenLowercaseLettersFromSixWords() throws Exception {
        assertEquals(6, ShortListGenerator.WORDS);
        assertEquals(3, ShortListGenerator.PREFIX_LENGTH);
        assertEquals(18, ShortListGenerator.LENGTH);
        final ShortListGenerator seeded = new ShortListGenerator(
                PassphraseTestUtils.effShort(), new SeededRandom(SEED));
        final ShortListGenerator secure = new ShortListGenerator(
                PassphraseTestUtils.effShort(), new SecureRandom());

        for (ShortListGenerator generator : new ShortListGenerator[] {seeded, secure}) {
            for (int i = 0; i < 20_000; i++) {
                try (Passphrase credential = generator.generate()) {
                    final char[] letters = credential.chars();
                    assertEquals(18, letters.length);
                    assertEquals(6, credential.wordCount());
                    for (char c : letters) {
                        assertTrue("character " + (int) c, c >= 'a' && c <= 'z');
                    }
                    for (int word = 0; word < 6; word++) {
                        assertTrue(wordAt(letters, word * 3) >= 0);
                    }
                    assertTrue(ShortListGenerator.meetsFloor(letters));
                }
            }
        }
    }

    @Test
    public void drawnWords_areAllButYoYo() throws Exception {
        final ShortListGenerator generator = new ShortListGenerator(
                PassphraseTestUtils.effShort(), new SeededRandom(SEED));

        assertEquals(1295, generator.drawnWords().size());
        assertEquals(-1, generator.drawnWords().indexOfPrefix("yo-".toCharArray(), 0, 3));
        // Framework's LockCredentialPolicyTest pins the same hash of the prefixes it accepts.
        final StringBuilder prefixes = new StringBuilder();
        for (int i = 0; i < generator.drawnWords().size(); i++) {
            prefixes.append(generator.drawnWords().word(i), 0, 3);
        }
        assertEquals("ef92078b878508cbd4c33129e107999979e5d8bc14705c3818a202709871f6d7",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(prefixes.toString().getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    public void generate_everyWordEquallyLikelyInEveryPosition() throws Exception {
        // 100 credentials expected per word and position.
        final ShortListGenerator generator = new ShortListGenerator(
                PassphraseTestUtils.effShort(), new SeededRandom(SEED));
        final long[][] counts = new long[6][mDrawn.size()];
        for (int i = 0; i < mDrawn.size() * 100; i++) {
            try (Passphrase credential = generator.generate()) {
                for (int position = 0; position < 6; position++) {
                    counts[position][wordAt(credential.chars(), position * 3)]++;
                }
            }
        }

        for (int position = 0; position < 6; position++) {
            PassphraseTestUtils.assertUniform("word " + position, counts[position]);
        }
    }

    @Test
    public void generate_underTheFloor_drawsAllSixAgain() throws Exception {
        // aardvark agency grape eggnog earlobe greyhound: four different letters. Then uncle
        // abandoned fox gem oat pry.
        final ScriptedRandom random = new ScriptedRandom(
                0, 28, 516, 344, 329, 517, 1155, 1, 464, 482, 806, 909);
        final ShortListGenerator generator =
                new ShortListGenerator(PassphraseTestUtils.effShort(), random);

        try (Passphrase credential = generator.generate()) {
            assertArrayEquals("uncabafoxgemoatpry".toCharArray(), credential.chars());
        }
        assertTrue(random.isUsedUp());
        assertFalse(ShortListGenerator.meetsFloor("aaragegraeggeargre".toCharArray()));
        assertTrue(ShortListGenerator.meetsFloor("aaragegraeggeargry".toCharArray()));
    }

    @Test
    public void generate_secureRandom_differs() throws Exception {
        final ShortListGenerator generator = new ShortListGenerator(
                PassphraseTestUtils.effShort(), new SecureRandom());

        try (Passphrase first = generator.generate(); Passphrase second = generator.generate()) {
            // The chance that two are equal is 2^-62.
            assertNotEquals(new String(first.chars()), new String(second.chars()));
        }
    }

    @Test
    public void estimate_countsTenPointThreeBitsAWord_belowTheExactFigure() throws Exception {
        assertEquals(10.3, ShortListGenerator.ESTIMATE_BITS_PER_WORD, 0);
        assertEquals(61.8, ShortListGenerator.ESTIMATE_BITS, 1e-12);

        // Draws under the floor have fewer than five letters, so all their prefixes fit in one
        // set of four letters. Counting the draws for each such set gives an upper bound.
        final int[] letterSets = new int[mDrawn.size()];
        for (int i = 0; i < letterSets.length; i++) {
            for (int c = 0; c < 3; c++) {
                letterSets[i] |= 1 << (mDrawn.word(i).charAt(c) - 'a');
            }
        }
        double rejectedShare = 0;
        for (int set = 0b1111; set < 1 << 26; set = nextSetOfFour(set)) {
            int inSet = 0;
            for (int letters : letterSets) {
                if ((letters & ~set) == 0) {
                    inSet++;
                }
            }
            rejectedShare += Math.pow((double) inSet / mDrawn.size(), 6);
        }
        final double lossBits = -Math.log1p(-rejectedShare) / Math.log(2);
        final double exactBits = 6 * Math.log(1295) / Math.log(2) - lossBits;

        assertTrue("loss " + lossBits, lossBits < 1e-6);
        assertEquals(62.03, exactBits, 0.005);
        assertTrue(ShortListGenerator.ESTIMATE_BITS < exactBits);
    }

    // The next larger number with as many bits set (Gosper's hack).
    private static int nextSetOfFour(int set) {
        final int lowest = set & -set;
        final int ripple = set + lowest;
        return ripple | (((set ^ ripple) >>> 2) / lowest);
    }
}

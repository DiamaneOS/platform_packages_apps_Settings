/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The words a short-list password stands for. The lock settings service generates it: the
 * distribution and length are tested with it, in frameworks/base ShortListPasswordsTest.
 */
public class ShortListTest {

    private WordList mDrawn;

    @Before
    public void setUp() throws Exception {
        mDrawn = ShortList.drawnWords(PassphraseTestUtils.effShort());
    }

    @Test
    public void credential_isEighteenLettersOfSixWords() {
        assertEquals(6, ShortList.WORDS);
        assertEquals(3, ShortList.PREFIX_LENGTH);
        assertEquals(18, ShortList.LENGTH);
    }

    @Test
    public void drawnWords_areAllButYoYo_withThePrefixesOfTheService() throws Exception {
        assertEquals(1295, mDrawn.size());
        assertEquals(-1, mDrawn.indexOfPrefix("yo-".toCharArray(), 0, 3));
        // frameworks/base LockCredentialPolicyTest pins the same hash of the prefixes the
        // service generates from.
        final StringBuilder prefixes = new StringBuilder();
        for (int i = 0; i < mDrawn.size(); i++) {
            prefixes.append(mDrawn.word(i), 0, 3);
        }
        assertEquals("ef92078b878508cbd4c33129e107999979e5d8bc14705c3818a202709871f6d7",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(prefixes.toString().getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    public void isShownBy_onlySixPrefixesOfTheList() {
        assertTrue(ShortList.isShownBy("uncabafoxgemoatpry".toCharArray(), mDrawn));
        assertFalse(ShortList.isShownBy("uncabafoxgemoatqqq".toCharArray(), mDrawn));
        assertFalse(ShortList.isShownBy("uncabafoxgemoatyo-".toCharArray(), mDrawn));
        assertFalse(ShortList.isShownBy("uncabafoxgemoat".toCharArray(), mDrawn));
        assertFalse(ShortList.isShownBy("uncabafoxgemoatprypry".toCharArray(), mDrawn));
    }

    @Test
    public void drawnWords_refusesWordsThatShareTheirStart() {
        assertThrows(IllegalArgumentException.class,
                () -> ShortList.drawnWords(WordList.of("abacus", "abandon")));
    }

    @Test
    public void estimate_countsTenPointThreeBitsAWord_belowTheExactFigure() throws Exception {
        assertEquals(10.3, ShortList.ESTIMATE_BITS_PER_WORD, 0);
        assertEquals(61.8, ShortList.ESTIMATE_BITS, 1e-12);

        // The service draws again when there are fewer than five different letters. All the
        // prefixes of such a draw fit in one set of four letters, which gives an upper bound.
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
        assertTrue(ShortList.ESTIMATE_BITS < exactBits);
    }

    // The next larger number with as many bits set (Gosper's hack).
    private static int nextSetOfFour(int set) {
        final int lowest = set & -set;
        final int ripple = set + lowest;
        return ripple | (((set ^ ripple) >>> 2) / lowest);
    }
}

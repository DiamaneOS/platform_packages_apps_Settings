/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** EFF's short word list 2 as it ships. */
public class ShortWordListTest {

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    public void shippedFile_isTheOfficialListWithoutDiceNumbers() throws Exception {
        final WordList list = PassphraseTestUtils.effShort();

        assertEquals(WordList.EFF_SHORT_SHA256, sha256(PassphraseTestUtils.effShortBytes()));
        assertEquals(1296, list.size());
        // Put back the four dice of each line, 1111 to 6666 in order, and a tab: the result is
        // the published file, eff_short_wordlist_2_0.txt.
        final ByteArrayOutputStream official = new ByteArrayOutputStream();
        for (int i = 0; i < list.size(); i++) {
            final char[] dice = new char[4];
            int rest = i;
            for (int d = 3; d >= 0; d--) {
                dice[d] = (char) ('1' + rest % 6);
                rest /= 6;
            }
            official.write((new String(dice) + "\t" + list.word(i) + "\n")
                    .getBytes(StandardCharsets.US_ASCII));
        }
        assertEquals("22b45c52e0bd0bbf03aa522240b111eb4c7c0c1d86c4e518e1be2a7eb2a625e4",
                sha256(official.toByteArray()));
    }

    @Test
    public void everyWordHasItsOwnFirstThreeLetters() throws Exception {
        final WordList list = PassphraseTestUtils.effShort();

        final Set<String> prefixes = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            assertTrue(list.word(i).length() >= 3);
            prefixes.add(list.word(i).substring(0, 3));
        }
        assertEquals(1296, prefixes.size());
        assertTrue(list.hasUniqueSortedPrefixes(3));
        assertFalse(list.hasUniqueSortedPrefixes(2));
    }

    @Test
    public void lettersOnly_leavesOutYoYo() throws Exception {
        final WordList list = PassphraseTestUtils.effShort();
        final WordList drawn = list.lettersOnly();

        assertEquals(WordList.EFF_SHORT_LETTERS_ONLY_SIZE, drawn.size());
        assertEquals(1295, drawn.size());
        for (int i = 0, j = 0; i < list.size(); i++) {
            if (!list.word(i).equals("yo-yo")) {
                assertEquals(list.word(i), drawn.word(j++));
            }
        }
        assertTrue(drawn.hasUniqueSortedPrefixes(3));
    }

    @Test
    public void indexOfPrefix_findsEachWord() throws Exception {
        final WordList drawn = PassphraseTestUtils.effShort().lettersOnly();

        for (int i = 0; i < drawn.size(); i++) {
            final char[] letters = ("x" + drawn.word(i)).toCharArray();
            assertEquals(i, drawn.indexOfPrefix(letters, 1, 3));
        }
        assertEquals(-1, drawn.indexOfPrefix("qqq".toCharArray(), 0, 3));
        assertEquals(-1, drawn.indexOfPrefix("aaa".toCharArray(), 0, 3));
        assertEquals(-1, drawn.indexOfPrefix("zzz".toCharArray(), 0, 3));
    }

    @Test
    public void hasUniqueSortedPrefixes_refusesSharedOrUnsortedOrShort() {
        assertFalse(WordList.of("ace", "acid", "bat").hasUniqueSortedPrefixes(2));
        assertTrue(WordList.of("ace", "aid", "bat").hasUniqueSortedPrefixes(2));
        assertFalse(WordList.of("bat", "ace").hasUniqueSortedPrefixes(2));
        assertFalse(WordList.of("a", "bat").hasUniqueSortedPrefixes(2));
    }

    @Test
    public void loadEffShort_refusesAChangedListOrTheLargeOne() throws Exception {
        final byte[] bytes = PassphraseTestUtils.effShortBytes();
        final byte[] oneLetterChanged = bytes.clone();
        oneLetterChanged[0] = 'b';

        for (byte[] content : new byte[][] {oneLetterChanged, new byte[0],
                PassphraseTestUtils.effLargeBytes()}) {
            assertThrows(IOException.class,
                    () -> WordList.loadEffShort(new ByteArrayInputStream(content)));
        }
    }
}

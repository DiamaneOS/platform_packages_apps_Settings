/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertArrayEquals;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

public class WordListTest {

    // SHA-256 of eff_large_wordlist.txt as EFF publishes it, with the dice numbers.
    private static final String OFFICIAL_SHA256 =
            "addd35536511597a02fa0a9ff1e5284677b8883b83e986e43f15a3db996b903e";
    private static final int OFFICIAL_BYTES = 108800;

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    public void shippedFile_hasThePinnedHash() throws Exception {
        final byte[] bytes = PassphraseTestUtils.effLargeBytes();

        assertEquals(WordList.EFF_LARGE_SHA256, sha256(bytes));
        assertEquals(62144, bytes.length);
    }

    @Test
    public void shippedFile_isTheOfficialListWithoutDiceNumbers() throws Exception {
        final WordList list = PassphraseTestUtils.effLarge();

        // Put back what was stripped: the five dice of each line, 11111 to 66666 in order, and
        // a tab. The result must be the published file, byte for byte.
        final ByteArrayOutputStream official = new ByteArrayOutputStream();
        for (int i = 0; i < list.size(); i++) {
            final char[] dice = new char[5];
            int rest = i;
            for (int d = 4; d >= 0; d--) {
                dice[d] = (char) ('1' + rest % 6);
                rest /= 6;
            }
            final String line = new String(dice) + "\t" + list.word(i) + "\n";
            official.write(line.getBytes(StandardCharsets.US_ASCII));
        }

        assertEquals(OFFICIAL_BYTES, official.size());
        assertEquals(OFFICIAL_SHA256, sha256(official.toByteArray()));
    }

    @Test
    public void shippedList_has7776DifferentWords() throws Exception {
        final WordList list = PassphraseTestUtils.effLarge();
        final Set<String> words = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            words.add(list.word(i));
        }

        assertEquals(7776, WordList.EFF_LARGE_SIZE);
        assertEquals(7776, list.size());
        assertEquals(7776, words.size());
    }

    @Test
    public void shippedList_isLowercaseLettersAndFourHyphenWords() throws Exception {
        final WordList list = PassphraseTestUtils.effLarge();
        final Set<String> notOnlyLetters = new TreeSet<>();
        for (int i = 0; i < list.size(); i++) {
            final String word = list.word(i);
            assertTrue(word, word.matches("[a-z]+(-[a-z]+)*"));
            if (!word.matches("[a-z]+")) {
                notOnlyLetters.add(word);
            }
        }

        assertEquals(new TreeSet<>(Arrays.asList("drop-down", "felt-tip", "t-shirt", "yo-yo")),
                notOnlyLetters);
    }

    @Test
    public void shippedList_wordLengths() throws Exception {
        final WordList list = PassphraseTestUtils.effLarge();
        final int[] byLength = new int[10];
        for (int i = 0; i < list.size(); i++) {
            byLength[list.word(i).length()]++;
        }

        // 82 words of three letters: the only way to a 5-word phrase under 20 characters.
        assertArrayEquals(new int[] {0, 0, 0, 82, 467, 928, 1372, 1591, 1779, 1557}, byLength);
        assertEquals(9, list.longestWord());
    }

    @Test
    public void shippedList_noWordStartsAnother() throws Exception {
        final WordList list = PassphraseTestUtils.effLarge();
        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < list.size(); j++) {
                if (i != j) {
                    assertFalse(list.word(j).startsWith(list.word(i)));
                }
            }
        }
    }

    @Test
    public void shippedList_keepsTheOfficialOrder() throws Exception {
        final WordList list = PassphraseTestUtils.effLarge();

        assertEquals("abacus", list.word(0));
        assertEquals("zoom", list.word(7775));
    }

    @Test
    public void copyWord_copiesAtTheOffset() throws Exception {
        final WordList list = WordList.of("ace", "window");
        final char[] dest = new char[10];

        assertEquals(4, list.copyWord(0, dest, 1));
        assertEquals(10, list.copyWord(1, dest, 4));
        assertArrayEquals("\0acewindow".toCharArray(), dest);
    }

    @Test
    public void loadEffLarge_refusesAChangedList() throws Exception {
        final byte[] bytes = PassphraseTestUtils.effLargeBytes();

        final byte[] oneLetterChanged = bytes.clone();
        oneLetterChanged[0] = 'b';
        assertRefused(oneLetterChanged);

        // The same words with a different line end.
        assertRefused(new String(bytes, StandardCharsets.US_ASCII).replace("\n", "\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        assertRefused(Arrays.copyOf(bytes, bytes.length - 1));
        assertRefused(Arrays.copyOf(bytes, bytes.length + 1));
        assertRefused(new byte[0]);
        assertRefused(new byte[1 << 20]);
    }

    private static void assertRefused(byte[] content) {
        assertThrows(IOException.class,
                () -> WordList.loadEffLarge(new ByteArrayInputStream(content)));
    }

    @Test
    public void of_refusesWhatIsNotAWordList() {
        assertThrows(IllegalArgumentException.class, () -> WordList.of());
        assertThrows(IllegalArgumentException.class, () -> WordList.of("ace", "ace"));
        assertThrows(IllegalArgumentException.class, () -> WordList.of("ace", ""));
        assertThrows(IllegalArgumentException.class, () -> WordList.of("Ace"));
        assertThrows(IllegalArgumentException.class, () -> WordList.of("ace1"));
        assertThrows(IllegalArgumentException.class, () -> WordList.of("two words"));
        assertThrows(IllegalArgumentException.class, () -> WordList.of("-ace"));
        assertThrows(IllegalArgumentException.class, () -> WordList.of("ace-"));
        assertThrows(IllegalArgumentException.class, () -> WordList.of("café"));
    }
}

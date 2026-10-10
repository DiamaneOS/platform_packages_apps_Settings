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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class PassphraseFloorTest {

    private static boolean isMet(String phrase) {
        return PassphraseFloor.isMet(phrase.toCharArray(), phrase.length());
    }

    @Test
    public void isMet_needs20Characters() {
        assertFalse(isMet("ace bid fog hum jar"));           // 19
        assertTrue(isMet("ace bid fog hum jars"));           // 20
        assertFalse(isMet(""));
    }

    @Test
    public void isMet_needs5DifferentCharacters() {
        assertFalse(isMet("aaaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse(isMet("abababababababababababab"));
        assertFalse(isMet("mama mama mama mama mama"));      // m, a, space
        assertFalse(isMet("abcd abcd abcd abcd abcd".replace(' ', 'a')));
        assertTrue(isMet("abcd abcd abcd abcd abcd"));       // a, b, c, d, space
    }

    @Test
    public void isMet_refusesDigitsOnly() {
        assertFalse(isMet("01234567890123456789"));
        assertTrue(isMet("0123456789012345678a"));
    }

    @Test
    public void isMet_refusesNonAscii() {
        assertFalse(isMet("café bid fog hum jars window"));
    }

    @Test
    public void isMet_looksOnlyAtTheGivenLength() {
        final char[] buffer = "ace bid fog hum jar window".toCharArray();

        assertFalse(PassphraseFloor.isMet(buffer, 19));
        assertTrue(PassphraseFloor.isMet(buffer, 20));
    }

    /** Counts by building every phrase and asking {@link PassphraseFloor#isMet}. */
    private static long countRejectedOneByOne(WordList list, int words) {
        final int[] pick = new int[words];
        final char[] chars = new char[words * list.longestWord() + words - 1];
        long rejected = 0;
        while (true) {
            int length = 0;
            for (int i = 0; i < words; i++) {
                if (i > 0) {
                    chars[length++] = PassphraseGenerator.SEPARATOR;
                }
                length = list.copyWord(pick[i], chars, length);
            }
            if (!PassphraseFloor.isMet(chars, length)) {
                rejected++;
            }
            int position = words - 1;
            while (position >= 0 && ++pick[position] == list.size()) {
                pick[position--] = 0;
            }
            if (position < 0) {
                return rejected;
            }
        }
    }

    @Test
    public void countRejected_smallLists_matchesCountingOneByOne() {
        final WordList[] lists = {
            WordList.of("ace", "bid", "fog", "hum", "jar", "elk", "window", "planet"),
            WordList.of("mama", "papaya", "banana", "yo-yo", "yoyo", "aqua", "ace", "ant", "zoom",
                    "abdominal", "t-shirt", "level", "eel"),
            WordList.of("aa", "ab", "abc", "abcd", "abcde", "abcdefghi", "b", "z-z"),
            WordList.of("window"),
        };
        for (WordList list : lists) {
            for (int words = 1; words <= 6; words++) {
                assertEquals("list of " + list.size() + ", " + words + " words",
                        BigInteger.valueOf(countRejectedOneByOne(list, words)),
                        PassphraseFloor.countRejected(list, words));
            }
        }
    }

    @Test
    public void countRejected_effSample_matchesCountingOneByOne() throws Exception {
        // Every 97th word of the real list, and all of its words with few different letters.
        final WordList eff = PassphraseTestUtils.effLarge();
        final List<String> sample = new ArrayList<>();
        for (int i = 0; i < eff.size(); i++) {
            final String word = eff.word(i);
            if (i % 97 == 0 || word.chars().distinct().count() <= 3) {
                sample.add(word);
            }
        }
        final WordList list = WordList.of(sample.toArray(new String[0]));

        for (int words = 1; words <= 3; words++) {
            assertEquals(words + " words",
                    BigInteger.valueOf(countRejectedOneByOne(list, words)),
                    PassphraseFloor.countRejected(list, words));
        }
    }

    /**
     * Counts the sequences of {@code words} words whose characters, all together, are at most
     * {@code maxDistinct} different ones, by trying to extend each such sequence with each
     * word. {@code shortOnly} counts only those made of three-letter words.
     */
    private static long countFewCharacters(WordList list, int words, int maxDistinct,
            boolean shortOnly, long charsSoFar) {
        if (words == 0) {
            return 1;
        }
        long count = 0;
        for (int i = 0; i < list.size(); i++) {
            final String word = list.word(i);
            if (shortOnly && word.length() != 3) {
                continue;
            }
            long chars = charsSoFar;
            for (int c = 0; c < word.length(); c++) {
                final char letter = word.charAt(c);
                chars |= 1L << (letter == '-' ? 26 : letter - 'a');
            }
            if (Long.bitCount(chars) <= maxDistinct) {
                count += countFewCharacters(list, words - 1, maxDistinct, shortOnly, chars);
            }
        }
        return count;
    }

    @Test
    public void countRejected_effList_matchesASecondWayOfCounting() throws Exception {
        final WordList list = PassphraseTestUtils.effLarge();

        for (int words = PassphraseGenerator.MIN_WORDS; words <= PassphraseGenerator.MAX_WORDS;
                words++) {
            // Too short: fewer than 20 characters with words - 1 spaces. The shortest words have
            // three letters, so that is five words of three letters and nothing else.
            final BigInteger tooShort =
                    words == 5 ? BigInteger.valueOf(82).pow(5) : BigInteger.ZERO;
            // Too plain: at most three different characters in the words, four with the space.
            final long tooPlain = countFewCharacters(list, words, 3, false, 0);
            final long both = words == 5 ? countFewCharacters(list, words, 3, true, 0) : 0;

            assertEquals(words + " words",
                    tooShort.add(BigInteger.valueOf(tooPlain - both)),
                    PassphraseFloor.countRejected(list, words));
        }
    }

    @Test
    public void countRejected_effList_values() throws Exception {
        final WordList list = PassphraseTestUtils.effLarge();

        // 82^5 = 3,707,398,432 too short, 4,058 too plain, 592 both.
        assertEquals(new BigInteger("3707401898"), PassphraseFloor.countRejected(list, 5));
        assertEquals(BigInteger.valueOf(10860), PassphraseFloor.countRejected(list, 6));
        assertEquals(BigInteger.valueOf(32066), PassphraseFloor.countRejected(list, 7));
        assertEquals(BigInteger.valueOf(103428), PassphraseFloor.countRejected(list, 8));
        // One or two words are never long enough.
        assertEquals(BigInteger.valueOf(7776), PassphraseFloor.countRejected(list, 1));
        assertEquals(BigInteger.valueOf(7776).pow(2), PassphraseFloor.countRejected(list, 2));
    }

    @Test
    public void countRejected_refusesNoWords() {
        assertThrows(IllegalArgumentException.class,
                () -> PassphraseFloor.countRejected(WordList.of("ace"), 0));
    }
}

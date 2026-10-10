/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SecretDisplayTest {

    private static String numbered(String phrase) {
        return new String(SecretDisplay.numberedWords(phrase.toCharArray()));
    }

    private static String grouped(String digits) {
        return new String(SecretDisplay.groupedDigits(digits.toCharArray()));
    }

    @Test
    public void numberedWords_oneWordPerLine() {
        assertEquals("1  abacus\n2  zoom\n3  abdomen\n4  yoyo\n5  zoology",
                numbered("abacus zoom abdomen yoyo zoology"));
        assertEquals("1  abacus", numbered("abacus"));
    }

    @Test
    public void numberedWords_eightWords() {
        assertEquals("1  a\n2  b\n3  c\n4  d\n5  e\n6  f\n7  g\n8  h", numbered("a b c d e f g h"));
    }

    @Test
    public void numberedWords_twoDigitNumbers() {
        assertEquals("1  a\n2  b\n3  c\n4  d\n5  e\n6  f\n7  g\n8  h\n9  i\n10  j\n11  k",
                numbered("a b c d e f g h i j k"));
    }

    @Test
    public void numberedWords_leavesThePhraseAsItWas() {
        final char[] phrase = "abacus zoom".toCharArray();

        SecretDisplay.numberedWords(phrase);

        assertArrayEquals("abacus zoom".toCharArray(), phrase);
    }

    private static String[] columns(String phrase) {
        final char[][] columns = SecretDisplay.numberedWordColumns(phrase.toCharArray());
        assertEquals(2, columns.length);
        return new String[] {new String(columns[0]), new String(columns[1])};
    }

    @Test
    public void numberedWordColumns_halfOnEachSide() {
        assertArrayEquals(new String[] {"1  a\n2  b\n3  c", "4  d\n5  e\n6  f"},
                columns("a b c d e f"));
        assertArrayEquals(new String[] {"1  a\n2  b\n3  c\n4  d", "5  e\n6  f\n7  g\n8  h"},
                columns("a b c d e f g h"));
    }

    @Test
    public void numberedWordColumns_oddCount_theLeftSideHasOneMore() {
        assertArrayEquals(new String[] {"1  a\n2  b\n3  c", "4  d\n5  e"},
                columns("a b c d e"));
        assertArrayEquals(new String[] {"1  a\n2  b\n3  c\n4  d", "5  e\n6  f\n7  g"},
                columns("a b c d e f g"));
    }

    @Test
    public void numberedWordColumns_oneWord_rightSideEmpty() {
        assertArrayEquals(new String[] {"1  abacus", ""}, columns("abacus"));
        assertArrayEquals(new String[] {"1  abacus", "2  zoom"}, columns("abacus zoom"));
    }

    @Test
    public void numberedWordColumns_realWords_nothingLostAndThePhraseUntouched() {
        final char[] phrase = "abacus zoom abdomen yoyo zoology aim".toCharArray();

        final char[][] columns = SecretDisplay.numberedWordColumns(phrase);

        assertEquals("1  abacus\n2  zoom\n3  abdomen", new String(columns[0]));
        assertEquals("4  yoyo\n5  zoology\n6  aim", new String(columns[1]));
        assertArrayEquals("abacus zoom abdomen yoyo zoology aim".toCharArray(), phrase);
    }

    @Test
    public void groupedDigits_groupsOfFour() {
        assertEquals("1234 5678 9012 3456 7890", grouped("12345678901234567890"));
        assertEquals("1234 5678 90", grouped("1234567890"));
        assertEquals("1234", grouped("1234"));
        assertEquals("1234 5", grouped("12345"));
        assertEquals("1", grouped("1"));
        assertEquals("", grouped(""));
    }

    @Test
    public void wipe_overwritesWithZeros() {
        final char[] chars = "abacus zoom".toCharArray();

        SecretDisplay.wipe(chars);

        assertArrayEquals(new char[11], chars);
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SecretDisplayTest {

    private static final String DOTS = "••••••";

    private static String[] words(String phrase, boolean twoColumns) {
        final char[][] columns = SecretDisplay.wordColumns(phrase.toCharArray(), twoColumns);
        assertEquals(2, columns.length);
        return new String[] {new String(columns[0]), new String(columns[1])};
    }

    private static String grouped(String digits) {
        return new String(SecretDisplay.groupedDigits(digits.toCharArray()));
    }

    private static int lineCount(String text) {
        return text.isEmpty() ? 0 : text.split("\n", -1).length;
    }

    @Test
    public void wordColumns_twoColumns_halfOnEachSide() {
        assertArrayEquals(new String[] {"abacus\nzoom\nabdomen", "yoyo\nzoology\naim"},
                words("abacus zoom abdomen yoyo zoology aim", true));
        assertArrayEquals(new String[] {"a\nb\nc\nd", "e\nf\ng\nh"},
                words("a b c d e f g h", true));
    }

    @Test
    public void wordColumns_oddCount_theFirstColumnHasOneMore() {
        assertArrayEquals(new String[] {"a\nb\nc", "d\ne"}, words("a b c d e", true));
        assertArrayEquals(new String[] {"a\nb\nc\nd", "e\nf\ng"}, words("a b c d e f g", true));
    }

    @Test
    public void wordColumns_oneColumn_allInTheFirst() {
        assertArrayEquals(new String[] {"a\nb\nc\nd\ne\nf", ""}, words("a b c d e f", false));
        assertArrayEquals(new String[] {"abacus", ""}, words("abacus", false));
    }

    @Test
    public void wordColumns_oneOrTwoWords() {
        assertArrayEquals(new String[] {"abacus", ""}, words("abacus", true));
        assertArrayEquals(new String[] {"abacus", "zoom"}, words("abacus zoom", true));
    }

    @Test
    public void wordColumns_leavesThePhraseAsItWas() {
        final char[] phrase = "abacus zoom abdomen".toCharArray();

        SecretDisplay.wordColumns(phrase, true);

        assertArrayEquals("abacus zoom abdomen".toCharArray(), phrase);
    }

    @Test
    public void numberColumns_countOn() {
        assertArrayEquals(new String[] {"1\n2\n3", "4\n5\n6"},
                SecretDisplay.numberColumns(6, true));
        assertArrayEquals(new String[] {"1\n2\n3", "4\n5"}, SecretDisplay.numberColumns(5, true));
        assertArrayEquals(new String[] {"1\n2\n3\n4", "5\n6\n7\n8"},
                SecretDisplay.numberColumns(8, true));
        assertArrayEquals(new String[] {"1\n2\n3\n4\n5\n6", ""},
                SecretDisplay.numberColumns(6, false));
    }

    @Test
    public void maskColumns_dotsInPlaceOfEveryWord() {
        assertArrayEquals(new String[] {DOTS + "\n" + DOTS + "\n" + DOTS, DOTS + "\n" + DOTS},
                SecretDisplay.maskColumns(5, true));
        assertArrayEquals(new String[] {DOTS + "\n" + DOTS, ""},
                SecretDisplay.maskColumns(2, false));
    }

    @Test
    public void hiddenAndShown_haveTheSameLinesInEveryColumn() {
        // So that the card keeps its size when the secret is shown or hidden.
        final String[] phrases = {"a b c d e", "a b c d e f", "a b c d e f g",
                "a b c d e f g h"};
        for (String phrase : phrases) {
            final int count = phrase.split(" ").length;
            for (boolean twoColumns : new boolean[] {true, false}) {
                final String[] shown = words(phrase, twoColumns);
                final String[] hidden = SecretDisplay.maskColumns(count, twoColumns);
                final String[] numbers = SecretDisplay.numberColumns(count, twoColumns);
                for (int column = 0; column < 2; column++) {
                    assertEquals(lineCount(shown[column]), lineCount(hidden[column]));
                    assertEquals(lineCount(shown[column]), lineCount(numbers[column]));
                }
            }
        }
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
    public void maskedDigits_sameShapeAsTheDigits() {
        final String dots = SecretDisplay.maskedDigits(20);

        assertEquals("•••• •••• •••• "
                + "•••• ••••", dots);
        assertEquals(grouped("12345678901234567890").length(), dots.length());
        assertEquals("", SecretDisplay.maskedDigits(0));
    }

    @Test
    public void wipe_overwritesWithZeros() {
        final char[] chars = "abacus zoom".toCharArray();

        SecretDisplay.wipe(chars);

        assertArrayEquals(new char[11], chars);
    }
}

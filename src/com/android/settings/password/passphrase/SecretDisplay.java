/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.util.Arrays;

/**
 * Lays a generated passphrase or PIN out for reading and copying to paper: the words in one or
 * two columns with their numbers beside them, a PIN in groups of digits. Hidden, the same
 * layout is filled with dots, so that showing the secret moves nothing on the screen.
 *
 * <p>The numbers and the dots are not secret and are plain strings. The words and digits are
 * char arrays only: the caller wipes them when they are no longer shown.
 */
public final class SecretDisplay {

    /** Digits of a PIN are shown in groups of this size. */
    public static final int DIGIT_GROUP = 4;

    /** What stands for a hidden word, and for a hidden digit. */
    static final String WORD_MASK = "••••••";
    static final char DIGIT_MASK = '•';

    private SecretDisplay() {}

    /**
     * Number of words in the first column: all of them in one column, the first half rounded
     * up in two.
     */
    static int firstColumnWords(int words, boolean twoColumns) {
        return twoColumns ? (words + 1) / 2 : words;
    }

    /**
     * The words of {@code phrase}, one per line, for the first and the second column. The
     * words of {@code phrase} are separated by single spaces. With one column the second
     * array is empty.
     *
     * @return two arrays. The caller wipes both
     */
    public static char[][] wordColumns(char[] phrase, boolean twoColumns) {
        int words = 1;
        for (char c : phrase) {
            if (c == PassphraseGenerator.SEPARATOR) {
                words++;
            }
        }
        final int firstWords = firstColumnWords(words, twoColumns);
        // Where the first column ends: at the separator after its last word.
        int split = phrase.length;
        int seen = 0;
        for (int i = 0; i < phrase.length; i++) {
            if (phrase[i] == PassphraseGenerator.SEPARATOR && ++seen == firstWords) {
                split = i;
                break;
            }
        }
        final char[] first = Arrays.copyOf(phrase, split);
        final char[] second = split < phrase.length
                ? Arrays.copyOfRange(phrase, split + 1, phrase.length) : new char[0];
        for (char[] column : new char[][] {first, second}) {
            for (int i = 0; i < column.length; i++) {
                if (column[i] == PassphraseGenerator.SEPARATOR) {
                    column[i] = '\n';
                }
            }
        }
        return new char[][] {first, second};
    }

    /**
     * The numbers that stand beside the words, one per line, for the first and the second
     * column: "1", "2", "3" and "4", "5", "6" for six words in two columns.
     */
    public static String[] numberColumns(int words, boolean twoColumns) {
        final int firstWords = firstColumnWords(words, twoColumns);
        return new String[] {numberLines(1, firstWords), numberLines(firstWords + 1, words)};
    }

    /** Dots in place of the words, line for line as {@link #wordColumns}. */
    public static String[] maskColumns(int words, boolean twoColumns) {
        final int firstWords = firstColumnWords(words, twoColumns);
        return new String[] {maskLines(firstWords), maskLines(words - firstWords)};
    }

    private static String numberLines(int from, int to) {
        final StringBuilder text = new StringBuilder();
        for (int i = from; i <= to; i++) {
            if (i > from) {
                text.append('\n');
            }
            text.append(i);
        }
        return text.toString();
    }

    private static String maskLines(int count) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(WORD_MASK);
        }
        return text.toString();
    }

    /** The digits in groups of {@link #DIGIT_GROUP}, separated by spaces: "1234 5678 9012". */
    public static char[] groupedDigits(char[] digits) {
        return grouped(digits, DIGIT_GROUP);
    }

    /** Dots in place of {@code digits} digits, grouped as {@link #groupedDigits}. */
    public static String maskedDigits(int digits) {
        final char[] dots = new char[digits];
        Arrays.fill(dots, DIGIT_MASK);
        return new String(groupedDigits(dots));
    }

    /**
     * The letters of a short-list credential in groups of three, one for each word, separated by
     * spaces for reading: "unc aba fox gem oat pry". The spaces are not typed.
     */
    public static char[] groupedLetters(char[] letters) {
        return grouped(letters, ShortList.PREFIX_LENGTH);
    }

    /** Dots in place of a short-list credential, grouped as {@link #groupedLetters}. */
    public static String maskedLetters() {
        final char[] dots = new char[ShortList.LENGTH];
        Arrays.fill(dots, DIGIT_MASK);
        return new String(groupedLetters(dots));
    }

    /**
     * The words a short-list credential stands for, in order: the word of {@code list} that
     * starts with each group of three letters.
     *
     * @param list the list the credential was drawn from
     * @return one new array for each word. The caller wipes them
     * @throws IllegalArgumentException if a group starts no word of the list
     */
    public static char[][] shortListWords(char[] letters, WordList list) {
        final int length = ShortList.PREFIX_LENGTH;
        final char[][] words = new char[letters.length / length][];
        for (int i = 0; i < words.length; i++) {
            final int index = list.indexOfPrefix(letters, i * length, length);
            if (index < 0) {
                for (int j = 0; j < i; j++) {
                    wipe(words[j]);
                }
                throw new IllegalArgumentException("not a credential of this list");
            }
            words[i] = list.word(index).toCharArray();
        }
        return words;
    }

    private static char[] grouped(char[] chars, int group) {
        if (chars.length == 0) {
            return new char[0];
        }
        final char[] result = new char[chars.length + (chars.length - 1) / group];
        int length = 0;
        for (int i = 0; i < chars.length; i++) {
            if (i > 0 && i % group == 0) {
                result[length++] = ' ';
            }
            result[length++] = chars[i];
        }
        return result;
    }

    /** Overwrites {@code chars} with zeros. */
    public static void wipe(char[] chars) {
        Arrays.fill(chars, '\0');
    }
}

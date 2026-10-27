/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * The short-list passphrase: six words from EFF's short word list 2, of which only the first
 * {@link #PREFIX_LENGTH} letters of each are the credential, {@link #LENGTH} lowercase letters
 * with nothing between them. The lock settings service generates it
 * ({@code LockPatternUtils.generateShortListPassword}), and counts it as strong only because it
 * did. This app shows the whole words, only to help remember the letters.
 *
 * <p>The words are the 1,295 of the list made of a to z, all but yo-yo. No two of them start
 * with the same three letters, so the letters name the words one to one:
 * {@code 6 * log2(1,295)} is 62.03 bits. Estimates count {@link #ESTIMATE_BITS_PER_WORD}.
 */
public final class ShortList {

    /** Number of words. */
    public static final int WORDS = 6;

    /** Letters of each word that are typed. */
    public static final int PREFIX_LENGTH = WordList.EFF_SHORT_PREFIX_LENGTH;

    /** Number of letters of the credential. */
    public static final int LENGTH = WORDS * PREFIX_LENGTH;

    /** Bits a word that time estimates count: log2(1,295) is 10.34, rounded down. */
    public static final double ESTIMATE_BITS_PER_WORD = 10.3;

    /** Bits of the whole credential that time estimates count. */
    public static final double ESTIMATE_BITS = WORDS * ESTIMATE_BITS_PER_WORD;

    private ShortList() {}

    /**
     * The words the service draws from: those of {@code list} made of a to z.
     *
     * @param list EFF's short word list 2
     * @throws IllegalArgumentException if two of them start with the same letters
     */
    public static WordList drawnWords(WordList list) {
        final WordList drawn = list.lettersOnly();
        if (!drawn.hasUniqueSortedPrefixes(PREFIX_LENGTH)) {
            throw new IllegalArgumentException("words do not have unique sorted prefixes");
        }
        return drawn;
    }

    /** Whether every group of letters starts a word of {@code drawn}: a credential to show. */
    public static boolean isShownBy(char[] letters, WordList drawn) {
        if (letters.length != LENGTH) {
            return false;
        }
        for (int i = 0; i < LENGTH; i += PREFIX_LENGTH) {
            if (drawn.indexOfPrefix(letters, i, PREFIX_LENGTH) < 0) {
                return false;
            }
        }
        return true;
    }
}

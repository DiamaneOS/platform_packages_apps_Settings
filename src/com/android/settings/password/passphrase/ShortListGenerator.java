/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

/**
 * Makes the short-list passphrase: {@link #WORDS} words from EFF's short word list 2, each drawn
 * independently and uniformly. Only the first {@link #PREFIX_LENGTH} letters of each word are
 * the credential, {@link #LENGTH} lowercase letters with nothing between them. The whole words
 * are only shown to help remember the letters.
 *
 * <p><b>Which words.</b> The 1,295 words made of a to z, all but yo-yo. No two of them start
 * with the same three letters, so the letters name the words one to one:
 * {@code 6 * log2(1,295)} is 62.03 bits. Estimates count {@link #ESTIMATE_BITS_PER_WORD}.
 *
 * <p><b>The floor.</b> The lock settings service counts such a password as strong if it has at
 * least {@link PassphraseFloor#MIN_DISTINCT_CHARS} different letters. A draw with fewer is
 * thrown away and all of its words are drawn again, as {@link PassphraseGenerator} does. That
 * happens to about one draw in 340 million and costs 4.3e-9 bits.
 *
 * <p><b>Secret handling</b> is that of {@link PassphraseGenerator}: the letters are built in char
 * arrays, which are overwritten before {@link #generate} returns, except the one inside the
 * returned {@link Passphrase}.
 */
public final class ShortListGenerator {

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

    // See PassphraseGenerator: only reached with a list that cannot meet the floor.
    private static final int MAX_DRAWS = 100;

    private final WordList mList;
    private final Random mRandom;

    /**
     * @param list EFF's short word list 2. Its words with a character other than a to z are
     *     never drawn
     * @param random the source of randomness. {@code new SecureRandom()} is the right one
     * @throws IllegalArgumentException if two drawn words start with the same letters
     */
    public ShortListGenerator(WordList list, SecureRandom random) {
        this(list, (Random) random);
    }

    /** For tests, which need a source that repeats. Never for a credential that is used. */
    ShortListGenerator(WordList list, Random random) {
        mList = list.lettersOnly();
        if (!mList.hasUniqueSortedPrefixes(PREFIX_LENGTH)) {
            throw new IllegalArgumentException("words do not have unique sorted prefixes");
        }
        mRandom = random;
    }

    /** The words that are drawn, each equally likely. */
    public WordList drawnWords() {
        return mList;
    }

    /** Returns a new random credential of {@link #LENGTH} letters. The caller closes it. */
    public Passphrase generate() {
        final int[] indices = new int[WORDS];
        final byte[] random = new byte[UniformIndex.bytesNeeded(mList.size())];
        final char[] chars = new char[LENGTH];
        try {
            for (int draw = 0; draw < MAX_DRAWS; draw++) {
                for (int i = 0; i < WORDS; i++) {
                    indices[i] = UniformIndex.next(mRandom, mList.size(), random);
                    mList.word(indices[i]).getChars(0, PREFIX_LENGTH, chars, i * PREFIX_LENGTH);
                }
                if (meetsFloor(chars)) {
                    return new Passphrase(Arrays.copyOf(chars, LENGTH), WORDS);
                }
            }
            throw new IllegalStateException("word list cannot meet the floor");
        } finally {
            Arrays.fill(indices, 0);
            Arrays.fill(random, (byte) 0);
            Arrays.fill(chars, '\0');
        }
    }

    /** Whether {@code letters}, all from a to z, has enough different letters to be strong. */
    static boolean meetsFloor(char[] letters) {
        int seen = 0;
        for (char c : letters) {
            seen |= 1 << (c - 'a');
        }
        return Integer.bitCount(seen) >= PassphraseFloor.MIN_DISTINCT_CHARS;
    }
}

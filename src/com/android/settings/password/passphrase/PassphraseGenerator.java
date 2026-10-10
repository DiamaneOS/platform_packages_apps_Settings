/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

/**
 * Makes random passphrases: {@link #MIN_WORDS} to {@link #MAX_WORDS} words from a
 * {@link WordList}, each drawn independently and uniformly, joined by single spaces.
 *
 * <p><b>Why a space.</b> It is one tap on the largest key of the lock screen keyboard, where a
 * hyphen is on the symbols page. Four words of the EFF list contain a hyphen themselves, so a
 * hyphen could not mark where words end. The separators also count toward the length floor.
 *
 * <p><b>The floor.</b> A phrase that does not meet {@link PassphraseFloor} is thrown away and all
 * of its words are drawn again. Only a whole new draw keeps every acceptable phrase equally
 * likely; replacing one word or adding one would favour some phrases. With the EFF list this
 * happens to about one 5-word phrase in 7.7 billion (five words of three letters make 19
 * characters) and costs 1.9e-10 bits of entropy.
 *
 * <p><b>Secret handling.</b> The phrase is built in char arrays. Every array that held a part of
 * it is overwritten before {@link #generate} returns, except the one inside the returned
 * {@link Passphrase}, which the caller closes. Nothing is logged and no String of the phrase is
 * made. This narrows where the phrase is in memory; it cannot rule out copies the runtime makes
 * when it moves objects, or state inside the random number generator.
 *
 * <p><b>What a screen that shows the phrase must do</b> to keep that true:
 * <ul>
 *   <li>Keep the {@link Passphrase} in memory only: not in saved instance state, a Bundle, an
 *       Intent, a Parcel, a preference or a file. If the process dies, generate a new one.
 *   <li>Close it when the phrase is saved, replaced by a new one, or the flow is left.
 *   <li>Never make a String of it: no {@code new String}, {@code String.valueOf}, concatenation
 *       or string formatting. Show it with {@code TextView.setText(char[], int, int)} and hand
 *       it to the lock settings with {@code CharBuffer.wrap(chars)}.
 *   <li>Never log it or anything derived from it. Do not log its length either.
 *   <li>No clipboard: the text is not selectable, has no copy or share action and no long-press
 *       menu.
 *   <li>Set {@code FLAG_SECURE} on the window for as long as the phrase is shown or typed back:
 *       no screenshots, no screen recording or casting, and a blank picture in recents.
 *   <li>Turn off state saving for every view that shows or takes the phrase
 *       ({@code setSaveEnabled(false)}), and keep those views out of autofill, content capture
 *       and assist data.
 *   <li>Show the phrase only after the user asks for it, and hide it again when the screen is
 *       left or the display turns off.
 *   <li>Mark the views as sensitive for accessibility services
 *       ({@code ACCESSIBILITY_DATA_SENSITIVE_YES}).
 * </ul>
 */
public final class PassphraseGenerator {

    /** Fewest words in a generated passphrase. */
    public static final int MIN_WORDS = 5;

    /** Most words in a generated passphrase. */
    public static final int MAX_WORDS = 8;

    /** Number of words when the user has not chosen one. */
    public static final int DEFAULT_WORDS = 6;

    /** The character between two words. */
    public static final char SEPARATOR = ' ';

    // A phrase that fails the floor is drawn again. With the EFF list the chance of a single
    // failure is below 2e-10, so this limit is only reached with a list that cannot meet the
    // floor, where giving up beats looping forever.
    private static final int MAX_DRAWS = 100;

    private final WordList mList;
    private final Random mRandom;

    /**
     * @param list the words to draw from
     * @param random the source of randomness; {@code new SecureRandom()} is the right one
     */
    public PassphraseGenerator(WordList list, SecureRandom random) {
        this(list, (Random) random);
    }

    /** For tests, which need a source that repeats. Never for a phrase that is used. */
    PassphraseGenerator(WordList list, Random random) {
        mList = list;
        mRandom = random;
    }

    /**
     * Returns a new random passphrase. The caller owns it and closes it.
     *
     * @param words number of words, from {@link #MIN_WORDS} to {@link #MAX_WORDS}
     */
    public Passphrase generate(int words) {
        checkWords(words);
        return generate(words, new Scratch(mList, words));
    }

    /**
     * {@link #generate(int)} with the working memory passed in, so that a test can see that it
     * is wiped. {@code scratch} must have been made for this list and word count.
     */
    Passphrase generate(int words, Scratch scratch) {
        checkWords(words);
        try {
            for (int draw = 0; draw < MAX_DRAWS; draw++) {
                for (int i = 0; i < words; i++) {
                    scratch.indices[i] = UniformIndex.next(mRandom, mList.size(), scratch.random);
                }
                int length = 0;
                for (int i = 0; i < words; i++) {
                    if (i > 0) {
                        scratch.chars[length++] = SEPARATOR;
                    }
                    length = mList.copyWord(scratch.indices[i], scratch.chars, length);
                }
                if (PassphraseFloor.isMet(scratch.chars, length)) {
                    return new Passphrase(Arrays.copyOf(scratch.chars, length), words);
                }
                // Not acceptable: every word is drawn again, see the class comment.
            }
            throw new IllegalStateException("word list cannot meet the passphrase floor");
        } finally {
            scratch.wipe();
        }
    }

    private static void checkWords(int words) {
        if (words < MIN_WORDS || words > MAX_WORDS) {
            throw new IllegalArgumentException(
                    "words must be from " + MIN_WORDS + " to " + MAX_WORDS);
        }
    }

    /** Working memory of one {@link #generate} call. Everything in it is secret until wiped. */
    static final class Scratch {
        final int[] indices;
        final byte[] random;
        final char[] chars;

        Scratch(WordList list, int words) {
            indices = new int[words];
            random = new byte[UniformIndex.bytesNeeded(list.size())];
            chars = new char[words * list.longestWord() + words - 1];
        }

        void wipe() {
            Arrays.fill(indices, 0);
            Arrays.fill(random, (byte) 0);
            Arrays.fill(chars, '\0');
        }
    }
}

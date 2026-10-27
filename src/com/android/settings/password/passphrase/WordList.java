/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * The words a generated passphrase is made of.
 *
 * <p>The list that ships is the EFF large word list: 7,776 words, one for each roll of five dice.
 * It is stored without the dice numbers, one word per line, and is only used when its SHA-256
 * matches {@link #EFF_LARGE_SHA256}. EFF's short word list 2 ships the same way, for
 * {@link ShortList}: 1,296 words, one for each roll of four dice, pinned by
 * {@link #EFF_SHORT_SHA256}.
 *
 * <p>The words are public, so they are held as strings. A generated phrase is not: it is built
 * from them in a char array, see {@link PassphraseGenerator}.
 *
 * <p>No word is the start of another word, and no word contains a space. 7,772 words are
 * lowercase a to z only. Four contain a hyphen (drop-down, felt-tip, t-shirt, yo-yo). The file
 * stays the published list. Passphrases are made from {@link #lettersOnly()}.
 */
public final class WordList {

    /** Where the EFF large word list is in the app's assets. */
    public static final String EFF_LARGE_ASSET = "passphrase/eff_large_wordlist.txt";

    /** Number of words in the EFF large word list: 6^5. */
    public static final int EFF_LARGE_SIZE = 7776;

    /** Number of words of the EFF large word list that are made of a to z only. */
    public static final int EFF_LARGE_LETTERS_ONLY_SIZE = 7772;

    /**
     * SHA-256 of the list as shipped. The file EFF publishes, with a dice number and a tab in
     * front of each word, has the SHA-256
     * addd35536511597a02fa0a9ff1e5284677b8883b83e986e43f15a3db996b903e.
     */
    static final String EFF_LARGE_SHA256 =
            "6d557f0693958fb5e650b68b5bee585eb82cf4da32965505c789e924743bc522";

    /** Where EFF's short word list 2 is in the app's assets. */
    public static final String EFF_SHORT_ASSET = "passphrase/eff_short_wordlist_2_0.txt";

    /** Number of words in EFF's short word list 2: 6^4. */
    public static final int EFF_SHORT_SIZE = 1296;

    /** Number of words of EFF's short word list 2 that are made of a to z only: all but yo-yo. */
    public static final int EFF_SHORT_LETTERS_ONLY_SIZE = 1295;

    /** Every word of the short list has a different start of this many letters. */
    public static final int EFF_SHORT_PREFIX_LENGTH = 3;

    /**
     * SHA-256 of the short list as shipped. The file EFF publishes, with a dice number and a tab
     * in front of each word, has the SHA-256
     * 22b45c52e0bd0bbf03aa522240b111eb4c7c0c1d86c4e518e1be2a7eb2a625e4.
     */
    static final String EFF_SHORT_SHA256 =
            "7aa57a4d3ecf6581729992bad9575bacdebf7c28378af2aec6a50f11aec326f5";

    // The shipped list is 62,144 bytes. Nothing near this size is read into memory unchecked.
    private static final int MAX_FILE_BYTES = 1 << 17;

    private final String[] mWords;
    private final int mLongestWord;

    private WordList(String[] words) {
        if (words.length == 0) {
            throw new IllegalArgumentException("empty word list");
        }
        final Set<String> seen = new HashSet<>();
        int longest = 0;
        for (String word : words) {
            if (!isWord(word)) {
                throw new IllegalArgumentException("not a word of a-z and inner hyphens");
            }
            if (!seen.add(word)) {
                throw new IllegalArgumentException("word list has a word twice");
            }
            longest = Math.max(longest, word.length());
        }
        mWords = words.clone();
        mLongestWord = longest;
    }

    /**
     * Reads the EFF large word list and checks it against the pinned hash.
     *
     * @param in the list as shipped in {@link #EFF_LARGE_ASSET}. The caller closes it
     * @throws IOException if reading fails or the content is not the pinned list
     */
    public static WordList loadEffLarge(InputStream in) throws IOException {
        return load(in, EFF_LARGE_SHA256, EFF_LARGE_SIZE);
    }

    /**
     * Reads EFF's short word list 2 and checks it against the pinned hash.
     *
     * @param in the list as shipped in {@link #EFF_SHORT_ASSET}. The caller closes it
     * @throws IOException if reading fails or the content is not the pinned list
     */
    public static WordList loadEffShort(InputStream in) throws IOException {
        return load(in, EFF_SHORT_SHA256, EFF_SHORT_SIZE);
    }

    private static WordList load(InputStream in, String pinnedSha256, int size)
            throws IOException {
        final byte[] bytes = in.readNBytes(MAX_FILE_BYTES + 1);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("word list is too large");
        }
        final byte[] expected = HexFormat.of().parseHex(pinnedSha256);
        if (!MessageDigest.isEqual(expected, sha256(bytes))) {
            throw new IOException("word list does not match its pinned hash");
        }
        final List<String> words = new ArrayList<>(size);
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '\n') {
                words.add(new String(bytes, start, i - start, StandardCharsets.US_ASCII));
                start = i + 1;
            }
        }
        if (start != bytes.length || words.size() != size) {
            throw new IOException("word list is not " + size + " lines");
        }
        return new WordList(words.toArray(new String[0]));
    }

    /**
     * Makes a list from the given words, for tests and for counting on small lists.
     *
     * @throws IllegalArgumentException if a word is repeated or is not made of a to z with
     *     hyphens only between letters
     */
    static WordList of(String... words) {
        return new WordList(words);
    }

    /**
     * The words of this list that have no character other than a to z, in the same order. Of
     * the EFF large list that is all but drop-down, felt-tip, t-shirt and yo-yo.
     *
     * @throws IllegalArgumentException if no word is left
     */
    public WordList lettersOnly() {
        final List<String> words = new ArrayList<>(mWords.length);
        for (String word : mWords) {
            if (isLettersOnly(word)) {
                words.add(word);
            }
        }
        return new WordList(words.toArray(new String[0]));
    }

    /** Number of words. */
    public int size() {
        return mWords.length;
    }

    /** The word at {@code index}, from 0 to {@link #size()} - 1. */
    public String word(int index) {
        return mWords[index];
    }

    /** Length of the longest word. */
    public int longestWord() {
        return mLongestWord;
    }

    /**
     * Whether the words are in order and no two of them start with the same {@code length}
     * letters, so that those letters name one word. Every word must be that long.
     */
    public boolean hasUniqueSortedPrefixes(int length) {
        for (int i = 0; i < mWords.length; i++) {
            if (mWords[i].length() < length
                    || i > 0 && mWords[i - 1].regionMatches(0, mWords[i], 0, length)
                    || i > 0 && mWords[i - 1].compareTo(mWords[i]) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * The index of the word that starts with {@code chars[offset]} to
     * {@code chars[offset + length - 1]}, or -1 if none does. Only for a list with
     * {@link #hasUniqueSortedPrefixes}. The characters may be secret: no String is made of them.
     */
    int indexOfPrefix(char[] chars, int offset, int length) {
        int low = 0;
        int high = mWords.length - 1;
        while (low <= high) {
            final int middle = (low + high) >>> 1;
            final String word = mWords[middle];
            int order = 0;
            for (int i = 0; i < length && order == 0; i++) {
                order = chars[offset + i] - word.charAt(i);
            }
            if (order == 0) {
                return middle;
            } else if (order < 0) {
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        return -1;
    }

    /**
     * Copies the word at {@code index} into {@code dest} starting at {@code offset}.
     *
     * @return the offset after the copied word
     */
    int copyWord(int index, char[] dest, int offset) {
        final String word = mWords[index];
        word.getChars(0, word.length(), dest, offset);
        return offset + word.length();
    }

    private static boolean isWord(String word) {
        final int length = word.length();
        if (length == 0 || !isLetter(word.charAt(0)) || !isLetter(word.charAt(length - 1))) {
            return false;
        }
        for (int i = 1; i < length - 1; i++) {
            final char c = word.charAt(i);
            if (!isLetter(c) && c != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLettersOnly(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (!isLetter(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLetter(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

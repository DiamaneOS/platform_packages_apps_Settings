/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * The least a passphrase must be to count as strong: at least {@link #MIN_LENGTH} characters, not
 * digits only, and at least {@link #MIN_DISTINCT_CHARS} different characters.
 *
 * <p>The lock settings service applies the same floor to every password it is asked to set. The
 * numbers here must not be looser than the service's, or a phrase the user already confirmed
 * could be refused when it is saved.
 *
 * <p>The floor is a shape check. It says nothing about how hard a self-chosen phrase is to guess.
 */
public final class PassphraseFloor {

    /** Shortest strong passphrase, in characters, separators included. */
    public static final int MIN_LENGTH = 20;

    /** Fewest different characters in a strong passphrase, the separator included. */
    public static final int MIN_DISTINCT_CHARS = 5;

    // Stands for "enough different characters" where a set of characters is expected.
    private static final int ENOUGH = -1;

    private PassphraseFloor() {}

    /**
     * Whether the first {@code length} characters of {@code phrase} meet the floor. Only ASCII
     * phrases can: the generator makes no other, and a character outside ASCII fails.
     */
    public static boolean isMet(char[] phrase, int length) {
        if (length < MIN_LENGTH) {
            return false;
        }
        // Which of the 128 ASCII characters occur, as two bit sets. No array is left behind.
        long low = 0;
        long high = 0;
        boolean digitsOnly = true;
        for (int i = 0; i < length; i++) {
            final char c = phrase[i];
            if (c >= 128) {
                return false;
            }
            if (c < '0' || c > '9') {
                digitsOnly = false;
            }
            if (c < 64) {
                low |= 1L << c;
            } else {
                high |= 1L << (c - 64);
            }
        }
        return !digitsOnly && Long.bitCount(low) + Long.bitCount(high) >= MIN_DISTINCT_CHARS;
    }

    /**
     * Counts, exactly, the phrases of {@code words} words from {@code list} that do not meet the
     * floor when joined with one separator character between words. The count is over ordered
     * picks with repeats, so it is out of {@code list.size()^words}.
     *
     * <p>The generator draws such a phrase again, so this is what its entropy is short of the
     * plain {@code words * log2(list.size())}.
     */
    public static BigInteger countRejected(WordList list, int words) {
        if (words < 1) {
            throw new IllegalArgumentException("words must be at least 1");
        }
        // A phrase fails on length when its words have fewer letters than this in total.
        final int lettersNeeded = Math.max(0, MIN_LENGTH - (words - 1));
        // It fails on variety when its words have fewer different characters than this. The
        // separator is one more, and it is in no word.
        final int distinctNeeded = MIN_DISTINCT_CHARS - (words > 1 ? 1 : 0);

        // Words that act alike: same set of characters (or "enough"), same length up to the need.
        final Map<Long, BigInteger> kinds = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            final String word = list.word(i);
            final int chars = cap(charSet(word), distinctNeeded);
            final int letters = Math.min(word.length(), lettersNeeded);
            kinds.merge(key(chars, letters), BigInteger.ONE, BigInteger::add);
        }

        // Number of ways to reach each (characters so far, letters so far), word by word.
        Map<Long, BigInteger> ways = new HashMap<>();
        ways.put(key(0, 0), BigInteger.ONE);
        for (int w = 0; w < words; w++) {
            final Map<Long, BigInteger> next = new HashMap<>();
            for (Map.Entry<Long, BigInteger> state : ways.entrySet()) {
                for (Map.Entry<Long, BigInteger> kind : kinds.entrySet()) {
                    final int stateChars = chars(state.getKey());
                    final int kindChars = chars(kind.getKey());
                    final int chars = stateChars == ENOUGH || kindChars == ENOUGH
                            ? ENOUGH : cap(stateChars | kindChars, distinctNeeded);
                    final int letters = Math.min(
                            letters(state.getKey()) + letters(kind.getKey()), lettersNeeded);
                    next.merge(key(chars, letters),
                            state.getValue().multiply(kind.getValue()), BigInteger::add);
                }
            }
            ways = next;
        }

        BigInteger rejected = BigInteger.ZERO;
        for (Map.Entry<Long, BigInteger> state : ways.entrySet()) {
            if (chars(state.getKey()) != ENOUGH || letters(state.getKey()) < lettersNeeded) {
                rejected = rejected.add(state.getValue());
            }
        }
        return rejected;
    }

    // The characters of a word as a bit set: a to z are bits 0 to 25, the hyphen is bit 26.
    private static int charSet(String word) {
        int set = 0;
        for (int i = 0; i < word.length(); i++) {
            final char c = word.charAt(i);
            set |= 1 << (c == '-' ? 26 : c - 'a');
        }
        return set;
    }

    private static int cap(int charSet, int distinctNeeded) {
        return Integer.bitCount(charSet) >= distinctNeeded ? ENOUGH : charSet;
    }

    private static long key(int chars, int letters) {
        return ((long) chars << 32) | letters;
    }

    private static int chars(long key) {
        return (int) (key >> 32);
    }

    private static int letters(long key) {
        return (int) key;
    }
}

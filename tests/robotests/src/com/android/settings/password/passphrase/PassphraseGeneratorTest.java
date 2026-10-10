/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.PassphraseGenerator.Scratch;
import com.android.settings.password.passphrase.PassphraseTestUtils.ScriptedRandom;
import com.android.settings.password.passphrase.PassphraseTestUtils.SeededRandom;

import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class PassphraseGeneratorTest {

    private static final int SEED = 20261010;

    private WordList mEff;
    private Map<String, Integer> mEffIndex;

    @Before
    public void setUp() throws Exception {
        mEff = PassphraseTestUtils.effLarge();
        mEffIndex = indexOf(mEff);
    }

    private static Map<String, Integer> indexOf(WordList list) {
        final Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            index.put(list.word(i), i);
        }
        return index;
    }

    private int eff(String word) {
        return mEffIndex.get(word);
    }

    /** The indices of the words of a phrase. Fails if it is not words joined by single spaces. */
    private static int[] wordsOf(Passphrase phrase, Map<String, Integer> index) {
        // The test may make a String of a phrase. The app may not.
        final String[] words = new String(phrase.chars()).split(" ", -1);
        assertEquals(phrase.wordCount(), words.length);
        final int[] indices = new int[words.length];
        for (int i = 0; i < words.length; i++) {
            assertTrue("not a word of the list: '" + words[i] + "'", index.containsKey(words[i]));
            indices[i] = index.get(words[i]);
        }
        return indices;
    }

    @Test
    public void wordCounts_areFiveToEightAndSixByDefault() {
        assertEquals(5, PassphraseGenerator.MIN_WORDS);
        assertEquals(8, PassphraseGenerator.MAX_WORDS);
        assertEquals(6, PassphraseGenerator.DEFAULT_WORDS);
        assertEquals(' ', PassphraseGenerator.SEPARATOR);
    }

    @Test
    public void generate_refusesOtherWordCounts() {
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SeededRandom(SEED));

        for (int words : new int[] {-1, 0, 1, 4, 9, 100}) {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(words));
            assertThrows(IllegalArgumentException.class, () -> generator.entropyBits(words));
            assertThrows(IllegalArgumentException.class,
                    () -> generator.rejectionLossBits(words));
        }
    }

    @Test
    public void generate_isListWordsJoinedBySingleSpaces_andMeetsTheFloor() {
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SeededRandom(SEED));

        for (int words = 5; words <= 8; words++) {
            for (int i = 0; i < 20_000; i++) {
                try (Passphrase phrase = generator.generate(words)) {
                    assertEquals(words, phrase.wordCount());
                    assertEquals(phrase.chars().length, phrase.length());
                    assertTrue(PassphraseFloor.isMet(phrase.chars(), phrase.length()));
                    assertTrue(phrase.length() >= 20);
                    int letters = 0;
                    for (int index : wordsOf(phrase, mEffIndex)) {
                        letters += mEff.word(index).length();
                    }
                    assertEquals(letters + words - 1, phrase.length());
                }
            }
        }
    }

    @Test
    public void generate_withSecureRandom_works() {
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SecureRandom());

        for (int words = 5; words <= 8; words++) {
            try (Passphrase first = generator.generate(words);
                    Passphrase second = generator.generate(words)) {
                wordsOf(first, mEffIndex);
                wordsOf(second, mEffIndex);
                assertTrue(PassphraseFloor.isMet(first.chars(), first.length()));
                // Two phrases being equal has a chance of 2^-64 or less.
                assertNotEquals(new String(first.chars()), new String(second.chars()));
            }
        }
    }

    @Test
    public void generate_sameSeed_samePhrase() {
        final PassphraseGenerator one = new PassphraseGenerator(mEff, new SeededRandom(SEED));
        final PassphraseGenerator two = new PassphraseGenerator(mEff, new SeededRandom(SEED));

        try (Passphrase first = one.generate(6); Passphrase second = two.generate(6)) {
            assertArrayEquals(first.chars(), second.chars());
        }
    }

    @Test
    public void generate_everyWordEquallyLikelyInEveryPosition() {
        // 100 phrases expected per word and position.
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SeededRandom(SEED));
        final int words = PassphraseGenerator.DEFAULT_WORDS;
        final long[][] counts = new long[words][mEff.size()];
        for (int i = 0; i < mEff.size() * 100; i++) {
            try (Passphrase phrase = generator.generate(words)) {
                final int[] indices = wordsOf(phrase, mEffIndex);
                for (int position = 0; position < words; position++) {
                    counts[position][indices[position]]++;
                }
            }
        }

        for (int position = 0; position < words; position++) {
            PassphraseTestUtils.assertUniform("word " + position, counts[position]);
        }
    }

    @Test
    public void generate_fiveShortWords_drawsAllFiveAgain() {
        // Five words of three letters and four spaces are 19 characters: under the floor.
        final ScriptedRandom random = new ScriptedRandom(
                eff("aim"), eff("art"), eff("zap"), eff("zen"), eff("zit"),
                eff("abacus"), eff("zoom"), eff("abdomen"), eff("yo-yo"), eff("zoology"));
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, random);

        try (Passphrase phrase = generator.generate(5)) {
            // Not one word of the first draw is kept.
            assertArrayEquals("abacus zoom abdomen yo-yo zoology".toCharArray(), phrase.chars());
        }
        assertTrue(random.isUsedUp());
    }

    @Test
    public void generate_twentyCharacters_isKept() {
        // Four words of three letters, one of four, four spaces: exactly 20.
        final ScriptedRandom random = new ScriptedRandom(
                eff("aim"), eff("art"), eff("zap"), eff("zen"), eff("zoom"));
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, random);

        try (Passphrase phrase = generator.generate(5)) {
            assertArrayEquals("aim art zap zen zoom".toCharArray(), phrase.chars());
            assertEquals(20, phrase.length());
        }
    }

    @Test
    public void generate_tooFewDifferentCharacters_drawsAgain() {
        // Long enough, but only m, a and the space.
        final ScriptedRandom random = new ScriptedRandom(
                eff("mama"), eff("mama"), eff("mama"), eff("mama"), eff("mama"), eff("mama"),
                eff("mama"), eff("mama"), eff("mama"), eff("mama"), eff("mama"), eff("zoom"));
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, random);

        try (Passphrase phrase = generator.generate(6)) {
            assertArrayEquals("mama mama mama mama mama zoom".toCharArray(), phrase.chars());
        }
    }

    @Test
    public void generate_indexAtOrAboveTheListSize_isThrownAway() {
        // 7,776 to 8,191 are not words. They are skipped one by one, not mapped onto words.
        final ScriptedRandom random = new ScriptedRandom(
                7776, eff("abacus"), 8191, eff("zoom"), eff("abdomen"), 8000, 7999,
                eff("zoology"), eff("aim"));
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, random);

        try (Passphrase phrase = generator.generate(5)) {
            assertArrayEquals("abacus zoom abdomen zoology aim".toCharArray(), phrase.chars());
        }
    }

    @Test
    public void generate_smallList_everyAcceptablePhraseEquallyLikely_noOtherReturned() {
        // Six words of three letters and two of six. Of the 8^5 = 32,768 phrases of five words,
        // the 6^5 = 7,776 made of short words only are under 20 characters.
        final WordList list =
                WordList.of("ace", "bid", "fog", "hum", "jar", "elk", "window", "planet");
        final Map<String, Integer> index = indexOf(list);
        assertEquals(BigInteger.valueOf(7776), PassphraseFloor.countRejected(list, 5));
        final PassphraseGenerator generator = new PassphraseGenerator(list, new SeededRandom(SEED));

        // 100 phrases expected for each of the 24,992 acceptable ones.
        final long[] counts = new long[32768];
        for (int i = 0; i < 24992 * 100; i++) {
            try (Passphrase phrase = generator.generate(5)) {
                int number = 0;
                for (int word : wordsOf(phrase, index)) {
                    number = number * 8 + word;
                }
                counts[number]++;
            }
        }

        final long[] acceptable = new long[24992];
        int next = 0;
        for (int number = 0; number < counts.length; number++) {
            boolean shortWordsOnly = true;
            for (int rest = number, i = 0; i < 5; i++, rest /= 8) {
                shortWordsOnly &= rest % 8 < 6;
            }
            if (shortWordsOnly) {
                assertEquals("a phrase under the floor was returned", 0, counts[number]);
            } else {
                acceptable[next++] = counts[number];
            }
        }
        assertEquals(acceptable.length, next);
        PassphraseTestUtils.assertUniform("acceptable phrases", acceptable);
    }

    @Test
    public void generate_listThatCannotMeetTheFloor_givesUp() {
        final WordList list = WordList.of("ace", "bid");
        final PassphraseGenerator generator = new PassphraseGenerator(list, new SeededRandom(SEED));
        final Scratch scratch = new Scratch(list, 5);

        assertThrows(IllegalStateException.class, () -> generator.generate(5, scratch));
        assertWiped(scratch);
    }

    /** Records whether the working memory ever held something while a phrase was made. */
    private static final class WatchingRandom extends SeededRandom {
        private final Scratch mScratch;
        boolean mSawIndices;
        boolean mSawChars;

        WatchingRandom(Scratch scratch) {
            super(SEED);
            mScratch = scratch;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            for (int index : mScratch.indices) {
                mSawIndices |= index != 0;
            }
            for (char c : mScratch.chars) {
                mSawChars |= c != 0;
            }
            super.nextBytes(bytes);
        }
    }

    private static void assertWiped(Scratch scratch) {
        for (int index : scratch.indices) {
            assertEquals(0, index);
        }
        for (byte b : scratch.random) {
            assertEquals(0, b);
        }
        for (char c : scratch.chars) {
            assertEquals(0, c);
        }
    }

    @Test
    public void generate_wipesItsWorkingMemory() {
        for (int words = 5; words <= 8; words++) {
            final Scratch scratch = new Scratch(mEff, words);
            assertEquals(words, scratch.indices.length);
            assertEquals(2, scratch.random.length);
            assertEquals(words * 9 + words - 1, scratch.chars.length);
            final PassphraseGenerator generator =
                    new PassphraseGenerator(mEff, new WatchingRandom(scratch));

            try (Passphrase phrase = generator.generate(words, scratch)) {
                assertWiped(scratch);
                // The phrase itself is a separate array and is still there.
                wordsOf(phrase, mEffIndex);
            }
        }
    }

    @Test
    public void generate_wipesAPhraseItThrewAway() {
        // Words of three letters only at first, so that whole phrases are thrown away while the
        // source is watched: the working memory holds them until then.
        final WordList list =
                WordList.of("ace", "bid", "fog", "hum", "jar", "elk", "window", "planet");
        final Scratch scratch = new Scratch(list, 5);
        final WatchingRandom random = new WatchingRandom(scratch);
        final PassphraseGenerator generator = new PassphraseGenerator(list, random);

        for (int i = 0; i < 200; i++) {
            try (Passphrase phrase = generator.generate(5, scratch)) {
                assertWiped(scratch);
                assertTrue(phrase.length() >= 20);
            }
        }
        // The check above means something only if the memory was in use in between.
        assertTrue(random.mSawIndices);
        assertTrue(random.mSawChars);
    }

    @Test
    public void passphrase_close_overwritesThePhrase() {
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SeededRandom(SEED));
        final Passphrase phrase = generator.generate(6);
        final char[] chars = phrase.chars();
        final int length = phrase.length();
        assertFalse(phrase.isClosed());

        phrase.close();

        assertArrayEquals(new char[length], chars);
        assertTrue(phrase.isClosed());
        assertThrows(IllegalStateException.class, phrase::chars);
        assertEquals(6, phrase.wordCount());
        // Closing twice is fine.
        phrase.close();
    }

    @Test
    public void passphrase_chars_isTheArrayNotACopy() {
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SeededRandom(SEED));

        try (Passphrase phrase = generator.generate(6)) {
            // One array to wipe, not a new copy on every call.
            assertTrue(phrase.chars() == phrase.chars());
        }
    }

    @Test
    public void passphrase_toString_doesNotContainThePhrase() {
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SeededRandom(SEED));

        try (Passphrase phrase = generator.generate(6)) {
            // The same text for every phrase of six words, so nothing of the phrase is in it.
            assertEquals("Passphrase(6 words)", phrase.toString());
            assertEquals("Passphrase(6 words)", String.valueOf((Object) phrase));
        }
    }

    @Test
    public void passphrase_cannotBeSerializedOrUsedAsText() {
        assertFalse(Serializable.class.isAssignableFrom(Passphrase.class));
        assertFalse(CharSequence.class.isAssignableFrom(Passphrase.class));
    }

    @Test
    public void entropyBits_effList() {
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SeededRandom(SEED));

        // words * log2(7776), less what the floor costs. Only five words lose anything that
        // shows in a double: 1.88e-10 bits.
        assertEquals(64.62406251784077, generator.entropyBits(5), 1e-12);
        assertEquals(77.54887502163469, generator.entropyBits(6), 1e-12);
        assertEquals(90.47368752524047, generator.entropyBits(7), 1e-12);
        assertEquals(103.39850002884625, generator.entropyBits(8), 1e-12);
    }

    @Test
    public void rejectionLossBits_effList() {
        final PassphraseGenerator generator = new PassphraseGenerator(mEff, new SeededRandom(SEED));

        assertEquals(1.88132119e-10, generator.rejectionLossBits(5), 1e-18);
        assertEquals(7.087072e-20, generator.rejectionLossBits(6), 1e-25);
        assertEquals(2.691074e-23, generator.rejectionLossBits(7), 1e-28);
        assertEquals(1.116253e-26, generator.rejectionLossBits(8), 1e-31);
    }
}

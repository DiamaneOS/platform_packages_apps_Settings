/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.StrengthComparison.Choice;
import com.android.settings.password.passphrase.StrengthComparison.Row;

import org.junit.Test;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class StrengthComparisonTest {

    private static String shown(Row row) {
        return row.estimate.describe((wording, amount, unit) -> wording + " " + amount + " "
                + unit);
    }

    /**
     * The estimate in the words of the screen, in English. The screen takes them from its
     * string resources; this copy of those words is here to check the figures and units.
     */
    private static String inWords(Row row) {
        return row.estimate.describe((wording, amount, unit) -> {
            if (wording == GuessTimeEstimate.Wording.LESS_THAN) {
                return "seconds";
            }
            if (wording == GuessTimeEstimate.Wording.MORE_THAN) {
                return "more than a trillion years";
            }
            final NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
            final String number = format.format(amount);
            switch (unit) {
                case MINUTES:
                    return "about " + number + " minutes";
                case HOURS:
                    return "about " + number + " hours";
                case DAYS:
                    return "about " + number + " days";
                case YEARS:
                    return "about " + number + " years";
                case MILLIONS_OF_YEARS:
                    return "about " + number + " million years";
                default:
                    return "about " + number + " billion years";
            }
        });
    }

    @Test
    public void setupAssumptions_areTheStatedOnes() {
        final GuessingAssumptions assumptions = SetupGuessingAssumptions.get();

        // A million guesses every second, the right one after half of all possible ones.
        assertEquals(1_000_000, SetupGuessingAssumptions.GUESSES_PER_SECOND);
        assertEquals(1e6, assumptions.units / assumptions.secondsPerGuess, 1e-6);
        assertEquals(0.5, assumptions.searchedShare, 0);
        // Stated next to it, not used in the estimate.
        assertEquals(10_000, SetupGuessingAssumptions.GRAPHICS_CARDS_NEEDED);
        assertEquals(1_000, SetupGuessingAssumptions.PURPOSE_BUILT_SPEEDUP);
    }

    @Test
    public void entropyBits_ofEachChoice() {
        assertEquals(19.931568569324174, StrengthComparison.entropyBits(Choice.PIN_6_DIGITS),
                1e-12);
        // log2(389,112)
        assertEquals(18.5698, StrengthComparison.entropyBits(Choice.PATTERN), 1e-4);
        assertEquals(64.6203509279267, StrengthComparison.entropyBits(Choice.WORDS_5), 1e-12);
        assertEquals(77.5444211135121, StrengthComparison.entropyBits(Choice.WORDS_6), 1e-12);
        assertEquals(90.4684912990974, StrengthComparison.entropyBits(Choice.WORDS_7), 1e-12);
        assertEquals(103.3925614846828, StrengthComparison.entropyBits(Choice.WORDS_8), 1e-12);
        assertEquals(39.86313713864835, StrengthComparison.entropyBits(Choice.RANDOM_PIN_12),
                1e-12);
        assertEquals(66.43856189774725, StrengthComparison.entropyBits(Choice.RANDOM_PIN_20),
                1e-12);
    }

    @Test
    public void entropyBits_ofWords_isTheGeneratorsToWithinABillionthOfABit() throws Exception {
        final PassphraseGenerator generator = new PassphraseGenerator(
                PassphraseTestUtils.effLarge(), new PassphraseTestUtils.SeededRandom(1));

        for (int words = 5; words <= 8; words++) {
            assertEquals(generator.entropyBits(words),
                    StrengthComparison.entropyBits(StrengthComparison.forWords(words)), 1e-9);
        }
    }

    @Test
    public void rows_underTheSetupAssumptions() {
        final GuessingAssumptions assumptions = SetupGuessingAssumptions.get();

        final List<Row> rows = StrengthComparison.rows(assumptions, Choice.values());

        // A million guesses a second, half of all values tried, 31,557,600 seconds to the
        // year, rounded down to two figures.
        assertEquals(8, rows.size());
        assertEquals("LESS_THAN 1.0 MINUTES", shown(rows.get(0)));           // 0.5 s
        assertEquals("LESS_THAN 1.0 MINUTES", shown(rows.get(1)));           // 0.19 s
        assertEquals("ABOUT 440000.0 YEARS", shown(rows.get(2)));            // 449,296
        assertEquals("ABOUT 3.4 BILLIONS_OF_YEARS", shown(rows.get(3)));     // 3.4919e9
        assertEquals("MORE_THAN 1000.0 BILLIONS_OF_YEARS", shown(rows.get(4)));
        assertEquals("MORE_THAN 1000.0 BILLIONS_OF_YEARS", shown(rows.get(5)));
        assertEquals("ABOUT 5.7 DAYS", shown(rows.get(6)));                  // 5.787
        assertEquals("ABOUT 1.5 MILLIONS_OF_YEARS", shown(rows.get(7)));     // 1.5844e6
        for (int i = 0; i < rows.size(); i++) {
            assertSame(Choice.values()[i], rows.get(i).choice);
            assertSame(assumptions, rows.get(i).estimate.assumptions);
            assertEquals(StrengthComparison.entropyBits(rows.get(i).choice),
                    rows.get(i).entropyBits, 0);
        }
    }

    @Test
    public void rows_inTheWordsOfTheScreen() {
        final List<Row> rows = StrengthComparison.rows(SetupGuessingAssumptions.get(),
                Choice.PIN_6_DIGITS, Choice.RANDOM_PIN_12, Choice.RANDOM_PIN_20,
                Choice.WORDS_5, Choice.WORDS_6, Choice.WORDS_7, Choice.WORDS_8);

        // The researched figures are 0.5 s, 5.8 days, 1.6 million, 450,000 and 3.5 billion
        // years when rounded to the nearest. Shown times are rounded down.
        assertEquals("seconds", inWords(rows.get(0)));
        assertEquals("about 5.7 days", inWords(rows.get(1)));
        assertEquals("about 1.5 million years", inWords(rows.get(2)));
        assertEquals("about 440,000 years", inWords(rows.get(3)));
        assertEquals("about 3.4 billion years", inWords(rows.get(4)));
        assertEquals("more than a trillion years", inWords(rows.get(5)));
        assertEquals("more than a trillion years", inWords(rows.get(6)));
    }

    @Test
    public void atBest_onlyForWhatAPersonPicks() {
        assertTrue(Choice.PIN_6_DIGITS.atBest);
        assertTrue(Choice.PATTERN.atBest);
        assertFalse(Choice.WORDS_5.atBest);
        assertFalse(Choice.WORDS_6.atBest);
        assertFalse(Choice.WORDS_7.atBest);
        assertFalse(Choice.WORDS_8.atBest);
        assertFalse(Choice.RANDOM_PIN_12.atBest);
        assertFalse(Choice.RANDOM_PIN_20.atBest);
    }

    @Test
    public void forWords_fiveToEight() {
        assertSame(Choice.WORDS_5, StrengthComparison.forWords(5));
        assertSame(Choice.WORDS_6, StrengthComparison.forWords(6));
        assertSame(Choice.WORDS_7, StrengthComparison.forWords(7));
        assertSame(Choice.WORDS_8, StrengthComparison.forWords(8));
        assertThrows(IllegalArgumentException.class, () -> StrengthComparison.forWords(4));
        assertThrows(IllegalArgumentException.class, () -> StrengthComparison.forWords(9));
    }
}

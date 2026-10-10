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

import java.util.List;

public class StrengthComparisonTest {

    private static String shown(Row row) {
        return row.estimate.describe((wording, amount, unit) -> wording + " " + amount + " "
                + unit);
    }

    @Test
    public void placeholderAssumptions_areTheStatedOnes() {
        final GuessingAssumptions assumptions = PlaceholderGuessingAssumptions.get();

        assertEquals(1000, PlaceholderGuessingAssumptions.MACHINES);
        assertEquals(30, PlaceholderGuessingAssumptions.GUESSES_PER_SECOND_PER_MACHINE);
        assertEquals(1000, assumptions.units, 0);
        assertEquals(1 / 30.0, assumptions.secondsPerGuess, 1e-15);
        assertEquals(0.5, assumptions.searchedShare, 0);
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
    public void rows_underThePlaceholderAssumptions() {
        final GuessingAssumptions assumptions = PlaceholderGuessingAssumptions.get();

        final List<Row> rows = StrengthComparison.rows(assumptions, Choice.values());

        // 30,000 guesses a second, half of all values tried, rounded down to two figures.
        assertEquals(7, rows.size());
        assertEquals("ABOUT 16.0 SECONDS", shown(rows.get(0)));              // 10^6 / 2 / 3e4
        assertEquals("ABOUT 6.4 SECONDS", shown(rows.get(1)));               // 389,112 / 2 / 3e4
        assertEquals("ABOUT 14.0 MILLIONS_OF_YEARS", shown(rows.get(2)));
        assertEquals("ABOUT 110.0 BILLIONS_OF_YEARS", shown(rows.get(3)));
        assertEquals("MORE_THAN 1000.0 BILLIONS_OF_YEARS", shown(rows.get(4)));
        assertEquals("MORE_THAN 1000.0 BILLIONS_OF_YEARS", shown(rows.get(5)));
        assertEquals("ABOUT 52.0 MILLIONS_OF_YEARS", shown(rows.get(6)));
        for (int i = 0; i < rows.size(); i++) {
            assertSame(Choice.values()[i], rows.get(i).choice);
            assertSame(assumptions, rows.get(i).estimate.assumptions);
            assertEquals(StrengthComparison.entropyBits(rows.get(i).choice),
                    rows.get(i).entropyBits, 0);
        }
    }

    @Test
    public void atBest_onlyForWhatAPersonPicks() {
        assertTrue(Choice.PIN_6_DIGITS.atBest);
        assertTrue(Choice.PATTERN.atBest);
        assertFalse(Choice.WORDS_5.atBest);
        assertFalse(Choice.WORDS_6.atBest);
        assertFalse(Choice.WORDS_7.atBest);
        assertFalse(Choice.WORDS_8.atBest);
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

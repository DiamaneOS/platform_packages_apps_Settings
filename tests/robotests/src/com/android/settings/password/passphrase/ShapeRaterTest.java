/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.settings.password.passphrase.ChosenPassphraseRater.Rating;

import org.junit.Test;

public class ShapeRaterTest {

    private static Rating rate(String phrase) {
        return new ShapeRater().rate(phrase.toCharArray(), phrase.length());
    }

    @Test
    public void belowTheFloor_isBelowMinimum() {
        assertEquals(Rating.BELOW_MINIMUM, rate(""));
        assertEquals(Rating.BELOW_MINIMUM, rate("short password"));
        assertEquals(Rating.BELOW_MINIMUM, rate("01234567890123456789"));
        assertEquals(Rating.BELOW_MINIMUM, rate("abababababababababababab"));
    }

    @Test
    public void wrongOneWay_aShortRandomPasswordIsBelowMinimum() {
        // Twelve random characters are hard to guess. The shape check cannot know that.
        assertEquals(Rating.BELOW_MINIMUM, rate("q7#Lw2!vXp9$"));
    }

    @Test
    public void wrongTheOtherWay_aLongQuoteIsNotFlagged() {
        // An attacker would try this early. The shape check cannot know that either.
        assertEquals(Rating.NOT_EASILY_GUESSED, rate("to be or not to be that is"));
    }

    @Test
    public void repeatedBlock_isGuessable() {
        assertEquals(Rating.GUESSABLE, rate("horse horse horse horse "));
        assertEquals(Rating.GUESSABLE, rate("correcthorsecorrecthorse"));
        assertEquals(Rating.GUESSABLE, rate("password1password1password1"));
        // The block may be cut short at the end.
        assertEquals(Rating.GUESSABLE, rate("abcdefg-abcdefg-abcdefg-abc"));
    }

    @Test
    public void oneLongRun_isGuessable() {
        assertEquals(Rating.GUESSABLE, rate("abcdefghijklmnopqrstuvwxyz"));
        assertEquals(Rating.GUESSABLE, rate("zyxwvutsrqponmlkjihgfedcba"));
        assertEquals(Rating.GUESSABLE, rate("abcdefghijklmnopqrstu!"));
    }

    @Test
    public void fewDifferentCharacters_isGuessable() {
        // Seven different characters, no block repeated, no long run.
        assertEquals(Rating.GUESSABLE, rate("abcdefgagfedcbagabgdfec"));
        // One more character and it is not flagged.
        assertEquals(Rating.NOT_EASILY_GUESSED, rate("abcdefg gfedcba abgdfec"));
    }

    @Test
    public void aGeneratedPhrase_isNotFlagged() {
        assertEquals(Rating.NOT_EASILY_GUESSED, rate("abacus zoom abdomen yoyo zoology"));
        assertEquals(Rating.NOT_EASILY_GUESSED, rate("mango stairs violet copper engine"));
    }

    @Test
    public void rate_looksOnlyAtTheGivenLength_andChangesNothing() {
        final char[] buffer = "abacus zoom abdomen yoyo zoology and more".toCharArray();
        final char[] before = buffer.clone();

        assertEquals(Rating.BELOW_MINIMUM, new ShapeRater().rate(buffer, 11));
        assertEquals(Rating.NOT_EASILY_GUESSED, new ShapeRater().rate(buffer, 32));
        assertArrayEquals(before, buffer);
    }
}

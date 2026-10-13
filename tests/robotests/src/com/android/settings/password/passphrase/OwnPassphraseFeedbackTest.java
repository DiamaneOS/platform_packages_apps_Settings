/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.ChosenPassphraseRater.Rating;
import com.android.settings.password.passphrase.OwnPassphraseFeedback.Verdict;

import org.junit.Test;

public class OwnPassphraseFeedbackTest {

    /** The class the lock settings give a typed password: strong if it meets the floor. */
    private static StrengthClass classOf(String password) {
        return PassphraseFloor.isMet(password.toCharArray(), password.length())
                ? StrengthClass.STRONG : StrengthClass.WEAKER;
    }

    private static OwnPassphraseFeedback feedback(String password) {
        return OwnPassphraseFeedback.of(classOf(password),
                new ShapeRater().rate(password.toCharArray(), password.length()));
    }

    @Test
    public void shortRandomPassword_countsAsWeaker_andIsNotRefused() {
        // Twelve random characters, as a password manager makes them.
        final OwnPassphraseFeedback feedback = feedback("q7#Lw2!vXp9$");

        assertTrue(feedback.countsAsWeaker);
        assertFalse(feedback.looksGuessable);
        assertFalse(feedback.learningPeriodApplies);
    }

    @Test
    public void weakerPassword_needsTheRiskScreenLikeAPinOrPattern() {
        final StrengthClass shortPassword = classOf("q7#Lw2!vXp9$");

        assertEquals(StrengthClass.WEAKER, shortPassword);
        assertTrue(WeakerRiskRule.riskScreenNeeded(shortPassword, StrengthClass.STRONG));
        assertTrue(WeakerRiskRule.riskScreenNeeded(shortPassword, StrengthClass.NONE));
        assertTrue(WeakerRiskRule.riskScreenNeeded(shortPassword, StrengthClass.UNKNOWN));
        // Not again when the lock in use is a PIN, a pattern or another short password.
        assertFalse(WeakerRiskRule.riskScreenNeeded(shortPassword, StrengthClass.WEAKER));
    }

    @Test
    public void strongShape_noRiskScreen_andTheLearningPeriodIsMentioned() {
        final OwnPassphraseFeedback feedback = feedback("mango stairs violet copper engine");

        assertFalse(feedback.countsAsWeaker);
        assertFalse(feedback.looksGuessable);
        assertTrue(feedback.learningPeriodApplies);
        for (StrengthClass current : StrengthClass.values()) {
            assertFalse(WeakerRiskRule.riskScreenNeeded(
                    classOf("mango stairs violet copper engine"), current));
        }
    }

    @Test
    public void strongShapeThatRepeats_isWarnedAbout_butStillStrong() {
        final OwnPassphraseFeedback feedback = feedback("horse horse horse horse ");

        assertFalse(feedback.countsAsWeaker);
        assertTrue(feedback.looksGuessable);
        // The service treats it as strong, so the learning period runs.
        assertTrue(feedback.learningPeriodApplies);
    }

    @Test
    public void weakerShapes_digitsOnly_fewCharacters_empty() {
        assertTrue(feedback("01234567890123456789").countsAsWeaker);
        assertTrue(feedback("abababababababababababab").countsAsWeaker);
        assertTrue(feedback("").countsAsWeaker);
        assertTrue(feedback("abcd").countsAsWeaker);
    }

    private static Verdict verdict(String password) {
        return OwnPassphraseFeedback.of(password.toCharArray(), password.length(),
                classOf(password),
                new ShapeRater().rate(password.toCharArray(), password.length())).verdict;
    }

    @Test
    public void verdict_followsWhatIsTyped() {
        assertEquals(Verdict.EMPTY, verdict(""));
        assertEquals(Verdict.WEAKER_TOO_SHORT, verdict("a"));
        assertEquals(Verdict.WEAKER_TOO_SHORT, verdict("q7#Lw2!vXp9$"));
        assertEquals(Verdict.WEAKER_TOO_SHORT, verdict("mango stairs violet"));      // 19
        assertEquals(Verdict.STRONG, verdict("mango stairs violets"));               // 20
        assertEquals(Verdict.STRONG, verdict("mango stairs violet copper engine"));
        assertEquals(Verdict.STRONG_LOOKS_GUESSABLE, verdict("horse horse horse horse "));
    }

    @Test
    public void verdict_weakerAtFullLength_saysWhy() {
        assertEquals(Verdict.WEAKER_DIGITS_ONLY, verdict("01234567890123456789"));
        assertEquals(Verdict.WEAKER_FEW_CHARACTERS, verdict("abababababababababababab"));
        assertEquals(Verdict.WEAKER_FEW_CHARACTERS, verdict("aaaa bbbb aaaa bbbb aaaa"));
        // Short and digits only: the length comes first, it is what to fix first.
        assertEquals(Verdict.WEAKER_TOO_SHORT, verdict("123456"));
    }

    @Test
    public void verdict_matchesTheFlags() {
        for (String password : new String[] {"", "abcd", "q7#Lw2!vXp9$", "01234567890123456789",
                "abababababababababababab", "mango stairs violet copper engine",
                "horse horse horse horse "}) {
            final OwnPassphraseFeedback feedback = OwnPassphraseFeedback.of(
                    password.toCharArray(), password.length(), classOf(password),
                    new ShapeRater().rate(password.toCharArray(), password.length()));
            final boolean strong = feedback.verdict == Verdict.STRONG
                    || feedback.verdict == Verdict.STRONG_LOOKS_GUESSABLE;
            assertEquals(password, !strong, feedback.countsAsWeaker);
            assertEquals(password, strong, feedback.learningPeriodApplies);
            assertEquals(password, feedback.verdict == Verdict.STRONG_LOOKS_GUESSABLE,
                    feedback.looksGuessable);
            // The flags agree with the simpler way of asking.
            final OwnPassphraseFeedback simple = feedback(password);
            assertEquals(password, simple.countsAsWeaker, feedback.countsAsWeaker);
            assertEquals(password, simple.looksGuessable, feedback.looksGuessable);
        }
    }

    @Test
    public void verdict_serviceSaysWeakerForAReasonNotSeenHere() {
        final String password = "mango stairs violet copper engine";

        final OwnPassphraseFeedback feedback = OwnPassphraseFeedback.of(password.toCharArray(),
                password.length(), StrengthClass.WEAKER, Rating.NOT_EASILY_GUESSED);

        assertEquals(Verdict.WEAKER, feedback.verdict);
        assertTrue(feedback.countsAsWeaker);
    }

    @Test
    public void theServicesClassDecides_notTheRating() {
        // Should the two ever disagree, the screen follows the lock settings service: that is
        // what refuses a save without the risk screen.
        final OwnPassphraseFeedback weaker =
                OwnPassphraseFeedback.of(StrengthClass.WEAKER, Rating.NOT_EASILY_GUESSED);
        assertTrue(weaker.countsAsWeaker);
        assertFalse(weaker.learningPeriodApplies);

        final OwnPassphraseFeedback strong =
                OwnPassphraseFeedback.of(StrengthClass.STRONG, Rating.BELOW_MINIMUM);
        assertFalse(strong.countsAsWeaker);
        assertFalse(strong.looksGuessable);
        assertTrue(strong.learningPeriodApplies);

        // A class that is not known is not treated as strong.
        assertTrue(OwnPassphraseFeedback.of(StrengthClass.UNKNOWN, Rating.GUESSABLE)
                .countsAsWeaker);
        assertFalse(OwnPassphraseFeedback.of(StrengthClass.UNKNOWN, Rating.GUESSABLE)
                .looksGuessable);
    }
}

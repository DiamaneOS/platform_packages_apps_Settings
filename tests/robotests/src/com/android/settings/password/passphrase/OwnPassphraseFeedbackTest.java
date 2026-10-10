/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.ChosenPassphraseRater.Rating;

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

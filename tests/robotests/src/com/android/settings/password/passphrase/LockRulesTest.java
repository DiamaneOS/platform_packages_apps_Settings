/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static com.android.settings.password.passphrase.StrengthClass.NONE;
import static com.android.settings.password.passphrase.StrengthClass.STRONG;
import static com.android.settings.password.passphrase.StrengthClass.UNKNOWN;
import static com.android.settings.password.passphrase.StrengthClass.WEAKER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.LockLabel.Kind;

import org.junit.Test;

/** The risk screen rule, the lock labels and the learning period's days. */
public class LockRulesTest {

    @Test
    public void riskScreen_neededWhenAWeakerLockReplacesAnythingButAWeakerOne() {
        assertTrue(WeakerRiskRule.riskScreenNeeded(WEAKER, STRONG));
        assertTrue(WeakerRiskRule.riskScreenNeeded(WEAKER, NONE));
        // A password that was not typed since the restart could be a passphrase.
        assertTrue(WeakerRiskRule.riskScreenNeeded(WEAKER, UNKNOWN));
        // The user agreed when the current weaker lock was set.
        assertFalse(WeakerRiskRule.riskScreenNeeded(WEAKER, WEAKER));
    }

    @Test
    public void riskScreen_neverNeededForAStrongLockOrNone() {
        for (StrengthClass current : StrengthClass.values()) {
            assertFalse(WeakerRiskRule.riskScreenNeeded(STRONG, current));
            assertFalse(WeakerRiskRule.riskScreenNeeded(NONE, current));
            assertFalse(WeakerRiskRule.riskScreenNeeded(UNKNOWN, current));
        }
    }

    @Test
    public void label_password() {
        assertEquals(LockLabel.PASSPHRASE, LockLabel.of(Kind.PASSWORD, STRONG));
        assertEquals(LockLabel.PASSWORD_WEAKER, LockLabel.of(Kind.PASSWORD, WEAKER));
        assertEquals(LockLabel.PASSWORD_NOT_TYPED_SINCE_RESTART,
                LockLabel.of(Kind.PASSWORD, UNKNOWN));
        // Not a guess either when the service's answer does not fit a password.
        assertEquals(LockLabel.PASSWORD_NOT_TYPED_SINCE_RESTART,
                LockLabel.of(Kind.PASSWORD, NONE));
    }

    @Test
    public void label_pin() {
        assertEquals(LockLabel.RANDOM_PIN, LockLabel.of(Kind.PIN, STRONG));
        assertEquals(LockLabel.PIN_WEAKER, LockLabel.of(Kind.PIN, WEAKER));
        assertEquals(LockLabel.PIN_WEAKER, LockLabel.of(Kind.PIN, UNKNOWN));
        assertEquals(LockLabel.PIN_WEAKER, LockLabel.of(Kind.PIN, NONE));
    }

    @Test
    public void label_patternIsWeakerAndNoneIsNoLock() {
        for (StrengthClass strength : StrengthClass.values()) {
            assertEquals(LockLabel.PATTERN_WEAKER, LockLabel.of(Kind.PATTERN, strength));
            assertEquals(LockLabel.NO_LOCK, LockLabel.of(Kind.NONE, strength));
        }
    }

    @Test
    public void learningDays_roundUp() {
        final long day = 24 * 60 * 60 * 1000L;
        assertEquals(0, LearningPeriod.daysRoundedUp(0));
        assertEquals(0, LearningPeriod.daysRoundedUp(-5));
        assertEquals(1, LearningPeriod.daysRoundedUp(1));
        assertEquals(1, LearningPeriod.daysRoundedUp(day));
        assertEquals(2, LearningPeriod.daysRoundedUp(day + 1));
        assertEquals(14, LearningPeriod.daysRoundedUp(14 * day));
        assertEquals(14, LearningPeriod.daysRoundedUp(13 * day + 1));
        assertEquals(Integer.MAX_VALUE, LearningPeriod.daysRoundedUp(Long.MAX_VALUE));
    }
}

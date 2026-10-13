/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.safetycenter;

import static com.android.settings.safetycenter.TallyLockStatusRule.MODE_OFF;
import static com.android.settings.safetycenter.TallyLockStatusRule.MODE_ROW;
import static com.android.settings.safetycenter.TallyLockStatusRule.MODE_ROW_AND_CARD;

import static org.junit.Assert.assertEquals;

import com.android.settings.password.passphrase.LockLabel;
import com.android.settings.safetycenter.TallyLockStatusRule.Card;
import com.android.settings.safetycenter.TallyLockStatusRule.Level;
import com.android.settings.safetycenter.TallyLockStatusRule.Verdict;

import org.junit.Test;

/** What the Security &amp; privacy page is told about a lock that is set. */
public class TallyLockStatusRuleTest {

    private static Verdict set(LockLabel label, int mode) {
        return TallyLockStatusRule.of(/* allowedByAdmin= */ true, /* secure= */ true, label, mode);
    }

    private static void assertVerdict(Level level, Card card, Verdict verdict) {
        assertEquals(level, verdict.level);
        assertEquals(card, verdict.card);
    }

    @Test
    public void weakerLock_isARecommendationWithTheCardOfItsKind() {
        assertVerdict(Level.RECOMMENDATION, Card.PIN,
                set(LockLabel.PIN_WEAKER, MODE_ROW_AND_CARD));
        assertVerdict(Level.RECOMMENDATION, Card.PATTERN,
                set(LockLabel.PATTERN_WEAKER, MODE_ROW_AND_CARD));
        assertVerdict(Level.RECOMMENDATION, Card.PASSWORD,
                set(LockLabel.PASSWORD_WEAKER, MODE_ROW_AND_CARD));
    }

    @Test
    public void strongLock_staysAsStock() {
        assertVerdict(Level.AS_STOCK, Card.NONE, set(LockLabel.PASSPHRASE, MODE_ROW_AND_CARD));
        assertVerdict(Level.AS_STOCK, Card.NONE, set(LockLabel.RANDOM_PIN, MODE_ROW_AND_CARD));
    }

    @Test
    public void passwordOfUnknownClass_makesNoClaimAndHasNoCard() {
        for (int mode : new int[] {MODE_ROW, MODE_ROW_AND_CARD}) {
            assertVerdict(Level.NO_CLAIM, Card.NONE,
                    set(LockLabel.PASSWORD_NOT_TYPED_SINCE_RESTART, mode));
        }
    }


    @Test
    public void noLock_staysAsStock() {
        // Android already reports no lock as a recommendation with its own card.
        for (LockLabel label : LockLabel.values()) {
            assertVerdict(Level.AS_STOCK, Card.NONE, TallyLockStatusRule.of(
                    /* allowedByAdmin= */ true, /* secure= */ false, label, MODE_ROW_AND_CARD));
        }
    }

    @Test
    public void rowDisabledByAdmin_staysAsStock() {
        for (LockLabel label : LockLabel.values()) {
            assertVerdict(Level.AS_STOCK, Card.NONE, TallyLockStatusRule.of(
                    /* allowedByAdmin= */ false, /* secure= */ true, label, MODE_ROW_AND_CARD));
        }
    }

    @Test
    public void modeOff_changesNothing() {
        for (LockLabel label : LockLabel.values()) {
            assertVerdict(Level.AS_STOCK, Card.NONE, set(label, MODE_OFF));
            assertVerdict(Level.AS_STOCK, Card.NONE, set(label, -1));
        }
    }

    @Test
    public void modeRow_turnsTheRowYellowWithoutACard() {
        assertVerdict(Level.RECOMMENDATION, Card.NONE, set(LockLabel.PIN_WEAKER, MODE_ROW));
        assertVerdict(Level.RECOMMENDATION, Card.NONE, set(LockLabel.PATTERN_WEAKER, MODE_ROW));
        assertVerdict(Level.RECOMMENDATION, Card.NONE, set(LockLabel.PASSWORD_WEAKER, MODE_ROW));
    }


    @Test
    public void cardIsNeverShownWithoutAYellowRow() {
        // Safety Center refuses a card that is more severe than its row.
        for (LockLabel label : LockLabel.values()) {
            for (int mode = -1; mode <= 3; mode++) {
                for (int flags = 0; flags < 4; flags++) {
                    final Verdict verdict = TallyLockStatusRule.of(
                            (flags & 1) != 0, (flags & 2) != 0, label, mode);
                    if (verdict.card != Card.NONE) {
                        assertEquals(Level.RECOMMENDATION, verdict.level);
                    }
                }
            }
        }
    }

}

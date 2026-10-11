/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.safetycenter;

import com.android.settings.password.passphrase.LockLabel;

/**
 * What the Security &amp; privacy page is told about a screen lock. It follows the lock's label
 * on the Security page, so the row's text and colour cannot disagree.
 *
 * <p>No Android classes, so it runs in plain JUnit.
 */
public final class TallyLockStatusRule {

    /** Any lock is reported as fine. */
    public static final int MODE_OFF = 0;
    /** A weaker lock turns its row yellow. */
    public static final int MODE_ROW = 1;
    /** A weaker lock turns its row yellow and shows a card. */
    public static final int MODE_ROW_AND_CARD = 2;

    public enum Level {
        AS_STOCK,
        /** Neither fine nor a recommendation. */
        NO_CLAIM,
        RECOMMENDATION,
    }

    /** The card, by the kind of lock it is about. */
    public enum Card { NONE, PIN, PATTERN, PASSWORD }

    public static final class Verdict {
        public final Level level;
        public final Card card;

        private Verdict(Level level, Card card) {
            this.level = level;
            this.card = card;
        }
    }

    private static final Verdict AS_STOCK = new Verdict(Level.AS_STOCK, Card.NONE);

    private TallyLockStatusRule() {}

    /** A row an admin disabled, no lock and a lock without a label stay as stock. */
    public static Verdict of(boolean allowedByAdmin, boolean secure, LockLabel label, int mode) {
        if (!allowedByAdmin || !secure || label == null || mode <= MODE_OFF) {
            return AS_STOCK;
        }
        final boolean withCard = mode >= MODE_ROW_AND_CARD;
        switch (label) {
            case PIN_WEAKER:
                return weaker(withCard, Card.PIN);
            case PATTERN_WEAKER:
                return weaker(withCard, Card.PATTERN);
            case PASSWORD_WEAKER:
                return weaker(withCard, Card.PASSWORD);
            case PASSWORD_NOT_TYPED_SINCE_RESTART:
                // Could be a passphrase or a short password.
                return new Verdict(Level.NO_CLAIM, Card.NONE);
            default:
                return AS_STOCK;
        }
    }

    private static Verdict weaker(boolean withCard, Card card) {
        return new Verdict(Level.RECOMMENDATION, withCard ? card : Card.NONE);
    }
}

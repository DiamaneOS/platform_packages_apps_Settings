/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import com.android.settings.password.passphrase.ChosenPassphraseRater.Rating;

/**
 * What the screen tells a user about a password or passphrase they are typing in.
 *
 * <p>A password under the shape of a strong passphrase is not refused: it counts as weaker on
 * this phone, like a PIN or a pattern, and is set after the same risk screen. A random
 * 12-character password from a password manager is such a one.
 */
public final class OwnPassphraseFeedback {

    /**
     * Whether the password counts as weaker on this phone. The screen says so, and the risk
     * screen comes before it is set, unless the current lock is weaker already.
     */
    public final boolean countsAsWeaker;

    /**
     * Whether to warn that the password looks easy to guess although it is long enough to count
     * as strong.
     */
    public final boolean looksGuessable;

    /**
     * Whether to say that the phone asks for the new lock daily at first. Only a lock of the
     * strong class has that learning period.
     */
    public final boolean learningPeriodApplies;

    private OwnPassphraseFeedback(boolean countsAsWeaker, boolean looksGuessable) {
        this.countsAsWeaker = countsAsWeaker;
        this.looksGuessable = looksGuessable;
        this.learningPeriodApplies = !countsAsWeaker;
    }

    /**
     * @param newClass the class the lock settings service gives the password; the service has
     *     the last word on what is weaker
     * @param rating what the shape rater makes of it
     */
    public static OwnPassphraseFeedback of(StrengthClass newClass, Rating rating) {
        final boolean weaker = newClass != StrengthClass.STRONG;
        return new OwnPassphraseFeedback(weaker, !weaker && rating == Rating.GUESSABLE);
    }
}

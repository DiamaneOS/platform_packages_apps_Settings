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

    /** The one-line verdict shown under the field while the user types. */
    public enum Verdict {
        /** Nothing typed yet: say what counts as strong. */
        EMPTY,
        /** Weaker: fewer characters than a strong passphrase has. */
        WEAKER_TOO_SHORT,
        /** Weaker: long enough, but digits only. That is a PIN. */
        WEAKER_DIGITS_ONLY,
        /** Weaker: long enough, but too few different characters. */
        WEAKER_FEW_CHARACTERS,
        /** Weaker for the lock settings service, for a reason this class does not see. */
        WEAKER,
        /** Has the shape of a strong passphrase. */
        STRONG,
        /** Has the shape of a strong passphrase, but looks easy to guess. */
        STRONG_LOOKS_GUESSABLE,
    }

    /** The verdict to show. */
    public final Verdict verdict;

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

    private OwnPassphraseFeedback(Verdict verdict) {
        this.verdict = verdict;
        this.countsAsWeaker =
                verdict != Verdict.STRONG && verdict != Verdict.STRONG_LOOKS_GUESSABLE;
        this.looksGuessable = verdict == Verdict.STRONG_LOOKS_GUESSABLE;
        this.learningPeriodApplies = !countsAsWeaker;
    }

    /**
     * @param newClass the class the lock settings service gives the password; the service has
     *     the last word on what is weaker
     * @param rating what the shape rater makes of it
     */
    public static OwnPassphraseFeedback of(StrengthClass newClass, Rating rating) {
        if (newClass != StrengthClass.STRONG) {
            return new OwnPassphraseFeedback(Verdict.WEAKER);
        }
        return new OwnPassphraseFeedback(rating == Rating.GUESSABLE
                ? Verdict.STRONG_LOOKS_GUESSABLE : Verdict.STRONG);
    }

    /**
     * As {@link #of(StrengthClass, Rating)}, and for a weaker password also why it is weaker,
     * read from the first {@code length} characters of {@code password}. Nothing of it is
     * kept.
     */
    public static OwnPassphraseFeedback of(char[] password, int length, StrengthClass newClass,
            Rating rating) {
        if (length == 0) {
            return new OwnPassphraseFeedback(Verdict.EMPTY);
        }
        if (newClass == StrengthClass.STRONG) {
            return of(newClass, rating);
        }
        if (length < PassphraseFloor.MIN_LENGTH) {
            return new OwnPassphraseFeedback(Verdict.WEAKER_TOO_SHORT);
        }
        boolean digitsOnly = true;
        for (int i = 0; i < length && digitsOnly; i++) {
            digitsOnly = password[i] >= '0' && password[i] <= '9';
        }
        if (digitsOnly) {
            return new OwnPassphraseFeedback(Verdict.WEAKER_DIGITS_ONLY);
        }
        // Long enough and not digits only: what is left of the floor is the variety. If that
        // is met too, the service has a reason of its own.
        return new OwnPassphraseFeedback(PassphraseFloor.isMet(password, length)
                ? Verdict.WEAKER : Verdict.WEAKER_FEW_CHARACTERS);
    }
}

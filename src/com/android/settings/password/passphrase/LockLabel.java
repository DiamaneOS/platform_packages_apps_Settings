/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * What Settings calls the screen lock in use, so that the name says how much it protects on
 * this phone.
 */
public enum LockLabel {
    /** No lock, or swipe. */
    NO_LOCK,
    /** A password that meets the floor: "Passphrase". */
    PASSPHRASE,
    /** A password under the floor: "Password: weaker on this phone". */
    PASSWORD_WEAKER,
    /**
     * A password whose class is not known, because it was not typed since the restart. Shown
     * with a neutral name, not a guess.
     */
    PASSWORD_NOT_TYPED_SINCE_RESTART,
    /** A long PIN the phone generated: "Long random PIN". */
    RANDOM_PIN,
    /** A PIN the user chose: "PIN: weaker on this phone". */
    PIN_WEAKER,
    /** A pattern: "Pattern: weaker on this phone". */
    PATTERN_WEAKER;

    /** The kind of screen lock, as stored. */
    public enum Kind { NONE, PATTERN, PIN, PASSWORD }

    /** The label for a lock of the given kind and strength class. */
    public static LockLabel of(Kind kind, StrengthClass strength) {
        switch (kind) {
            case PASSWORD:
                if (strength == StrengthClass.STRONG) {
                    return PASSPHRASE;
                }
                return strength == StrengthClass.WEAKER
                        ? PASSWORD_WEAKER : PASSWORD_NOT_TYPED_SINCE_RESTART;
            case PIN:
                // Only a generated PIN is strong, and the service knows a PIN's class at all
                // times. Anything else is treated as chosen.
                return strength == StrengthClass.STRONG ? RANDOM_PIN : PIN_WEAKER;
            case PATTERN:
                return PATTERN_WEAKER;
            default:
                return NO_LOCK;
        }
    }
}

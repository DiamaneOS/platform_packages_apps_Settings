/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * The strength class the lock settings service gives a screen lock. It is a shape check, not a
 * measurement: see {@link PassphraseFloor}.
 */
public enum StrengthClass {
    /** A password that was not typed since the restart: its class is not stored. */
    UNKNOWN,
    /** No screen lock. */
    NONE,
    /** A pattern, a chosen PIN, or a password under the floor. */
    WEAKER,
    /** A password that meets the floor, or a long PIN the phone generated. */
    STRONG,
}

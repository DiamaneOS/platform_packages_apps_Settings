/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.settings.password.ScreenLockType;

/**
 * The order of the choices in the screen lock picker: the strong ones first, the passphrase the
 * phone generates at the top; then the weaker ones; no lock last.
 *
 * <p>Set in code, so that the picker's layout files keep the order they have upstream.
 */
public final class LockPickerOrder {

    private static final ScreenLockType[] ORDER = {
        ScreenLockType.GENERATED_PASSPHRASE,
        ScreenLockType.PASSWORD,
        ScreenLockType.GENERATED_PIN,
        ScreenLockType.PIN,
        ScreenLockType.PATTERN,
        ScreenLockType.SWIPE,
        ScreenLockType.NONE,
    };

    private LockPickerOrder() {}

    /** Orders the lock choices of a picker. Its other entries follow, in the order they had. */
    public static void apply(PreferenceGroup picker) {
        for (int i = 0; i < ORDER.length; i++) {
            final Preference choice = picker.findPreference(ORDER[i].preferenceKey);
            if (choice != null) {
                // Below zero: before every entry that keeps the order it was added in.
                choice.setOrder(i - ORDER.length);
            }
        }
    }
}

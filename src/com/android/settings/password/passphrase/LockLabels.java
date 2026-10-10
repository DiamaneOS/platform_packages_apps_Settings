/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.app.admin.DevicePolicyManager;

import androidx.annotation.Nullable;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;

/** The name Settings shows for the screen lock in use: see {@link LockLabel}. */
public final class LockLabels {

    private LockLabels() {}

    /**
     * The text for the screen lock of a user, or null when the usual text is to be used (no
     * lock, or a kind this does not know).
     *
     * @param credentialOwnerUserId the user that owns the lock: the parent, for a profile that
     *     shares its parent's lock
     * @param storedQuality the stored password quality of that lock
     */
    @Nullable
    public static Integer summaryResId(LockPatternUtils utils, int credentialOwnerUserId,
            int storedQuality) {
        final LockLabel.Kind kind = kindOf(storedQuality);
        if (kind == LockLabel.Kind.NONE) {
            return null;
        }
        switch (LockLabel.of(kind, LockStrength.current(utils, credentialOwnerUserId))) {
            case PASSPHRASE:
                return R.string.tally_lock_label_passphrase;
            case PASSWORD_WEAKER:
                return R.string.tally_lock_label_password_weaker;
            case PASSWORD_NOT_TYPED_SINCE_RESTART:
                return R.string.tally_lock_label_password_unknown;
            case RANDOM_PIN:
                return R.string.tally_lock_label_random_pin;
            case PIN_WEAKER:
                return R.string.tally_lock_label_pin_weaker;
            case PATTERN_WEAKER:
                return R.string.tally_lock_label_pattern_weaker;
            default:
                return null;
        }
    }

    private static LockLabel.Kind kindOf(int storedQuality) {
        switch (storedQuality) {
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                return LockLabel.Kind.PATTERN;
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
                return LockLabel.Kind.PIN;
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
            case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                return LockLabel.Kind.PASSWORD;
            default:
                // Also a lock that a device admin manages: it keeps its usual name.
                return LockLabel.Kind.NONE;
        }
    }
}

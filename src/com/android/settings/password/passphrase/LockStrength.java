/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.util.Log;

import com.android.internal.widget.LockCredentialPolicy;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;

/** Asks the lock settings for strength classes, as {@link StrengthClass}. */
public final class LockStrength {

    private static final String TAG = "LockStrength";

    private LockStrength() {}

    /** The class of a {@code LockCredentialPolicy.STRENGTH_} value. */
    public static StrengthClass fromPolicy(int strength) {
        switch (strength) {
            case LockCredentialPolicy.STRENGTH_STRONG:
                return StrengthClass.STRONG;
            case LockCredentialPolicy.STRENGTH_WEAKER:
                return StrengthClass.WEAKER;
            case LockCredentialPolicy.STRENGTH_NONE:
                return StrengthClass.NONE;
            default:
                return StrengthClass.UNKNOWN;
        }
    }

    /**
     * The class the lock settings service will give a credential when it is set.
     *
     * @param generated whether it is a PIN that came from
     *     {@link LockPatternUtils#generateStrongPin} in this flow
     */
    public static StrengthClass of(LockscreenCredential credential, boolean generated) {
        return fromPolicy(LockCredentialPolicy.getStrength(credential, generated));
    }

    /**
     * The class of the screen lock a user has now. Pass the user whose lock is set: a profile
     * with its own lock is judged on its own. {@link StrengthClass#UNKNOWN} if the service
     * cannot be asked, which makes the screens ask rather than assume.
     */
    public static StrengthClass current(LockPatternUtils utils, int userId) {
        try {
            return fromPolicy(utils.getCredentialStrength(userId));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not get the strength of the screen lock", e);
            return StrengthClass.UNKNOWN;
        }
    }
}

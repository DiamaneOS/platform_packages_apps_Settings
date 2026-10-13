/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * When the user has to be told about the risk of a weaker screen lock before one is set.
 *
 * <p>The lock settings service enforces the same rule: it refuses a weaker lock unless the
 * current one is known to be weaker, or the user's agreement was recorded just before.
 */
public final class WeakerRiskRule {

    private WeakerRiskRule() {}

    /**
     * Whether the risk screen is needed before a lock of class {@code newLock} replaces one of
     * class {@code current}. It is, each time a weaker lock replaces a strong one, none, or a
     * password whose class is not known. A weaker lock replaces a weaker one without it: the
     * user agreed when that one was set.
     */
    public static boolean riskScreenNeeded(StrengthClass newLock, StrengthClass current) {
        return newLock == StrengthClass.WEAKER && current != StrengthClass.WEAKER;
    }
}

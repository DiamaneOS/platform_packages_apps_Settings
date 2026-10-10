/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;

/**
 * Keeps track, for one screen that sets a lock, of whether the user has to see the risk screen
 * and whether they agreed.
 *
 * <p>The answer stays in the screen until the save. It is not a secret and survives a
 * recreation of the screen; the lock settings service is only told right before the lock is
 * set, by the save worker.
 */
public final class WeakerRiskGate {

    private static final String KEY_ACCEPTED = "tally_weaker_risk_accepted";

    private final Fragment mFragment;
    private final LockPatternUtils mUtils;
    private final int mUserId;
    private boolean mAccepted;

    /**
     * @param fragment the screen; it implements {@link WeakerRiskDialog.Listener}
     * @param userId the user whose lock is set
     */
    public WeakerRiskGate(Fragment fragment, LockPatternUtils utils, int userId,
            @Nullable Bundle savedInstanceState) {
        mFragment = fragment;
        mUtils = utils;
        mUserId = userId;
        mAccepted = savedInstanceState != null && savedInstanceState.getBoolean(KEY_ACCEPTED);
    }

    /** Whether the user agreed on the risk screen. Passed to the save worker. */
    public boolean isAccepted() {
        return mAccepted;
    }

    /** The user tapped "I understand". */
    public void onAccepted() {
        mAccepted = true;
    }

    /**
     * Whether the risk screen still has to be shown before a lock that is weaker by its kind,
     * a PIN the user picks or a pattern, replaces the current lock.
     */
    public boolean isNeededForWeakerLock() {
        return !mAccepted && WeakerRiskRule.riskScreenNeeded(
                StrengthClass.WEAKER, LockStrength.current(mUtils, mUserId));
    }

    /** Whether the risk screen still has to be shown before {@code credential} is saved. */
    public boolean isNeededFor(LockscreenCredential credential, boolean generated) {
        return !mAccepted && WeakerRiskRule.riskScreenNeeded(
                LockStrength.of(credential, generated), LockStrength.current(mUtils, mUserId));
    }

    /** Shows the risk screen over the fragment, worded for the kind of lock. */
    public void show(WeakerRiskDialog.Kind kind) {
        WeakerRiskDialog.show(mFragment.getChildFragmentManager(), kind);
    }

    /** Saves the answer. Nothing else of this flow belongs in a saved state. */
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_ACCEPTED, mAccepted);
    }
}

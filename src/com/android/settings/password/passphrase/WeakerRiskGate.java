/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import androidx.fragment.app.Fragment;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;

/**
 * Keeps track, for one screen that sets a lock, of whether the user has to see the risk screen
 * and whether they agreed.
 *
 * <p>The answer stays in the activity's {@link LockSetupHolder}, in memory, until the save: it
 * outlives a screen that is created anew for a rotation and nothing else. The lock settings
 * service is only told right before the lock is set, by the save worker.
 */
public final class WeakerRiskGate {

    private final Fragment mFragment;
    private final LockPatternUtils mUtils;
    private final int mUserId;
    private final LockSetupHolder mHolder;

    /**
     * @param fragment the screen; it implements {@link WeakerRiskDialog.Listener}
     * @param userId the user whose lock is set
     */
    public WeakerRiskGate(Fragment fragment, LockPatternUtils utils, int userId) {
        mFragment = fragment;
        mUtils = utils;
        mUserId = userId;
        mHolder = LockSetupHolder.of(fragment.requireActivity());
    }

    /** Whether the user agreed on the risk screen. Passed to the save worker. */
    public boolean isAccepted() {
        return mHolder.isRiskAccepted();
    }

    /** The user tapped "I understand". */
    public void onAccepted() {
        mHolder.setRiskAccepted();
    }

    /**
     * Whether the risk screen still has to be shown before a lock that is weaker by its kind,
     * a PIN the user picks or a pattern, replaces the current lock.
     */
    public boolean isNeededForWeakerLock() {
        return !isAccepted() && WeakerRiskRule.riskScreenNeeded(
                StrengthClass.WEAKER, LockStrength.current(mUtils, mUserId));
    }

    /** Whether the risk screen still has to be shown before {@code credential} is saved. */
    public boolean isNeededFor(LockscreenCredential credential, boolean generated) {
        return !isAccepted() && WeakerRiskRule.riskScreenNeeded(
                LockStrength.of(credential, generated), LockStrength.current(mUtils, mUserId));
    }

    /** Shows the risk screen over the fragment, worded for the kind of lock. */
    public void show(WeakerRiskDialog.Kind kind) {
        WeakerRiskDialog.show(mFragment.getChildFragmentManager(), kind);
    }
}

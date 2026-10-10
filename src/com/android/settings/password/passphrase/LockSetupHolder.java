/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.internal.widget.LockscreenCredential;

/**
 * Holds, in memory only, what the choice of a lock must not lose when its screen is created
 * anew for a rotation, a text size or a theme change: the generated passphrase or PIN and its
 * step, the entries of the password screen, and the user's agreement to a weaker lock.
 *
 * <p>It belongs to the activity that sets the lock. The system keeps it across such a change
 * and hands it to the new screen; it is never written to a saved state. When the activity is
 * left for good, and whenever its process is gone, there is nothing to restore: the choice
 * starts again. {@link #onCleared()} overwrites every secret in here at that point.
 */
public final class LockSetupHolder extends ViewModel {

    private boolean mRiskAccepted;
    @Nullable private GeneratedSecret mGenerated;
    @Nullable private KeptEntry<LockscreenCredential> mEntry;

    /** Made by the system, through {@link #of}. */
    public LockSetupHolder() {}

    /** The holder of the activity that sets the lock; the same one after a recreation. */
    public static LockSetupHolder of(FragmentActivity activity) {
        return new ViewModelProvider(activity).get(LockSetupHolder.class);
    }

    /** Whether the user agreed on the risk screen. */
    public boolean isRiskAccepted() {
        return mRiskAccepted;
    }

    /** The user tapped "I understand". */
    public void setRiskAccepted() {
        mRiskAccepted = true;
    }

    /** The generated passphrase or PIN of this activity. Made on first use. */
    GeneratedSecret generatedSecret(boolean isPassphrase, Context context) {
        if (mGenerated == null || mGenerated.isPassphrase() != isPassphrase) {
            if (mGenerated != null) {
                mGenerated.wipe();
            }
            mGenerated = new GeneratedSecret(isPassphrase, context);
        }
        return mGenerated;
    }

    /** The password screen is being created anew: this is what it had in hand. */
    public void keepEntry(KeptEntry<LockscreenCredential> entry) {
        if (mEntry != null) {
            mEntry.wipe();
        }
        mEntry = entry;
    }

    /**
     * What the screen before this one had in hand. Given out once.
     *
     * @return null if there was no such screen in this process, or it kept nothing
     */
    @Nullable
    public KeptEntry<LockscreenCredential> takeEntry() {
        final KeptEntry<LockscreenCredential> entry = mEntry;
        mEntry = null;
        return entry;
    }

    /** The choice of a lock is over or given up: every secret in here is overwritten. */
    public void wipe() {
        if (mEntry != null) {
            mEntry.wipe();
            mEntry = null;
        }
        if (mGenerated != null) {
            mGenerated.wipe();
            mGenerated = null;
        }
        mRiskAccepted = false;
    }

    @Override
    protected void onCleared() {
        wipe();
    }
}

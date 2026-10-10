/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * What the password screen had in hand when it was destroyed to be created anew, for a rotation
 * or a similar change: the step, the first entry, the entry that waits for an answer or is being
 * saved, and what was typed in the field.
 *
 * <p>It is held in memory between the two screens and nowhere else: it cannot be parcelled, and
 * {@link #toString()} is not overridden to show anything of it. The screen that takes it owns
 * the entries from then on. If no screen takes it, {@link #wipe()} overwrites them.
 *
 * @param <C> the kind of credential; overwritten by the wiper given to the constructor
 */
public final class KeptEntry<C> {

    private final Consumer<C> mWiper;
    private final String mStage;
    private final boolean mChosenIsBeingSaved;
    private final boolean mRiskAskedForFirstEntry;
    private final boolean mSaveRefused;
    private final boolean mSavingStrongLock;
    private C mFirst;
    private C mChosen;
    private char[] mTyped;

    /**
     * @param wiper overwrites a credential
     * @param stage name of the step the screen was on
     * @param first the first entry, or the generated secret, to compare the next entry with;
     *     may be null
     * @param chosen the entry that waits for the user's answer about its risk, or that is being
     *     saved; may be null, and may be the same object as {@code first}
     * @param chosenIsBeingSaved a save that is running uses {@code chosen}: it is then never
     *     overwritten here
     * @param typed a copy of the characters in the entry field; owned by this object
     */
    public KeptEntry(Consumer<C> wiper, String stage, C first, C chosen,
            boolean chosenIsBeingSaved, char[] typed, boolean riskAskedForFirstEntry,
            boolean saveRefused, boolean savingStrongLock) {
        mWiper = wiper;
        mStage = stage;
        mFirst = first;
        mChosen = chosen;
        mChosenIsBeingSaved = chosenIsBeingSaved;
        mTyped = typed;
        mRiskAskedForFirstEntry = riskAskedForFirstEntry;
        mSaveRefused = saveRefused;
        mSavingStrongLock = savingStrongLock;
    }

    /** Name of the step the screen was on. */
    public String stage() {
        return mStage;
    }

    /** The first entry or the generated secret; null if there was none. */
    public C first() {
        return mFirst;
    }

    /** The entry that waits for an answer or is being saved; null if there was none. */
    public C chosen() {
        return mChosen;
    }

    /** The characters that were in the entry field. Empty once they are wiped. */
    public char[] typed() {
        return mTyped;
    }

    /** Whether the risk dialog was up for a first entry. */
    public boolean riskAskedForFirstEntry() {
        return mRiskAskedForFirstEntry;
    }

    /** Whether the save waits for the user's agreement to the risk. */
    public boolean saveRefused() {
        return mSaveRefused;
    }

    /** Whether the lock that is being saved counts as strong. */
    public boolean savingStrongLock() {
        return mSavingStrongLock;
    }

    /**
     * The new screen has the entries now and has put the typed characters in its field: the
     * copy of those characters is overwritten, and the entries are let go of, not overwritten.
     */
    public void handedOver() {
        mFirst = null;
        mChosen = null;
        wipeTyped();
    }

    /** No screen took it: everything is overwritten, except an entry that is being saved. */
    public void wipe() {
        // A save that is running holds its entry and reads it on another thread.
        final boolean firstIsBeingSaved = mChosenIsBeingSaved && mFirst == mChosen;
        if (mFirst != null && !firstIsBeingSaved) {
            mWiper.accept(mFirst);
        }
        if (mChosen != null && mChosen != mFirst && !mChosenIsBeingSaved) {
            mWiper.accept(mChosen);
        }
        mFirst = null;
        mChosen = null;
        wipeTyped();
    }

    private void wipeTyped() {
        Arrays.fill(mTyped, '\0');
        mTyped = new char[0];
    }
}

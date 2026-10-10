/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

/**
 * Where the user is in setting up a generated passphrase or PIN, and whether it is on screen.
 *
 * <p>The steps: the secret is shown on request and copied to paper; it is typed back; it is
 * typed once more for practice; then it is saved. The secret itself is not in here.
 *
 * <p>It also knows when a shown secret has been on screen long enough. That time runs on, and
 * does not start again, when the screen is created anew while the secret is shown.
 */
public final class GeneratedSetupState {

    /** The steps of the flow. */
    public enum Step {
        /** The secret can be shown. It is hidden until the user asks for it. */
        SHOW,
        /** The secret is hidden and has to be typed back. */
        TYPE_BACK,
        /** Typed back correctly once. It has to be typed once more, for practice. */
        PRACTISE,
    }

    private Step mStep = Step.SHOW;
    private boolean mHasSecret;
    private boolean mRevealed;
    private boolean mSeen;
    // When the shown secret leaves the screen by itself, on the clock reveal() was given.
    private long mHideAtMillis;

    /** The current step. */
    public Step step() {
        return mStep;
    }

    /** Whether the secret is on screen now. Only ever true in {@link Step#SHOW}. */
    public boolean isRevealed() {
        return mRevealed;
    }

    /**
     * Whether the user may go on to type the secret back: there is one, and it was shown at
     * least once. Nobody can type back what they have not seen.
     */
    public boolean canContinue() {
        return mStep == Step.SHOW && mHasSecret && mSeen;
    }

    /** A new secret was generated. It starts hidden and unseen, and the flow starts again. */
    public void onGenerated() {
        mStep = Step.SHOW;
        mHasSecret = true;
        mRevealed = false;
        mSeen = false;
    }

    /** There is no secret (it could not be generated, or it was wiped). */
    public void onSecretGone() {
        mStep = Step.SHOW;
        mHasSecret = false;
        mRevealed = false;
        mSeen = false;
    }

    /**
     * The user asked to see the secret.
     *
     * @param nowMillis the time now, on any clock that only runs forward
     * @param forMillis how long the secret may stay on screen
     * @return whether it is shown now
     */
    public boolean reveal(long nowMillis, long forMillis) {
        if (mStep != Step.SHOW || !mHasSecret) {
            return false;
        }
        mRevealed = true;
        mSeen = true;
        mHideAtMillis = nowMillis + forMillis;
        return true;
    }

    /**
     * How much longer the secret may stay on screen: 0 when it is hidden or its time is up.
     *
     * @param nowMillis the time now, on the clock {@link #reveal} was given
     */
    public long revealMillisLeft(long nowMillis) {
        return mRevealed ? Math.max(0, mHideAtMillis - nowMillis) : 0;
    }

    /** The secret leaves the screen: the user hid it, the screen was left, or time ran out. */
    public void hide() {
        mRevealed = false;
    }

    /**
     * The user goes on to type the secret back. It is hidden first.
     *
     * @return whether the step changed
     */
    public boolean continueToTypeBack() {
        if (!canContinue()) {
            return false;
        }
        mRevealed = false;
        mStep = Step.TYPE_BACK;
        return true;
    }

    /**
     * The secret was typed correctly.
     *
     * @return true when it may be saved now: it was typed back and typed once more
     */
    public boolean onTypedCorrectly() {
        if (mStep == Step.TYPE_BACK) {
            mStep = Step.PRACTISE;
            return false;
        }
        return mStep == Step.PRACTISE;
    }

    /** The user wants to look at the secret again. It stays hidden until asked for. */
    public void backToShow() {
        mStep = Step.SHOW;
        mRevealed = false;
    }
}

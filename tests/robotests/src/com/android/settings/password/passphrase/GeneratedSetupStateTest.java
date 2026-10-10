/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.settings.password.passphrase.GeneratedSetupState.Step;

import org.junit.Test;

public class GeneratedSetupStateTest {

    private static GeneratedSetupState shown() {
        final GeneratedSetupState state = new GeneratedSetupState();
        state.onGenerated();
        state.reveal();
        return state;
    }

    @Test
    public void start_nothingToShowOrToContinueWith() {
        final GeneratedSetupState state = new GeneratedSetupState();

        assertEquals(Step.SHOW, state.step());
        assertFalse(state.isRevealed());
        assertFalse(state.canContinue());
        assertFalse(state.reveal());
        assertFalse(state.isRevealed());
        assertFalse(state.continueToTypeBack());
    }

    @Test
    public void generated_isHiddenUntilAskedFor() {
        final GeneratedSetupState state = new GeneratedSetupState();
        state.onGenerated();

        assertFalse(state.isRevealed());
        // Nobody can type back what was never on screen.
        assertFalse(state.canContinue());
        assertFalse(state.continueToTypeBack());
        assertEquals(Step.SHOW, state.step());

        assertTrue(state.reveal());
        assertTrue(state.isRevealed());
        assertTrue(state.canContinue());
    }

    @Test
    public void hide_takesItOffTheScreenButItCountsAsSeen() {
        final GeneratedSetupState state = shown();

        state.hide();

        assertFalse(state.isRevealed());
        assertTrue(state.canContinue());
    }

    @Test
    public void continue_hidesTheSecretAndAsksForItBack() {
        final GeneratedSetupState state = shown();

        assertTrue(state.continueToTypeBack());

        assertEquals(Step.TYPE_BACK, state.step());
        assertFalse(state.isRevealed());
        // It cannot be shown while it is being typed back.
        assertFalse(state.reveal());
        assertFalse(state.isRevealed());
    }

    @Test
    public void typedCorrectly_onceIsNotEnough_twiceIs() {
        final GeneratedSetupState state = shown();
        state.continueToTypeBack();

        assertFalse(state.onTypedCorrectly());
        assertEquals(Step.PRACTISE, state.step());
        assertFalse(state.reveal());

        assertTrue(state.onTypedCorrectly());
        assertEquals(Step.PRACTISE, state.step());
    }

    @Test
    public void typedCorrectly_whileShowing_savesNothing() {
        final GeneratedSetupState state = shown();

        assertFalse(state.onTypedCorrectly());
        assertEquals(Step.SHOW, state.step());
    }

    @Test
    public void backToShow_startsTheTypingAgainAndStaysHidden() {
        final GeneratedSetupState state = shown();
        state.continueToTypeBack();
        state.onTypedCorrectly();

        state.backToShow();

        assertEquals(Step.SHOW, state.step());
        assertFalse(state.isRevealed());
        assertTrue(state.canContinue());
        // Both entries are needed again.
        state.continueToTypeBack();
        assertFalse(state.onTypedCorrectly());
        assertTrue(state.onTypedCorrectly());
    }

    @Test
    public void generateAnother_startsOverUnseen() {
        final GeneratedSetupState state = shown();
        state.continueToTypeBack();

        state.onGenerated();

        assertEquals(Step.SHOW, state.step());
        assertFalse(state.isRevealed());
        assertFalse(state.canContinue());
    }

    @Test
    public void secretGone_nothingToShow() {
        final GeneratedSetupState state = shown();

        state.onSecretGone();

        assertFalse(state.isRevealed());
        assertFalse(state.canContinue());
        assertFalse(state.reveal());
    }
}

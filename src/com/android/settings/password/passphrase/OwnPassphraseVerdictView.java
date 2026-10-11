/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.android.settings.R;
import com.android.settings.password.passphrase.OwnPassphraseFeedback.Verdict;

/**
 * The line under the entry field that says, while the user types a passphrase or password of
 * their own, what the phone makes of it: strong, or weaker and why. Under it, a small link to
 * what that verdict can and cannot tell.
 *
 * <p>Weaker is shown in a calm colour, not as an error: a weaker password is allowed.
 *
 * <p>It only ever gets a {@link Verdict}, never the entry itself.
 */
public final class OwnPassphraseVerdictView {

    private static final String TAG_HOW = "tally_verdict_how";

    private final Context mContext;
    private final View mRoot;
    private final TextView mVerdict;
    @Nullable private Verdict mShown;

    /**
     * Adds the line to {@code entryContainer}, right after the view that holds
     * {@code entryField}.
     *
     * @param dialogs where the "How is this judged?" dialog is shown
     */
    public OwnPassphraseVerdictView(LayoutInflater inflater, ViewGroup entryContainer,
            View entryField, FragmentManager dialogs) {
        mContext = entryContainer.getContext();
        mRoot = inflater.inflate(R.layout.tally_own_passphrase_verdict, entryContainer, false);
        // The field itself, or the layout around it that is a child of the container.
        int index = entryContainer.getChildCount();
        for (int i = 0; i < entryContainer.getChildCount(); i++) {
            final View child = entryContainer.getChildAt(i);
            if (child == entryField || child.findViewById(entryField.getId()) != null) {
                index = i + 1;
                break;
            }
        }
        entryContainer.addView(mRoot, index);
        mVerdict = mRoot.findViewById(R.id.tally_verdict);
        mRoot.findViewById(R.id.tally_verdict_how).setOnClickListener(v ->
                InfoDialog.show(dialogs, TAG_HOW, R.string.tally_verdict_how,
                        mContext.getString(R.string.tally_verdict_how_message)));
    }

    /** Shows the verdict, or hides the line and its link when {@code verdict} is null. */
    public void show(@Nullable Verdict verdict) {
        if (verdict == null) {
            mRoot.setVisibility(View.GONE);
            mShown = null;
            return;
        }
        mRoot.setVisibility(View.VISIBLE);
        if (verdict == mShown) {
            // Not set again: a screen reader would read it out on every key.
            return;
        }
        mShown = verdict;
        mVerdict.setText(text(verdict));
        mVerdict.setTextColor(mContext.getColor(color(verdict)));
    }

    private String text(Verdict verdict) {
        switch (verdict) {
            case EMPTY:
                return mContext.getString(R.string.tally_verdict_empty,
                        PassphraseFloor.MIN_LENGTH);
            case WEAKER_TOO_SHORT:
                return mContext.getString(R.string.tally_verdict_too_short,
                        PassphraseFloor.MIN_LENGTH);
            case WEAKER_DIGITS_ONLY:
                return mContext.getString(R.string.tally_verdict_digits_only);
            case WEAKER_FEW_CHARACTERS:
                return mContext.getString(R.string.tally_verdict_few_characters);
            case STRONG:
                return mContext.getString(R.string.tally_verdict_strong);
            case STRONG_LOOKS_GUESSABLE:
                return mContext.getString(R.string.tally_verdict_guessable);
            default:
                return mContext.getString(R.string.tally_verdict_weaker);
        }
    }

    // Quiet while nothing is typed; a positive colour for strong; a calm warning colour for
    // everything that is weaker or looks easy to guess.
    private static int color(Verdict verdict) {
        switch (verdict) {
            case EMPTY:
                return R.color.tally_passphrase_verdict_neutral;
            case STRONG:
                return R.color.tally_passphrase_verdict_strong;
            default:
                return R.color.tally_passphrase_verdict_weaker;
        }
    }
}

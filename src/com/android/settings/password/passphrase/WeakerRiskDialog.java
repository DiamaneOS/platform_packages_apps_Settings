/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.password.passphrase.StrengthComparison.Choice;

/**
 * Tells the user, in plain words, what a PIN, a pattern or a short password is worth on this
 * phone, and asks for an explicit "I understand" before one is set.
 *
 * <p>It is shown from a fragment's child fragment manager. That fragment implements
 * {@link Listener}. Agreeing records nothing by itself: the fragment carries the answer to the
 * save, where the lock settings service is told right before the lock is set.
 */
public class WeakerRiskDialog extends InstrumentedDialogFragment {

    /** Implemented by the fragment that shows the dialog. */
    public interface Listener {
        /** The user tapped "I understand". */
        void onWeakerRiskAccepted();

        /** The user went back without agreeing. */
        void onWeakerRiskDeclined();
    }

    private static final String TAG_DIALOG = "tally_weaker_risk";

    /** Shows the dialog, unless it is already there. */
    public static void show(FragmentManager childFragmentManager) {
        if (childFragmentManager.findFragmentByTag(TAG_DIALOG) == null) {
            new WeakerRiskDialog().show(childFragmentManager, TAG_DIALOG);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final String message = getString(R.string.tally_weaker_risk_message)
                + "\n\n"
                + LockStrengthText.comparison(requireContext(), Choice.PIN_6_DIGITS,
                        Choice.PATTERN, Choice.WORDS_6)
                + "\n\n"
                + getString(R.string.tally_weaker_risk_again);
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.tally_weaker_risk_title)
                .setMessage(message)
                .setPositiveButton(R.string.tally_weaker_risk_accept, (dialog, which) -> {
                    final Listener listener = listener();
                    if (listener != null) {
                        listener.onWeakerRiskAccepted();
                    }
                })
                .setNegativeButton(R.string.tally_weaker_risk_decline,
                        (dialog, which) -> declined())
                .create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        // Back, or a tap outside: not an agreement.
        declined();
    }

    private void declined() {
        final Listener listener = listener();
        if (listener != null) {
            listener.onWeakerRiskDeclined();
        }
    }

    @Nullable
    private Listener listener() {
        final Fragment parent = getParentFragment();
        return parent instanceof Listener ? (Listener) parent : null;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }
}

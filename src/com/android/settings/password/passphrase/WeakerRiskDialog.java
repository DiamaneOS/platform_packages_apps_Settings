/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;

/**
 * Tells the user, in plain words, what a PIN, a pattern or a short password is worth on this
 * phone, and asks for an explicit "I understand" before one is set.
 *
 * <p>Two buttons, one under the other at full width. The recommended way out is the filled one
 * (a passphrase instead, or a longer password). Agreeing is a plain text button under it.
 *
 * <p>It is shown from a fragment's child fragment manager. That fragment implements
 * {@link Listener}. Agreeing records nothing by itself: the fragment carries the answer to the
 * save, where the lock settings service is told right before the lock is set.
 */
public class WeakerRiskDialog extends InstrumentedDialogFragment {

    /** The kind of weaker lock the warning is about. It decides the words. */
    public enum Kind {
        /** A PIN the user picks. Not agreeing leads to a passphrase. */
        PIN,
        /** A pattern. Not agreeing goes back. */
        PATTERN,
        /** A password under the shape of a strong passphrase. Not agreeing returns to it. */
        PASSWORD,
    }

    /** Implemented by the fragment that shows the dialog. */
    public interface Listener {
        /** The user tapped the button that starts with "I understand". */
        void onWeakerRiskAccepted();

        /** The user did not agree: the other button, Back, or a tap outside. */
        void onWeakerRiskDeclined();
    }

    private static final String TAG_DIALOG = "tally_weaker_risk";
    private static final String ARG_KIND = "kind";

    /** Shows the dialog, unless it is already there. */
    public static void show(FragmentManager childFragmentManager, Kind kind) {
        if (childFragmentManager.findFragmentByTag(TAG_DIALOG) != null) {
            return;
        }
        final Bundle args = new Bundle();
        args.putString(ARG_KIND, kind.name());
        final WeakerRiskDialog dialog = new WeakerRiskDialog();
        dialog.setArguments(args);
        dialog.show(childFragmentManager, TAG_DIALOG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final int title;
        final int message;
        final int accept;
        final int decline;
        switch (Kind.valueOf(requireArguments().getString(ARG_KIND, Kind.PIN.name()))) {
            case PATTERN:
                title = R.string.tally_weaker_risk_title_pattern;
                message = R.string.tally_weaker_risk_message_pattern;
                accept = R.string.tally_weaker_risk_accept_pattern;
                decline = R.string.tally_weaker_risk_decline_pattern;
                break;
            case PASSWORD:
                title = R.string.tally_weaker_risk_title_password;
                message = R.string.tally_weaker_risk_message_password;
                accept = R.string.tally_weaker_risk_accept_password;
                decline = R.string.tally_weaker_risk_decline_password;
                break;
            default:
                title = R.string.tally_weaker_risk_title_pin;
                message = R.string.tally_weaker_risk_message_pin;
                accept = R.string.tally_weaker_risk_accept_pin;
                decline = R.string.tally_weaker_risk_decline_pin;
                break;
        }
        // Not the fragment's own inflater: asking for that one creates the dialog.
        final View buttons = LayoutInflater.from(requireActivity())
                .inflate(R.layout.tally_weaker_risk_buttons, null);
        final Button declineButton = buttons.findViewById(R.id.tally_weaker_risk_decline);
        declineButton.setText(decline);
        declineButton.setOnClickListener(v -> {
            final Listener listener = listener();
            dismiss();
            if (listener != null) {
                listener.onWeakerRiskDeclined();
            }
        });
        final Button acceptButton = buttons.findViewById(R.id.tally_weaker_risk_accept);
        acceptButton.setText(accept);
        acceptButton.setOnClickListener(v -> {
            final Listener listener = listener();
            dismiss();
            if (listener != null) {
                listener.onWeakerRiskAccepted();
            }
        });
        return new AlertDialog.Builder(requireActivity())
                .setTitle(title)
                .setMessage(message)
                .setView(buttons)
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

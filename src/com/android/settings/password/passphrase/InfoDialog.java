/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.android.settings.core.instrumentation.InstrumentedDialogFragment;

/**
 * A title, some text and OK: the details that the lock setup screens keep off the screen itself
 * (how a time to guess is estimated, how a typed passphrase is judged). Never for a secret: the
 * text is kept in the fragment's arguments.
 */
public class InfoDialog extends InstrumentedDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";

    /** Shows the dialog, unless one with the same tag is already there. */
    public static void show(FragmentManager manager, String tag, int titleRes, String message) {
        if (manager.findFragmentByTag(tag) != null) {
            return;
        }
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE, titleRes);
        args.putString(ARG_MESSAGE, message);
        final InfoDialog dialog = new InfoDialog();
        dialog.setArguments(args);
        dialog.show(manager, tag);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Bundle args = requireArguments();
        return new AlertDialog.Builder(requireActivity())
                .setTitle(args.getInt(ARG_TITLE))
                .setMessage(args.getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }
}

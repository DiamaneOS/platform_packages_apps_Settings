/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.android.settings.R;

/**
 * The two buttons of a dialog that asks before a weaker or riskier lock is chosen: one under
 * the other at full width, the safe choice filled, the other a plain text button.
 *
 * <p>The same rule for every such dialog: the filled button never does the risky thing.
 */
public final class StackedDialogButtons {

    private StackedDialogButtons() {}

    /**
     * Makes the two buttons, to be set as the view of an alert dialog that has no buttons of
     * its own. The buttons do not close the dialog: {@code onSafe} and {@code onOther} do.
     *
     * @param context the dialog's context. Not a dialog fragment's own layout inflater:
     *     asking for that one while the dialog is created creates the dialog.
     * @param safeText label of the filled button
     * @param otherText label of the text button under it
     */
    public static View create(Context context, int safeText, Runnable onSafe, int otherText,
            Runnable onOther) {
        final View buttons = LayoutInflater.from(context)
                .inflate(R.layout.tally_stacked_dialog_buttons, null);
        final Button safe = buttons.findViewById(R.id.tally_dialog_safe_button);
        safe.setText(safeText);
        safe.setOnClickListener(v -> onSafe.run());
        final Button other = buttons.findViewById(R.id.tally_dialog_other_button);
        other.setText(otherText);
        other.setOnClickListener(v -> onOther.run());
        return buttons;
    }
}

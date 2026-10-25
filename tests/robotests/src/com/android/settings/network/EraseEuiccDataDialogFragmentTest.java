/*
 * Copyright (C) 2026 The DiamaneOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.network;

import static com.google.common.truth.Truth.assertThat;

import android.app.Dialog;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.android.settings.R;
import com.android.settings.testutils.shadow.ShadowRecoverySystem;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowRecoverySystem.class)
public class EraseEuiccDataDialogFragmentTest {

    @After
    public void tearDown() {
        ShadowRecoverySystem.reset();
    }

    private static Dialog erase(boolean wiped) {
        ShadowRecoverySystem.setWipeEuiccResult(wiped);
        FragmentActivity activity = Robolectric.setupActivity(FragmentActivity.class);
        EraseEuiccDataDialogFragment.newWipe(activity).run();
        ShadowLooper.idleMainLooper();
        assertThat(ShadowRecoverySystem.getWipeEuiccCalledCount()).isEqualTo(1);
        return ShadowDialog.getLatestDialog();
    }

    @Test
    public void failedErase_saysSo() {
        Dialog dialog = erase(false);

        assertThat(dialog.isShowing()).isTrue();
        TextView message = dialog.findViewById(android.R.id.message);
        assertThat(message.getText().toString())
                .isEqualTo(dialog.getContext().getString(R.string.reset_esim_error_msg));
    }

    @Test
    public void failedErase_afterSettingsClosed_saysSoInAToast() {
        ShadowRecoverySystem.setWipeEuiccResult(false);
        FragmentActivity activity = Robolectric.setupActivity(FragmentActivity.class);
        Runnable wipe = EraseEuiccDataDialogFragment.newWipe(activity);
        activity.finish();
        wipe.run();
        ShadowLooper.idleMainLooper();

        assertThat(ShadowDialog.getLatestDialog()).isNull();
        assertThat(ShadowToast.getTextOfLatestToast().toString())
                .isEqualTo(activity.getString(R.string.reset_esim_error_msg));
    }

    @Test
    public void erase_showsNothing() {
        assertThat(erase(true)).isNull();
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.InputStream;

/** The word list as the app finds it: in its assets. The other passphrase tests need no app. */
@RunWith(RobolectricTestRunner.class)
public class WordListAssetTest {

    @Test
    public void effLargeAsset_isThePinnedList() throws Exception {
        final Context context = ApplicationProvider.getApplicationContext();

        try (InputStream in = context.getAssets().open(WordList.EFF_LARGE_ASSET)) {
            assertThat(WordList.loadEffLarge(in).size()).isEqualTo(WordList.EFF_LARGE_SIZE);
        }
    }
}

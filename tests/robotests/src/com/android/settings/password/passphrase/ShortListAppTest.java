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
import java.nio.charset.StandardCharsets;

/** The short list as the app finds it in its assets, its strength text and its notice. */
@RunWith(RobolectricTestRunner.class)
public class ShortListAppTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void asset_isThePinnedList() throws Exception {
        try (InputStream in = mContext.getAssets().open(WordList.EFF_SHORT_ASSET)) {
            assertThat(WordList.loadEffShort(in).size()).isEqualTo(WordList.EFF_SHORT_SIZE);
        }
    }

    @Test
    public void strengthText_countsTenPointThreeBitsAWord() {
        assertThat(LockStrengthText.strengthLine(mContext, ShortListGenerator.ESTIMATE_BITS))
                .isEqualTo("Estimate: about 63,000 years to guess");
        assertThat(LockStrengthText.details(mContext))
                .contains("\n6 × 3 letters: about 63,000 years\n");
    }

    @Test
    public void notice_attributesTheShortList() throws Exception {
        final String notice = new String(PassphraseTestUtils.resource("/NOTICE"),
                StandardCharsets.UTF_8);

        assertThat(notice).contains("assets/passphrase/eff_short_wordlist_2_0.txt\n\n"
                + "   EFF Short Wordlist 2.0 for Passphrases, by the Electronic Frontier\n"
                + "   Foundation (Joseph Bonneau), 2016.");
        assertThat(notice).contains(
                "https://www.eff.org/files/2016/09/08/eff_short_wordlist_2_0.txt");
        assertThat(notice.substring(notice.indexOf("eff_short_wordlist_2_0.txt")))
                .contains("Creative Commons Attribution 3.0 United States");
    }
}

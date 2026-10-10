/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Shared by the passphrase tests. None of them needs Android. */
final class PassphraseTestUtils {

    // The shipped list is put on the test class path under the same path it has in the assets.
    private static final String EFF_LARGE_RESOURCE = "/" + WordList.EFF_LARGE_ASSET;

    private PassphraseTestUtils() {}

    /** The bytes of the word list file that ships. */
    static byte[] effLargeBytes() throws IOException {
        try (InputStream in = PassphraseTestUtils.class.getResourceAsStream(EFF_LARGE_RESOURCE)) {
            assertNotNull("word list is not on the class path at " + EFF_LARGE_RESOURCE, in);
            return in.readAllBytes();
        }
    }

    /** The word list that ships, loaded the way the app loads it. */
    static WordList effLarge() throws IOException {
        return WordList.loadEffLarge(new ByteArrayInputStream(effLargeBytes()));
    }
}

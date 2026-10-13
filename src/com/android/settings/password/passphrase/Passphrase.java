/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.util.Arrays;

/**
 * A generated passphrase, held in a char array that {@link #close()} overwrites.
 *
 * <p>This is the only place the phrase lives. It cannot be parcelled or serialized, and
 * {@link #toString()} does not contain it, so it does not reach a log or a saved state by
 * accident. Whoever holds it closes it as soon as the phrase is saved or dropped.
 *
 * <p>Not safe for use from several threads at once.
 */
public final class Passphrase implements AutoCloseable {

    private final char[] mChars;
    private final int mWordCount;
    private boolean mClosed;

    Passphrase(char[] chars, int wordCount) {
        mChars = chars;
        mWordCount = wordCount;
    }

    /**
     * The characters of the phrase. This is the array itself, not a copy: do not keep it past
     * {@link #close()}, do not copy it into a String, and wipe every copy you do make.
     *
     * @throws IllegalStateException after {@link #close()}
     */
    public char[] chars() {
        if (mClosed) {
            throw new IllegalStateException("passphrase was closed");
        }
        return mChars;
    }

    /** Number of characters, separators included. */
    public int length() {
        return mChars.length;
    }

    /** Number of words. */
    public int wordCount() {
        return mWordCount;
    }

    /** Whether {@link #close()} was called. */
    public boolean isClosed() {
        return mClosed;
    }

    /** Overwrites the phrase with zeros. Safe to call more than once. */
    @Override
    public void close() {
        Arrays.fill(mChars, '\0');
        mClosed = true;
    }

    /** Never contains the phrase. */
    @Override
    public String toString() {
        return "Passphrase(" + mWordCount + " words)";
    }
}

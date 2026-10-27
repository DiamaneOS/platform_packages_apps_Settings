/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.internal.widget.LockCredentialPolicy;
import com.android.internal.widget.LockscreenCredential;
import com.android.settingslib.utils.ThreadUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.security.SecureRandom;

/**
 * The passphrase or PIN the phone generated for a lock that is being set up, the number of
 * words or the short list chosen for it, and the step the user is on.
 *
 * <p>It lives in the {@link LockSetupHolder}, in memory only, so that a screen that is created
 * anew for a rotation shows the same secret on the same step. The screen that shows it attaches
 * itself and is told when something changes. Nothing of the secret is logged, put in a String,
 * or saved, and {@link #wipe()} overwrites it.
 */
final class GeneratedSecret {

    /** The screen that shows the secret at the moment. */
    interface Screen {
        /** Whether the rules set for this device accept the credential. */
        boolean accepts(LockscreenCredential credential);

        /** A new random PIN from the lock settings service, which remembers it as generated. */
        LockscreenCredential newPin(int length);

        /** A new short-list password from the lock settings service, likewise. */
        LockscreenCredential newShortListPassword();

        /** The secret, the step or what can be done changed: show it. */
        void onSecretChanged();
    }

    private static final String TAG = "GeneratedSecret";

    static final int PIN_LENGTH = LockCredentialPolicy.MIN_GENERATED_PIN_LENGTH;

    // A device rule against runs of digits refuses about one random 20-digit PIN in twenty.
    private static final int MAX_PIN_TRIES = 20;

    private final boolean mIsPassphrase;
    private final Context mAppContext;
    private final GeneratedSetupState mState = new GeneratedSetupState();
    private final double[] mEntropyBits = new double[PassphraseGenerator.MAX_WORDS + 1];

    // The secret: a passphrase or a PIN, by kind. Null while there is none.
    @Nullable private Passphrase mPassphrase;
    @Nullable private LockscreenCredential mPin;
    @Nullable private LockscreenCredential mShortListPassword;
    @Nullable private PassphraseGenerator mGenerator;
    // The words a short-list password stands for, to show them.
    @Nullable private WordList mShortListWords;
    private int mWords = PassphraseGenerator.DEFAULT_WORDS;
    // Whether the passphrase is the short-list one: only the first letters of its words.
    private boolean mShortList;
    private boolean mStarted;
    private boolean mPreparing;
    private boolean mBlockedByRules;
    // A secret is due, and is made as soon as a screen is there to check it against the rules.
    private boolean mGenerateWhenShown;
    private boolean mWiped;
    @Nullable private Screen mScreen;

    /**
     * @param isPassphrase a passphrase if true, a PIN otherwise
     * @param context any context. Only the application's is kept
     */
    GeneratedSecret(boolean isPassphrase, Context context) {
        mIsPassphrase = isPassphrase;
        mAppContext = context.getApplicationContext();
    }

    /**
     * A screen shows the secret from now on. The first one starts the making of a secret. A
     * later one finds the secret and the step as the screen before it left them.
     */
    void attach(Screen screen) {
        mScreen = screen;
        if (!mStarted) {
            mStarted = true;
            if (mIsPassphrase) {
                loadWordsThenGenerate();
            } else {
                generate();
            }
        } else if (mGenerateWhenShown) {
            generate();
        }
    }

    /** The screen is going away. The secret stays, for the screen that takes its place. */
    void detach(Screen screen) {
        if (mScreen == screen) {
            mScreen = null;
        }
    }

    boolean isPassphrase() {
        return mIsPassphrase;
    }

    /** The step, and whether the secret is on screen. */
    GeneratedSetupState state() {
        return mState;
    }

    boolean hasSecret() {
        return mPassphrase != null || mPin != null || mShortListPassword != null;
    }

    /** The word list is still being read. */
    boolean isPreparing() {
        return mPreparing;
    }

    /** The rules set for this device refuse what is generated. */
    boolean isBlockedByRules() {
        return mBlockedByRules;
    }

    /** Number of words of a passphrase of whole words. */
    int words() {
        return mWords;
    }

    /** Whether the passphrase is the short-list one, typed as the first letters of its words. */
    boolean isShortList() {
        return mShortList;
    }

    /** Chooses whole words or the short list, and makes a new passphrase of that kind. */
    void setShortList(boolean shortList) {
        if (!mIsPassphrase || shortList == mShortList) {
            return;
        }
        mShortList = shortList;
        generate();
    }

    /** Chooses the number of words, and makes a new passphrase of that length. */
    void setWords(int words) {
        if (words < PassphraseGenerator.MIN_WORDS || words > PassphraseGenerator.MAX_WORDS
                || words == mWords) {
            return;
        }
        mWords = words;
        generate();
    }

    /**
     * Entropy of the secret in bits, as estimates take it: exact for whole words and a PIN, and
     * {@link ShortList#ESTIMATE_BITS} for the short list. It depends on the kind and the
     * number of words or digits only.
     */
    double entropyBits() {
        if (!mIsPassphrase) {
            return CredentialStrength.generatedPinEntropyBits(PIN_LENGTH);
        }
        return mShortList ? ShortList.ESTIMATE_BITS : mEntropyBits[mWords];
    }

    /**
     * The secret as the card shows it, numbered words in one or two columns or grouped digits.
     *
     * @return two new arrays, the second one empty for a PIN. The caller wipes them. Null if
     *     there is no secret.
     */
    @Nullable
    char[][] displayColumns(boolean twoColumns) {
        if (mPassphrase != null) {
            return SecretDisplay.wordColumns(mPassphrase.chars(), twoColumns);
        }
        if (mPin == null) {
            return null;
        }
        final char[] digits = letters(mPin);
        final char[] grouped = SecretDisplay.groupedDigits(digits);
        SecretDisplay.wipe(digits);
        return new char[][] {grouped, new char[0]};
    }

    /**
     * The words of the short-list passphrase, whose first letters are the credential.
     *
     * @return one new array for each word. The caller wipes them. Null if there is no
     *     short-list passphrase.
     */
    @Nullable
    char[][] shortListWords() {
        if (mShortListPassword == null || mShortListWords == null) {
            return null;
        }
        final char[] letters = letters(mShortListPassword);
        try {
            return SecretDisplay.shortListWords(letters, mShortListWords);
        } finally {
            SecretDisplay.wipe(letters);
        }
    }

    /**
     * The letters of the short-list passphrase in groups for reading.
     *
     * @return a new array. The caller wipes it. Null if there is no short-list passphrase.
     */
    @Nullable
    char[] groupedLetters() {
        if (mShortListPassword == null) {
            return null;
        }
        final char[] letters = letters(mShortListPassword);
        try {
            return SecretDisplay.groupedLetters(letters);
        } finally {
            SecretDisplay.wipe(letters);
        }
    }

    // The characters of a PIN or short-list password, one byte each. The caller wipes them.
    private static char[] letters(LockscreenCredential credential) {
        final byte[] bytes = credential.getCredential();
        final char[] chars = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            chars[i] = (char) bytes[i];
        }
        return chars;
    }

    /** A copy of the secret as a credential. The caller zeroizes it. Null if there is none. */
    @Nullable
    LockscreenCredential newCredential() {
        if (mPassphrase != null) {
            return LockscreenCredential.createPassword(CharBuffer.wrap(mPassphrase.chars()));
        }
        if (mShortListPassword != null) {
            return mShortListPassword.duplicate();
        }
        return mPin != null ? mPin.duplicate() : null;
    }

    /** Overwrites the secret for good. Nothing is generated after this. */
    void wipe() {
        mWiped = true;
        mScreen = null;
        wipeSecret();
    }

    private void loadWordsThenGenerate() {
        mPreparing = true;
        final Context appContext = mAppContext;
        ThreadUtils.postOnBackgroundThread(() -> {
            PassphraseGenerator loaded = null;
            final double[] bits = new double[mEntropyBits.length];
            try (InputStream in = appContext.getAssets().open(WordList.EFF_LARGE_ASSET)) {
                loaded = new PassphraseGenerator(WordList.loadEffLarge(in), new SecureRandom());
                for (int words = PassphraseGenerator.MIN_WORDS;
                        words <= PassphraseGenerator.MAX_WORDS; words++) {
                    bits[words] = loaded.entropyBits(words);
                }
            } catch (IOException | RuntimeException e) {
                // The list is missing or is not the pinned one. No fallback to another list.
                Log.e(TAG, "The word list cannot be used", e);
                loaded = null;
            }
            WordList loadedShort = null;
            try (InputStream in = appContext.getAssets().open(WordList.EFF_SHORT_ASSET)) {
                loadedShort = ShortList.drawnWords(WordList.loadEffShort(in));
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "The short word list cannot be used", e);
            }
            final PassphraseGenerator generator = loaded;
            final WordList shortListWords = loadedShort;
            ThreadUtils.postOnMainThread(() -> {
                if (mWiped) {
                    return;
                }
                mGenerator = generator;
                mShortListWords = shortListWords;
                System.arraycopy(bits, 0, mEntropyBits, 0, bits.length);
                mPreparing = false;
                generate();
            });
        });
    }

    /** Makes a new secret in place of the current one. The flow starts again with it. */
    void generate() {
        if (mWiped) {
            return;
        }
        final Screen screen = mScreen;
        if (screen == null) {
            // Nobody to ask whether the device's rules accept it. Made when a screen is back.
            mGenerateWhenShown = true;
            return;
        }
        mGenerateWhenShown = false;
        wipeSecret();
        mBlockedByRules = false;
        try {
            if (mIsPassphrase) {
                generatePassphrase(screen);
            } else {
                generatePin(screen);
            }
        } catch (RuntimeException e) {
            // The exception carries nothing of a secret: it comes from the service call or
            // from a word list that cannot meet the floor.
            Log.e(TAG, "Nothing could be generated", e);
            wipeSecret();
        }
        if (hasSecret()) {
            mState.onGenerated();
        }
        screen.onSecretChanged();
    }

    private void generatePassphrase(Screen screen) {
        if (mShortList) {
            generateShortListPassword(screen);
            return;
        }
        if (mGenerator == null) {
            return;
        }
        final Passphrase phrase = mGenerator.generate(mWords);
        boolean accepted = false;
        try (LockscreenCredential credential =
                LockscreenCredential.createPassword(CharBuffer.wrap(phrase.chars()))) {
            // The same check the lock settings service makes, and the device's own rules.
            accepted = LockStrength.of(credential, false) == StrengthClass.STRONG
                    && screen.accepts(credential);
        } finally {
            if (!accepted) {
                phrase.close();
            }
        }
        if (accepted) {
            mPassphrase = phrase;
        } else {
            // Another phrase of letters and spaces would be refused for the same reason.
            mBlockedByRules = true;
        }
    }

    private void generatePin(Screen screen) {
        for (int i = 0; i < MAX_PIN_TRIES; i++) {
            // Each call replaces the PIN the service remembers as generated for this user.
            final LockscreenCredential pin = screen.newPin(PIN_LENGTH);
            if (screen.accepts(pin)) {
                mPin = pin;
                return;
            }
            pin.zeroize();
        }
        mBlockedByRules = true;
    }

    // The service makes it and counts it as strong because it did. The words are only shown.
    private void generateShortListPassword(Screen screen) {
        if (mShortListWords == null) {
            return;
        }
        // It replaces what the service remembers as generated for this user.
        final LockscreenCredential password = screen.newShortListPassword();
        try {
            final char[] letters = letters(password);
            final boolean shown = ShortList.isShownBy(letters, mShortListWords);
            SecretDisplay.wipe(letters);
            if (!shown) {
                // The service draws from other words than this app's list: nothing to show.
                Log.e(TAG, "The short-list password is not of the shipped word list");
            } else if (LockStrength.of(password, true) == StrengthClass.STRONG
                    && screen.accepts(password)) {
                mShortListPassword = password;
            } else {
                // Every short-list password has the same shape: another would be refused too.
                mBlockedByRules = true;
            }
        } finally {
            if (mShortListPassword != password) {
                password.zeroize();
            }
        }
    }

    private void wipeSecret() {
        if (mPassphrase != null) {
            mPassphrase.close();
            mPassphrase = null;
        }
        if (mPin != null) {
            mPin.zeroize();
            mPin = null;
        }
        if (mShortListPassword != null) {
            mShortListPassword.zeroize();
            mShortListPassword = null;
        }
        mState.onSecretGone();
    }
}

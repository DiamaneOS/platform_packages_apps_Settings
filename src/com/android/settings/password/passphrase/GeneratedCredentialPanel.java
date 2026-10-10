/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.internal.widget.LockCredentialPolicy;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settings.R;
import com.android.settings.password.passphrase.GeneratedSetupState.Step;
import com.android.settings.password.passphrase.StrengthComparison.Choice;
import com.android.settingslib.utils.ThreadUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

/**
 * The part of the password screen that sets up a passphrase or PIN the phone generates: it
 * shows the secret on request, with advice and its strength, and guides the typing back.
 *
 * <p>The password screen keeps its own steps and its entry field. This panel sits above the
 * field, tells the screen when the user may go on, and hands over the credential to compare
 * the typed entries with.
 *
 * <p>Secret handling: the secret is held in a {@link Passphrase} or a {@link LockscreenCredential}
 * and in one char array for the text view. It is on screen only after "Show", it leaves the
 * screen on "Hide", when the screen is left, after {@link #REVEAL_TIMEOUT_MS}, and when the
 * typing starts, and every array is overwritten when it is no longer needed. Nothing of it is
 * logged, put in a String, or saved: after a recreation of the screen a new secret is made.
 */
public final class GeneratedCredentialPanel {

    /** What the password screen does for the panel. */
    public interface Host {
        /** Something changed that the screen's buttons or header depend on. */
        void onGeneratedCredentialChanged();

        /** The user wants to see the secret again instead of typing on. */
        void onShowGeneratedCredentialAgain();

        /** Whether the rules set for this device accept the credential. */
        boolean isGeneratedCredentialAcceptable(LockscreenCredential credential);
    }

    private static final String TAG = "GeneratedCredential";

    /** How long the secret stays on screen before it is hidden again. */
    static final long REVEAL_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);

    private static final int PIN_LENGTH = LockCredentialPolicy.MIN_GENERATED_PIN_LENGTH;

    // A device rule against runs of digits refuses about one random 20-digit PIN in twenty.
    private static final int MAX_PIN_TRIES = 20;

    private static final int[] WORD_BUTTONS = {
        R.id.tally_generated_words_5,
        R.id.tally_generated_words_6,
        R.id.tally_generated_words_7,
        R.id.tally_generated_words_8,
    };

    private final Context mContext;
    private final Host mHost;
    private final boolean mIsPassphrase;
    private final LockPatternUtils mUtils;
    private final int mUserId;
    private final GeneratedSetupState mState = new GeneratedSetupState();

    private final View mEntryContainer;
    private final View mShowSection;
    private final View mTypeSection;
    private final TextView mSecretView;
    private final TextView mStrengthView;
    private final TextView mHintView;
    private final Button mRevealButton;
    private final Button mAnotherButton;
    private final Runnable mHideWhenTimeIsUp = this::hide;

    // The secret: a passphrase or a PIN, by kind. Null while there is none.
    @Nullable private Passphrase mPassphrase;
    @Nullable private LockscreenCredential mPin;
    // What the secret view shows while the secret is revealed. Wiped when it is hidden.
    private char[] mDisplay = new char[0];

    @Nullable private PassphraseGenerator mGenerator;
    private final double[] mEntropyBits = new double[PassphraseGenerator.MAX_WORDS + 1];
    private int mWords = PassphraseGenerator.DEFAULT_WORDS;
    private boolean mPreparing;
    private boolean mBlockedByRules;
    private boolean mDestroyed;
    // The host is not called back before the constructor is done: it has no panel yet.
    private boolean mConstructed;

    /**
     * Adds the panel to the screen, above the entry field, and starts making a secret.
     *
     * @param entryContainer the view that holds the entry field; hidden while the secret is
     *     shown
     * @param isPassphrase a passphrase if true, a PIN otherwise
     * @param userId the user whose lock is set
     */
    public GeneratedCredentialPanel(LayoutInflater inflater, View entryContainer,
            boolean isPassphrase, LockPatternUtils utils, int userId, Host host) {
        mContext = entryContainer.getContext();
        mHost = host;
        mIsPassphrase = isPassphrase;
        mUtils = utils;
        mUserId = userId;
        mEntryContainer = entryContainer;

        final ViewGroup parent = (ViewGroup) entryContainer.getParent();
        final View root = inflater.inflate(R.layout.tally_generated_credential, parent, false);
        parent.addView(root, parent.indexOfChild(entryContainer));
        mShowSection = root.findViewById(R.id.tally_generated_show_section);
        mTypeSection = root.findViewById(R.id.tally_generated_type_section);
        mSecretView = root.findViewById(R.id.tally_generated_secret);
        mStrengthView = root.findViewById(R.id.tally_generated_strength);
        mHintView = root.findViewById(R.id.tally_generated_hint);
        mRevealButton = root.findViewById(R.id.tally_generated_reveal);
        mAnotherButton = root.findViewById(R.id.tally_generated_another);

        // Only accessibility services that are tools for the user get to read the secret.
        mSecretView.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        mSecretView.setSaveEnabled(false);

        final TextView advice = root.findViewById(R.id.tally_generated_advice);
        advice.setText(mContext.getString(R.string.tally_generated_advice) + "\n\n"
                + strongLockNotes(mContext, userId));

        mRevealButton.setOnClickListener(v -> {
            if (mState.isRevealed()) {
                hide();
            } else if (mState.reveal()) {
                renderSecret();
                notifyHost();
            }
        });
        mAnotherButton.setOnClickListener(v -> generate());
        root.findViewById(R.id.tally_generated_show_again)
                .setOnClickListener(v -> mHost.onShowGeneratedCredentialAgain());

        final RadioGroup wordsGroup = root.findViewById(R.id.tally_generated_words);
        final View wordsLabel = root.findViewById(R.id.tally_generated_words_label);
        if (isPassphrase) {
            final NumberFormat format = NumberFormat.getIntegerInstance();
            for (int i = 0; i < WORD_BUTTONS.length; i++) {
                final RadioButton button = root.findViewById(WORD_BUTTONS[i]);
                button.setText(format.format(PassphraseGenerator.MIN_WORDS + i));
            }
            wordsGroup.check(WORD_BUTTONS[mWords - PassphraseGenerator.MIN_WORDS]);
            wordsGroup.setOnCheckedChangeListener((group, checkedId) -> {
                for (int i = 0; i < WORD_BUTTONS.length; i++) {
                    if (WORD_BUTTONS[i] == checkedId
                            && mWords != PassphraseGenerator.MIN_WORDS + i) {
                        mWords = PassphraseGenerator.MIN_WORDS + i;
                        generate();
                    }
                }
            });
            loadGeneratorThenGenerate();
        } else {
            wordsGroup.setVisibility(View.GONE);
            wordsLabel.setVisibility(View.GONE);
            generate();
        }
        mConstructed = true;
    }

    private void notifyHost() {
        if (mConstructed && !mDestroyed) {
            mHost.onGeneratedCredentialChanged();
        }
    }

    /**
     * What a user is told whenever a strong lock is set up: there is no recovery, and the
     * phone asks for the lock daily at first.
     */
    public static String strongLockNotes(Context context, int userId) {
        final int learningDays = LearningPeriod.daysRoundedUp(
                LockCredentialPolicy.LEARNING_PERIOD_MILLIS);
        final long timeout = context.getSystemService(DevicePolicyManager.class)
                .getRequiredStrongAuthTimeout(null /* admin */, userId);
        return context.getString(R.string.tally_lock_no_recovery) + "\n\n"
                + context.getString(R.string.tally_lock_learning, learningDays,
                        (int) TimeUnit.MILLISECONDS.toHours(timeout));
    }

    /** Whether the secret is being shown or can be: the first step, before the typing. */
    public boolean isShowStep() {
        return mState.step() == Step.SHOW;
    }

    /** Whether the user may go on to type the secret back. */
    public boolean canContinue() {
        return mState.canContinue();
    }

    /**
     * Hides the secret and moves on to typing it back.
     *
     * @return a copy of the secret as a credential, to compare the entries with; the caller
     *     zeroizes it. Null if the user may not go on yet.
     */
    @Nullable
    public LockscreenCredential continueToTypeBack() {
        final LockscreenCredential credential = newCredential();
        if (credential == null || !mState.continueToTypeBack()) {
            if (credential != null) {
                credential.zeroize();
            }
            return null;
        }
        render();
        return credential;
    }

    /**
     * The entry matched the secret.
     *
     * @return true when the secret may be saved now; false when it is to be typed once more
     */
    public boolean onTypedCorrectly() {
        final boolean done = mState.onTypedCorrectly();
        render();
        return done;
    }

    /** Goes back to the step where the secret can be shown. It stays hidden until asked for. */
    public void showAgain() {
        mState.backToShow();
        render();
    }

    /** The header of the screen for the current step. */
    public String headerText(boolean entryWasWrong) {
        switch (mState.step()) {
            case SHOW:
                return mContext.getString(mIsPassphrase
                        ? R.string.tally_generated_passphrase_header
                        : R.string.tally_generated_pin_header);
            case PRACTISE:
                return mContext.getString(entryWasWrong
                        ? R.string.tally_generated_wrong_header
                        : R.string.tally_generated_practise_header);
            default:
                if (entryWasWrong) {
                    return mContext.getString(R.string.tally_generated_wrong_header);
                }
                return mContext.getString(mIsPassphrase
                        ? R.string.tally_generated_type_back_passphrase_header
                        : R.string.tally_generated_type_back_pin_header);
        }
    }

    /** The screen is no longer in front: the secret leaves the screen. */
    public void onPause() {
        hide();
    }

    /** The screen is going away: the secret is wiped. */
    public void onDestroy() {
        mDestroyed = true;
        wipeSecret();
    }

    private void loadGeneratorThenGenerate() {
        mPreparing = true;
        render();
        final Context appContext = mContext.getApplicationContext();
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
            final PassphraseGenerator generator = loaded;
            ThreadUtils.postOnMainThread(() -> {
                if (mDestroyed) {
                    return;
                }
                mGenerator = generator;
                System.arraycopy(bits, 0, mEntropyBits, 0, bits.length);
                mPreparing = false;
                generate();
            });
        });
    }

    // Makes a new secret in place of the current one.
    private void generate() {
        wipeSecret();
        mBlockedByRules = false;
        try {
            if (mIsPassphrase) {
                generatePassphrase();
            } else {
                generatePin();
            }
        } catch (RuntimeException e) {
            // The exception carries nothing of a secret: it comes from the service call or
            // from a word list that cannot meet the floor.
            Log.e(TAG, "Nothing could be generated", e);
            wipeSecret();
        }
        if (mPassphrase != null || mPin != null) {
            mState.onGenerated();
        }
        render();
        notifyHost();
    }

    private void generatePassphrase() {
        if (mGenerator == null) {
            return;
        }
        final Passphrase phrase = mGenerator.generate(mWords);
        boolean accepted = false;
        try (LockscreenCredential credential =
                LockscreenCredential.createPassword(CharBuffer.wrap(phrase.chars()))) {
            // The same check the lock settings service makes, and the device's own rules.
            accepted = LockStrength.of(credential, false) == StrengthClass.STRONG
                    && mHost.isGeneratedCredentialAcceptable(credential);
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

    private void generatePin() {
        for (int i = 0; i < MAX_PIN_TRIES; i++) {
            // Each call replaces the PIN the service remembers as generated for this user.
            final LockscreenCredential pin = mUtils.generateStrongPin(PIN_LENGTH, mUserId);
            if (mHost.isGeneratedCredentialAcceptable(pin)) {
                mPin = pin;
                return;
            }
            pin.zeroize();
        }
        mBlockedByRules = true;
    }

    @Nullable
    private LockscreenCredential newCredential() {
        if (mPassphrase != null) {
            return LockscreenCredential.createPassword(CharBuffer.wrap(mPassphrase.chars()));
        }
        return mPin != null ? mPin.duplicate() : null;
    }

    private void hide() {
        if (mDestroyed) {
            return;
        }
        final boolean wasRevealed = mState.isRevealed();
        mState.hide();
        renderSecret();
        if (wasRevealed) {
            notifyHost();
        }
    }

    private void wipeSecret() {
        mSecretView.removeCallbacks(mHideWhenTimeIsUp);
        // The view lets go of the array before it is overwritten.
        mSecretView.setText("");
        SecretDisplay.wipe(mDisplay);
        mDisplay = new char[0];
        if (mPassphrase != null) {
            mPassphrase.close();
            mPassphrase = null;
        }
        if (mPin != null) {
            mPin.zeroize();
            mPin = null;
        }
        mState.onSecretGone();
    }

    private void render() {
        final boolean showStep = mState.step() == Step.SHOW;
        mShowSection.setVisibility(showStep ? View.VISIBLE : View.GONE);
        mTypeSection.setVisibility(showStep ? View.GONE : View.VISIBLE);
        mEntryContainer.setVisibility(showStep ? View.GONE : View.VISIBLE);
        renderSecret();
        if (showStep) {
            mStrengthView.setText(strengthText());
        } else if (mState.step() == Step.PRACTISE) {
            mHintView.setText(R.string.tally_generated_practise_hint);
        } else {
            mHintView.setText(mIsPassphrase
                    ? R.string.tally_generated_type_back_passphrase_hint
                    : R.string.tally_generated_type_back_pin_hint);
        }
    }

    // Puts the secret on screen if it is revealed, and a note in its place if it is not.
    private void renderSecret() {
        mSecretView.removeCallbacks(mHideWhenTimeIsUp);
        final boolean hasSecret = mPassphrase != null || mPin != null;
        final char[] previous = mDisplay;
        if (mState.isRevealed() && hasSecret) {
            mDisplay = displayChars();
            mSecretView.setText(mDisplay, 0, mDisplay.length);
            mSecretView.postDelayed(mHideWhenTimeIsUp, REVEAL_TIMEOUT_MS);
        } else {
            mDisplay = new char[0];
            if (hasSecret) {
                mSecretView.setText(R.string.tally_generated_hidden);
            } else if (mPreparing) {
                mSecretView.setText(R.string.tally_generated_preparing);
            } else {
                mSecretView.setText(mBlockedByRules
                        ? R.string.tally_generated_blocked_by_rules
                        : R.string.tally_generated_unavailable);
            }
        }
        // The view no longer points at the previous array.
        SecretDisplay.wipe(previous);
        mRevealButton.setText(mState.isRevealed()
                ? R.string.tally_generated_hide : R.string.tally_generated_show);
        mRevealButton.setEnabled(hasSecret);
        mAnotherButton.setEnabled(hasSecret);
    }

    private char[] displayChars() {
        if (mPassphrase != null) {
            return SecretDisplay.numberedWords(mPassphrase.chars());
        }
        // A PIN is digits, one byte each.
        final byte[] bytes = mPin != null ? mPin.getCredential() : new byte[0];
        final char[] digits = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            digits[i] = (char) bytes[i];
        }
        final char[] grouped = SecretDisplay.groupedDigits(digits);
        SecretDisplay.wipe(digits);
        return grouped;
    }

    // Strength figures are not secret: they depend on the number of words or digits only.
    private String strengthText() {
        if (mPassphrase == null && mPin == null) {
            return "";
        }
        final double bits;
        final String strength;
        final Choice[] others;
        if (mIsPassphrase) {
            bits = mEntropyBits[mWords];
            strength = mContext.getString(R.string.tally_strength_passphrase, mWords,
                    LockStrengthText.bits(bits));
            others = new Choice[] {Choice.PIN_6_DIGITS, Choice.PATTERN, Choice.WORDS_5,
                    Choice.WORDS_6, Choice.WORDS_7, Choice.WORDS_8, Choice.RANDOM_PIN_20};
        } else {
            bits = CredentialStrength.generatedPinEntropyBits(PIN_LENGTH);
            strength = mContext.getString(R.string.tally_strength_pin, PIN_LENGTH,
                    LockStrengthText.bits(bits));
            others = new Choice[] {Choice.PIN_6_DIGITS, Choice.PATTERN, Choice.WORDS_5,
                    Choice.WORDS_6, Choice.RANDOM_PIN_20};
        }
        final String time = LockStrengthText.time(mContext,
                CredentialStrength.estimateTimeToGuess(
                        bits, PlaceholderGuessingAssumptions.get()));
        return strength + " " + mContext.getString(R.string.tally_strength_bits_note) + "\n\n"
                + mContext.getString(R.string.tally_strength_estimate, time) + "\n\n"
                + LockStrengthText.comparison(mContext, others);
    }
}

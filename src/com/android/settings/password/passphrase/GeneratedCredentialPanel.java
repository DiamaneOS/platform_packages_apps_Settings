/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

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
import java.util.concurrent.TimeUnit;

/**
 * The part of the password screen that sets up a passphrase or PIN the phone generates: it
 * shows the secret on request, with one strength line and two lines of advice, and guides the
 * typing back.
 *
 * <p>The password screen keeps its own steps and its entry field. This panel sits above the
 * field, tells the screen when the user may go on, and hands over the credential to compare
 * the typed entries with.
 *
 * <p>Secret handling: the secret is held in a {@link Passphrase} or a {@link LockscreenCredential}
 * and in the char arrays the text views show. It is on screen only after "Show", it leaves the
 * screen on "Hide", when the screen is left, after {@link #REVEAL_TIMEOUT_MS}, and when the
 * typing starts, and every array is overwritten when it is no longer needed. Nothing of it is
 * logged, put in a String, or saved: a screen that is created anew makes a new secret.
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
    private static final String TAG_DETAILS = "tally_strength_details";

    /** How long the secret stays on screen before it is hidden again. */
    static final long REVEAL_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);

    private static final int PIN_LENGTH = LockCredentialPolicy.MIN_GENERATED_PIN_LENGTH;

    // A device rule against runs of digits refuses about one random 20-digit PIN in twenty.
    private static final int MAX_PIN_TRIES = 20;

    // Two columns of words need this much room: beyond this text size, or on a narrower
    // screen, the words go in one column.
    private static final float MAX_FONT_SCALE_FOR_COLUMNS = 1.2f;
    private static final int MIN_WIDTH_DP_FOR_COLUMNS = 320;

    private final Context mContext;
    private final Host mHost;
    private final FragmentManager mDialogs;
    private final boolean mIsPassphrase;
    private final LockPatternUtils mUtils;
    private final int mUserId;
    private final GeneratedSetupState mState = new GeneratedSetupState();

    private final View mEntryContainer;
    private final View mShowSection;
    private final View mTypeSection;
    private final View mSecretColumns;
    private final TextView mSecretLeft;
    private final TextView mSecretRight;
    private final TextView mPlaceholder;
    private final TextView mWordsCount;
    private final TextView mStrengthView;
    private final TextView mHintView;
    private final Button mRevealButton;
    private final Button mAnotherButton;
    private final Button mFewerButton;
    private final Button mMoreButton;
    private final Runnable mHideWhenTimeIsUp = this::hide;

    // The secret: a passphrase or a PIN, by kind. Null while there is none.
    @Nullable private Passphrase mPassphrase;
    @Nullable private LockscreenCredential mPin;
    // What the two secret views show while the secret is revealed. Wiped when it is hidden.
    private char[] mDisplayLeft = new char[0];
    private char[] mDisplayRight = new char[0];

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
     * @param dialogs where the details behind the strength line are shown
     */
    public GeneratedCredentialPanel(LayoutInflater inflater, View entryContainer,
            boolean isPassphrase, LockPatternUtils utils, int userId, Host host,
            FragmentManager dialogs) {
        mContext = entryContainer.getContext();
        mHost = host;
        mDialogs = dialogs;
        mIsPassphrase = isPassphrase;
        mUtils = utils;
        mUserId = userId;
        mEntryContainer = entryContainer;

        final ViewGroup parent = (ViewGroup) entryContainer.getParent();
        final View root = inflater.inflate(R.layout.tally_generated_credential, parent, false);
        parent.addView(root, parent.indexOfChild(entryContainer));
        mShowSection = root.findViewById(R.id.tally_generated_show_section);
        mTypeSection = root.findViewById(R.id.tally_generated_type_section);
        mSecretColumns = root.findViewById(R.id.tally_generated_secret_columns);
        mSecretLeft = root.findViewById(R.id.tally_generated_secret);
        mSecretRight = root.findViewById(R.id.tally_generated_secret_right);
        mPlaceholder = root.findViewById(R.id.tally_generated_placeholder);
        mWordsCount = root.findViewById(R.id.tally_generated_words_count);
        mStrengthView = root.findViewById(R.id.tally_generated_strength);
        mHintView = root.findViewById(R.id.tally_generated_hint);
        mRevealButton = root.findViewById(R.id.tally_generated_reveal);
        mAnotherButton = root.findViewById(R.id.tally_generated_another);
        mFewerButton = root.findViewById(R.id.tally_generated_fewer);
        mMoreButton = root.findViewById(R.id.tally_generated_more);

        // Only accessibility services that are tools for the user get to read the secret.
        mSecretLeft.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        mSecretRight.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        mSecretLeft.setSaveEnabled(false);
        mSecretRight.setSaveEnabled(false);

        final TextView advice = root.findViewById(R.id.tally_generated_advice);
        advice.setText(mContext.getString(R.string.tally_generated_advice) + "\n"
                + mContext.getString(R.string.tally_lock_no_recovery));

        mRevealButton.setOnClickListener(v -> {
            if (mState.isRevealed()) {
                hide();
            } else if (mState.reveal()) {
                renderSecret();
                notifyHost();
            }
        });
        mAnotherButton.setText(isPassphrase
                ? R.string.tally_generated_another : R.string.tally_generated_another_pin);
        mAnotherButton.setOnClickListener(v -> generate());
        mStrengthView.setOnClickListener(v -> showStrengthDetails());
        root.findViewById(R.id.tally_generated_show_again)
                .setOnClickListener(v -> mHost.onShowGeneratedCredentialAgain());

        if (isPassphrase) {
            mFewerButton.setOnClickListener(v -> setWords(mWords - 1));
            mMoreButton.setOnClickListener(v -> setWords(mWords + 1));
            loadGeneratorThenGenerate();
        } else {
            mFewerButton.setVisibility(View.GONE);
            mMoreButton.setVisibility(View.GONE);
            mWordsCount.setVisibility(View.GONE);
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
     * What a user is told once when a lock of the strong class is set up: the phone asks for
     * it daily at first. A weaker lock has no such learning period.
     */
    public static String learningPeriodNote(Context context) {
        return context.getString(R.string.tally_lock_learning,
                LearningPeriod.daysRoundedUp(LockCredentialPolicy.LEARNING_PERIOD_MILLIS));
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

    /** The screen changed its size, orientation or text size without being created anew. */
    public void onConfigurationChanged() {
        if (!mDestroyed) {
            // One or two columns may fit now.
            renderSecret();
        }
    }

    /** The screen is going away: the secret is wiped. */
    public void onDestroy() {
        mDestroyed = true;
        wipeSecret();
    }

    private void setWords(int words) {
        if (words < PassphraseGenerator.MIN_WORDS || words > PassphraseGenerator.MAX_WORDS
                || words == mWords) {
            return;
        }
        mWords = words;
        generate();
    }

    private void showStrengthDetails() {
        if (mPassphrase == null && mPin == null) {
            return;
        }
        final String details = mIsPassphrase
                ? LockStrengthText.details(mContext, mEntropyBits[mWords], Choice.PIN_6_DIGITS,
                        Choice.WORDS_5, Choice.WORDS_6, Choice.WORDS_7, Choice.WORDS_8,
                        Choice.RANDOM_PIN_20)
                : LockStrengthText.details(mContext,
                        CredentialStrength.generatedPinEntropyBits(PIN_LENGTH),
                        Choice.PIN_6_DIGITS, Choice.WORDS_5, Choice.WORDS_6,
                        Choice.RANDOM_PIN_20);
        InfoDialog.show(mDialogs, TAG_DETAILS, R.string.tally_strength_details_title, details);
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
        clearSecretViews();
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

    // Takes the secret off the screen and overwrites what the views showed.
    private void clearSecretViews() {
        mSecretLeft.removeCallbacks(mHideWhenTimeIsUp);
        // The views let go of the arrays before they are overwritten.
        mSecretLeft.setText("");
        mSecretRight.setText("");
        SecretDisplay.wipe(mDisplayLeft);
        SecretDisplay.wipe(mDisplayRight);
        mDisplayLeft = new char[0];
        mDisplayRight = new char[0];
    }

    private void render() {
        final boolean showStep = mState.step() == Step.SHOW;
        mShowSection.setVisibility(showStep ? View.VISIBLE : View.GONE);
        mTypeSection.setVisibility(showStep ? View.GONE : View.VISIBLE);
        mEntryContainer.setVisibility(showStep ? View.GONE : View.VISIBLE);
        renderSecret();
        if (showStep) {
            renderStrength();
        } else if (mState.step() == Step.PRACTISE) {
            // The last step: the one place that says what the first two weeks are like.
            mHintView.setText(mContext.getString(R.string.tally_generated_practise_hint) + "\n"
                    + learningPeriodNote(mContext));
        } else {
            mHintView.setText(mIsPassphrase
                    ? R.string.tally_generated_type_back_passphrase_hint
                    : R.string.tally_generated_type_back_pin_hint);
        }
    }

    // Puts the secret on screen if it is revealed, and a note in its place if it is not.
    private void renderSecret() {
        clearSecretViews();
        final boolean hasSecret = mPassphrase != null || mPin != null;
        final boolean revealed = mState.isRevealed() && hasSecret;
        if (revealed) {
            fillDisplay();
            mSecretLeft.setText(mDisplayLeft, 0, mDisplayLeft.length);
            mSecretRight.setText(mDisplayRight, 0, mDisplayRight.length);
            mSecretRight.setVisibility(mDisplayRight.length > 0 ? View.VISIBLE : View.GONE);
            mSecretLeft.postDelayed(mHideWhenTimeIsUp, REVEAL_TIMEOUT_MS);
        } else if (hasSecret) {
            mPlaceholder.setText(R.string.tally_generated_hidden);
        } else if (mPreparing) {
            mPlaceholder.setText(R.string.tally_generated_preparing);
        } else {
            mPlaceholder.setText(mBlockedByRules
                    ? R.string.tally_generated_blocked_by_rules
                    : R.string.tally_generated_unavailable);
        }
        mSecretColumns.setVisibility(revealed ? View.VISIBLE : View.GONE);
        mPlaceholder.setVisibility(revealed ? View.GONE : View.VISIBLE);
        mRevealButton.setText(mState.isRevealed()
                ? R.string.tally_generated_hide : R.string.tally_generated_show);
        mRevealButton.setEnabled(hasSecret);
        mAnotherButton.setEnabled(hasSecret);
    }

    // Lays the secret out for the two views: words in two columns where they fit, in one
    // column otherwise; a PIN as one line of groups.
    private void fillDisplay() {
        if (mPassphrase != null) {
            final Configuration config = mContext.getResources().getConfiguration();
            if (config.fontScale <= MAX_FONT_SCALE_FOR_COLUMNS
                    && config.screenWidthDp >= MIN_WIDTH_DP_FOR_COLUMNS) {
                final char[][] columns = SecretDisplay.numberedWordColumns(mPassphrase.chars());
                mDisplayLeft = columns[0];
                mDisplayRight = columns[1];
            } else {
                mDisplayLeft = SecretDisplay.numberedWords(mPassphrase.chars());
            }
        } else if (mPin != null) {
            // A PIN is digits, one byte each.
            final byte[] bytes = mPin.getCredential();
            final char[] digits = new char[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                digits[i] = (char) bytes[i];
            }
            mDisplayLeft = SecretDisplay.groupedDigits(digits);
            SecretDisplay.wipe(digits);
        }
    }

    // Strength figures are not secret: they depend on the number of words or digits only.
    private void renderStrength() {
        final boolean hasSecret = mPassphrase != null || mPin != null;
        mStrengthView.setVisibility(hasSecret ? View.VISIBLE : View.INVISIBLE);
        if (mIsPassphrase) {
            mWordsCount.setText(
                    mContext.getString(R.string.tally_generated_words_count, mWords));
            mFewerButton.setEnabled(hasSecret && mWords > PassphraseGenerator.MIN_WORDS);
            mMoreButton.setEnabled(hasSecret && mWords < PassphraseGenerator.MAX_WORDS);
        }
        if (!hasSecret) {
            return;
        }
        mStrengthView.setText(mIsPassphrase
                ? LockStrengthText.strengthLine(mContext, true, mWords, mEntropyBits[mWords])
                : LockStrengthText.strengthLine(mContext, false, PIN_LENGTH,
                        CredentialStrength.generatedPinEntropyBits(PIN_LENGTH)));
    }
}

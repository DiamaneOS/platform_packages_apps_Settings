/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.android.internal.widget.LockCredentialPolicy;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settings.R;
import com.android.settings.password.passphrase.GeneratedSetupState.Step;
import com.android.settings.password.passphrase.StrengthComparison.Choice;

import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

/**
 * The part of the password screen that sets up a passphrase or PIN the phone generates: a card
 * that shows the secret on request, a choice of how many words, one strength row and two
 * paragraphs of advice; then it guides the typing back.
 *
 * <p>The password screen keeps its own steps and its entry field. This panel sits above the
 * field, tells the screen when the user may go on, and hands over the credential to compare
 * the typed entries with.
 *
 * <p>The secret and the step are not in here but in a {@link GeneratedSecret}, which the
 * {@link LockSetupHolder} keeps in memory: a screen that is created anew for a rotation gets a
 * new panel that shows the same secret on the same step, still shown if it was shown.
 *
 * <p>Secret handling: the panel only has the char arrays its text views show. The secret is on
 * screen only after "Show"; it leaves the screen on "Hide", when the screen is left, after
 * {@link #REVEAL_TIMEOUT_MS} in all, and when the typing starts, and the arrays are overwritten
 * then. Nothing of it is logged, put in a String, or saved.
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

    private static final String TAG_DETAILS = "tally_strength_details";

    /** How long the secret stays on screen before it is hidden again. */
    static final long REVEAL_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);

    private static final int PIN_LENGTH = GeneratedSecret.PIN_LENGTH;

    // Two columns of words need this much room: beyond this text size, or on a narrower
    // screen, the words go in one column.
    private static final float MAX_FONT_SCALE_FOR_COLUMNS = 1.2f;
    private static final int MIN_WIDTH_DP_FOR_COLUMNS = 320;

    private final Context mContext;
    private final Host mHost;
    private final FragmentManager mDialogs;
    private final boolean mIsPassphrase;
    private final GeneratedSecret mSecret;
    private final GeneratedSetupState mState;
    private final GeneratedSecret.Screen mScreen;

    private static final int[] WORD_CHOICES = {
        R.id.tally_generated_words_5,
        R.id.tally_generated_words_6,
        R.id.tally_generated_words_7,
        R.id.tally_generated_words_8,
    };

    private final View mEntryContainer;
    private final View mShowSection;
    private final View mTypeSection;
    private final View mGrid;
    private final TextView mNumbersLeft;
    private final TextView mNumbersRight;
    private final TextView mSecretLeft;
    private final TextView mSecretRight;
    private final TextView mPlaceholder;
    private final RadioGroup mWordsChoice;
    private final TextView mStrengthView;
    private final TextView mHintView;
    private final TextView mRevealButton;
    private final TextView mAnotherButton;
    private final Runnable mHideWhenTimeIsUp = this::hide;

    // What the two secret views show while the secret is revealed. Wiped when it is hidden.
    private char[] mDisplayLeft = new char[0];
    private char[] mDisplayRight = new char[0];

    private boolean mDestroyed;
    // The host is not called back before the constructor is done: it has no panel yet.
    private boolean mConstructed;

    /**
     * Adds the panel to the screen, above the entry field. The first panel of an activity
     * starts the making of a secret; a later one shows the secret the holder kept.
     *
     * @param entryContainer the view that holds the entry field; hidden while the secret is
     *     shown
     * @param holder the activity's holder of what outlives a screen
     * @param isPassphrase a passphrase if true, a PIN otherwise
     * @param userId the user whose lock is set
     * @param dialogs where the details behind the strength line are shown
     */
    public GeneratedCredentialPanel(LayoutInflater inflater, View entryContainer,
            LockSetupHolder holder, boolean isPassphrase, LockPatternUtils utils, int userId,
            Host host, FragmentManager dialogs) {
        mContext = entryContainer.getContext();
        mHost = host;
        mDialogs = dialogs;
        mIsPassphrase = isPassphrase;
        mEntryContainer = entryContainer;
        mSecret = holder.generatedSecret(isPassphrase, mContext);
        mState = mSecret.state();
        mScreen = new GeneratedSecret.Screen() {
            @Override
            public boolean accepts(LockscreenCredential credential) {
                return mHost.isGeneratedCredentialAcceptable(credential);
            }

            @Override
            public LockscreenCredential newPin(int length) {
                return utils.generateStrongPin(length, userId);
            }

            @Override
            public void onSecretChanged() {
                if (!mDestroyed) {
                    render();
                    notifyHost();
                }
            }
        };

        final ViewGroup parent = (ViewGroup) entryContainer.getParent();
        final View root = inflater.inflate(R.layout.tally_generated_credential, parent, false);
        parent.addView(root, parent.indexOfChild(entryContainer));
        mShowSection = root.findViewById(R.id.tally_generated_show_section);
        mTypeSection = root.findViewById(R.id.tally_generated_type_section);
        mGrid = root.findViewById(R.id.tally_generated_grid);
        mNumbersLeft = root.findViewById(R.id.tally_generated_numbers);
        mNumbersRight = root.findViewById(R.id.tally_generated_numbers_right);
        mSecretLeft = root.findViewById(R.id.tally_generated_words);
        mSecretRight = root.findViewById(R.id.tally_generated_words_right);
        mPlaceholder = root.findViewById(R.id.tally_generated_placeholder);
        mWordsChoice = root.findViewById(R.id.tally_generated_words_choice);
        mStrengthView = root.findViewById(R.id.tally_generated_strength);
        mHintView = root.findViewById(R.id.tally_generated_hint);
        mRevealButton = root.findViewById(R.id.tally_generated_reveal);
        mAnotherButton = root.findViewById(R.id.tally_generated_another);

        // Only accessibility services that are tools for the user get to read the secret.
        mSecretLeft.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        mSecretRight.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        mSecretLeft.setSaveEnabled(false);
        mSecretRight.setSaveEnabled(false);

        mRevealButton.setOnClickListener(v -> {
            if (mState.isRevealed()) {
                hide();
            } else if (mState.reveal(SystemClock.elapsedRealtime(), REVEAL_TIMEOUT_MS)) {
                renderSecret();
                notifyHost();
            }
        });
        mAnotherButton.setText(isPassphrase
                ? R.string.tally_generated_another : R.string.tally_generated_another_pin);
        mAnotherButton.setOnClickListener(v -> mSecret.generate());
        mStrengthView.setOnClickListener(v -> showStrengthDetails());
        root.findViewById(R.id.tally_generated_show_again)
                .setOnClickListener(v -> mHost.onShowGeneratedCredentialAgain());

        if (isPassphrase) {
            final NumberFormat format = NumberFormat.getIntegerInstance();
            for (int i = 0; i < WORD_CHOICES.length; i++) {
                final int words = PassphraseGenerator.MIN_WORDS + i;
                final RadioButton choice = root.findViewById(WORD_CHOICES[i]);
                choice.setText(format.format(words));
                choice.setContentDescription(
                        mContext.getString(R.string.tally_generated_words_choice, words));
            }
            mWordsChoice.check(WORD_CHOICES[mSecret.words() - PassphraseGenerator.MIN_WORDS]);
            mWordsChoice.setOnCheckedChangeListener((group, checkedId) -> {
                for (int i = 0; i < WORD_CHOICES.length; i++) {
                    if (WORD_CHOICES[i] == checkedId) {
                        mSecret.setWords(PassphraseGenerator.MIN_WORDS + i);
                    }
                }
            });
        } else {
            // A PIN has one length: no choice, and the button stays at the end of its row.
            mWordsChoice.setVisibility(View.GONE);
            root.findViewById(R.id.tally_generated_words_caption).setVisibility(View.GONE);
            root.findViewById(R.id.tally_generated_row_filler).setVisibility(View.VISIBLE);
            mNumbersLeft.setVisibility(View.GONE);
            mNumbersRight.setVisibility(View.GONE);
            mSecretRight.setVisibility(View.GONE);
        }
        mSecret.attach(mScreen);
        render();
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
        final LockscreenCredential credential = mSecret.newCredential();
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

    /** The screen is no longer in front, and stays: the secret leaves the screen. */
    public void onPause() {
        hide();
    }

    /**
     * The screen is going away: nothing of the secret stays in its views. The secret itself is
     * the holder's, which keeps it for a screen that is created anew and overwrites it when the
     * choice of a lock is over.
     */
    public void onDestroy() {
        mDestroyed = true;
        clearSecretViews();
        mSecret.detach(mScreen);
    }

    private void showStrengthDetails() {
        if (!mSecret.hasSecret()) {
            return;
        }
        final String details = mIsPassphrase
                ? LockStrengthText.details(mContext, mSecret.entropyBits(), Choice.PIN_6_DIGITS,
                        Choice.WORDS_5, Choice.WORDS_6, Choice.WORDS_7, Choice.WORDS_8,
                        Choice.RANDOM_PIN_20)
                : LockStrengthText.details(mContext, mSecret.entropyBits(),
                        Choice.PIN_6_DIGITS, Choice.WORDS_5, Choice.WORDS_6,
                        Choice.RANDOM_PIN_20);
        InfoDialog.show(mDialogs, TAG_DETAILS, R.string.tally_strength_details_title, details);
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

    // Fills the card: the secret if it is revealed, dots in the same places if it is not, so
    // that the card has the same size either way; a note if there is no secret.
    private void renderSecret() {
        clearSecretViews();
        final boolean hasSecret = mSecret.hasSecret();
        final boolean revealed = mState.isRevealed() && hasSecret;
        mGrid.setVisibility(hasSecret ? View.VISIBLE : View.GONE);
        mPlaceholder.setVisibility(hasSecret ? View.GONE : View.VISIBLE);
        final boolean twoColumns = mIsPassphrase && fitsTwoColumns();
        if (!hasSecret) {
            if (mSecret.isPreparing()) {
                mPlaceholder.setText(R.string.tally_generated_preparing);
            } else {
                mPlaceholder.setText(mSecret.isBlockedByRules()
                        ? R.string.tally_generated_blocked_by_rules
                        : R.string.tally_generated_unavailable);
            }
        } else if (mIsPassphrase) {
            final String[] numbers = SecretDisplay.numberColumns(mSecret.words(), twoColumns);
            mNumbersLeft.setText(numbers[0]);
            mNumbersRight.setText(numbers[1]);
            mNumbersRight.setVisibility(twoColumns ? View.VISIBLE : View.GONE);
            mSecretRight.setVisibility(twoColumns ? View.VISIBLE : View.GONE);
            // The card has the rows this number of words needs: three for 5 and 6 words in
            // two columns, four for 7 and 8. Dots or words, the lines are the same.
            if (!revealed) {
                final String[] dots = SecretDisplay.maskColumns(mSecret.words(), twoColumns);
                mSecretLeft.setText(dots[0]);
                mSecretRight.setText(dots[1]);
            }
        } else if (!revealed) {
            mSecretLeft.setText(SecretDisplay.maskedDigits(PIN_LENGTH));
        }
        final char[][] shown = revealed ? mSecret.displayColumns(twoColumns) : null;
        if (shown != null) {
            mDisplayLeft = shown[0];
            mDisplayRight = shown[1];
            mSecretLeft.setText(mDisplayLeft, 0, mDisplayLeft.length);
            mSecretRight.setText(mDisplayRight, 0, mDisplayRight.length);
            // Its time on screen runs on when a new screen shows it: it is not given anew.
            mSecretLeft.postDelayed(mHideWhenTimeIsUp,
                    mState.revealMillisLeft(SystemClock.elapsedRealtime()));
        }
        // A screen reader says "Hidden" for the dots.
        final String hidden = hasSecret && !revealed
                ? mContext.getString(R.string.tally_generated_hidden_description) : null;
        mSecretLeft.setContentDescription(hidden);
        mSecretRight.setContentDescription(hidden);
        mRevealButton.setText(mState.isRevealed()
                ? R.string.tally_generated_hide : R.string.tally_generated_show);
        mRevealButton.setEnabled(hasSecret);
        mAnotherButton.setEnabled(hasSecret);
        for (int id : WORD_CHOICES) {
            mWordsChoice.findViewById(id).setEnabled(hasSecret);
        }
    }

    // Two columns of words need room: at a large text size, or on a narrow screen, the words
    // go in one column.
    private boolean fitsTwoColumns() {
        final Configuration config = mContext.getResources().getConfiguration();
        return config.fontScale <= MAX_FONT_SCALE_FOR_COLUMNS
                && config.screenWidthDp >= MIN_WIDTH_DP_FOR_COLUMNS;
    }

    // The strength row is not secret: it depends on the number of words or digits only.
    private void renderStrength() {
        final boolean hasSecret = mSecret.hasSecret();
        mStrengthView.setVisibility(hasSecret ? View.VISIBLE : View.INVISIBLE);
        if (hasSecret) {
            mStrengthView.setText(LockStrengthText.strengthLine(mContext, mSecret.entropyBits()));
        }
    }
}

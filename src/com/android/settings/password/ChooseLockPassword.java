/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.password;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
import static android.app.admin.DevicePolicyResources.Strings.Settings.PASSWORD_RECENTLY_USED;
import static android.app.admin.DevicePolicyResources.Strings.Settings.PIN_RECENTLY_USED;
import static android.app.admin.DevicePolicyResources.Strings.Settings.REENTER_WORK_PROFILE_PASSWORD_HEADER;
import static android.app.admin.DevicePolicyResources.Strings.Settings.REENTER_WORK_PROFILE_PIN_HEADER;
import static android.app.admin.DevicePolicyResources.Strings.Settings.SET_WORK_PROFILE_PASSWORD_HEADER;
import static android.app.admin.DevicePolicyResources.Strings.Settings.SET_WORK_PROFILE_PIN_HEADER;
import static android.app.admin.DevicePolicyResources.UNDEFINED;
import static android.os.UserManager.USER_TYPE_PROFILE_SUPERVISING;
import static android.view.View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE;
import static android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE;

import static com.android.internal.widget.LockPatternUtils.CREDENTIAL_TYPE_NONE;
import static com.android.internal.widget.PasswordValidationError.CONTAINS_INVALID_CHARACTERS;
import static com.android.internal.widget.PasswordValidationError.CONTAINS_SEQUENCE;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_DIGITS;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_LETTERS;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_LOWER_CASE;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_NON_DIGITS;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_NON_LETTER;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_SYMBOLS;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_UPPER_CASE;
import static com.android.internal.widget.PasswordValidationError.RECENTLY_USED;
import static com.android.internal.widget.PasswordValidationError.TOO_LONG;
import static com.android.internal.widget.PasswordValidationError.TOO_SHORT;
import static com.android.internal.widget.PasswordValidationError.TOO_SHORT_WHEN_ALL_NUMERIC;
import static com.android.settings.password.ChooseLockSettingsHelper.EXTRA_KEY_UNIFICATION_PROFILE_CREDENTIAL;
import static com.android.settings.password.ChooseLockSettingsHelper.EXTRA_KEY_UNIFICATION_PROFILE_ID;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManager.PasswordComplexity;
import android.app.admin.PasswordMetrics;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImeAwareEditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.insets.ProtectionLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.PasswordValidationError;
import com.android.internal.widget.TextViewInputDisabler;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SetupWizardUtils;
import com.android.settings.Utils;
import com.android.settings.core.InstrumentedFragment;
import com.android.settings.flags.Flags;
import com.android.settings.notification.RedactionInterstitial;
import com.android.settings.password.passphrase.ChosenPassphraseRater;
import com.android.settings.password.passphrase.GeneratedCredentialPanel;
import com.android.settings.password.passphrase.LockStrength;
import com.android.settings.password.passphrase.OwnPassphraseFeedback;
import com.android.settings.password.passphrase.OwnPassphraseVerdictView;
import com.android.settings.password.passphrase.ShapeRater;
import com.android.settings.password.passphrase.StrengthClass;
import com.android.settings.password.passphrase.WeakerRiskDialog;
import com.android.settings.password.passphrase.WeakerRiskGate;
import com.android.settings.widget.ImeAwareTextInputEditText;
import com.android.settingslib.utils.StringUtil;

import com.google.android.material.textfield.TextInputLayout;
import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChooseLockPassword extends SettingsActivity {
    private static final String TAG = "ChooseLockPassword";

    static final String EXTRA_KEY_MIN_METRICS = "min_metrics";
    static final String EXTRA_KEY_MIN_COMPLEXITY = "min_complexity";
    public static final String EXTRA_KEY_FOR_SUPERVISION_RESET = "for_supervision_reset";
    /**
     * Whether the phone generates the passphrase or PIN: it is shown, typed back and typed once
     * more, instead of being chosen by the user.
     */
    public static final String EXTRA_KEY_GENERATED = "tally_generated_credential";

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, getFragmentClass().getName());
        modIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_USE_EXPRESSIVE_STYLE,
                ThemeHelper.shouldApplyGlifExpressiveStyle(getApplicationContext()));
        return modIntent;
    }

    public static class IntentBuilder {

        private final Intent mIntent;

        public IntentBuilder(Context context) {
            mIntent = new Intent(context, ChooseLockPassword.class);
            mIntent.putExtra(ChooseLockGeneric.CONFIRM_CREDENTIALS, false);
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_USE_EXPRESSIVE_STYLE,
                    ThemeHelper.shouldApplyGlifExpressiveStyle(context));
        }

        /**
         * Sets the intended credential type i.e. whether it's numeric PIN or general password
         * @param passwordType password type represented by one of the {@code PASSWORD_QUALITY_}
         *   constants.
         */
        public IntentBuilder setPasswordType(int passwordType) {
            mIntent.putExtra(LockPatternUtils.PASSWORD_TYPE_KEY, passwordType);
            return this;
        }

        public IntentBuilder setUserId(int userId) {
            mIntent.putExtra(Intent.EXTRA_USER_ID, userId);
            return this;
        }

        public IntentBuilder setRequestGatekeeperPasswordHandle(
                boolean requestGatekeeperPasswordHandle) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_GK_PW_HANDLE,
                    requestGatekeeperPasswordHandle);
            return this;
        }

        public IntentBuilder setPassword(LockscreenCredential password) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, password);
            return this;
        }

        public IntentBuilder setForFingerprint(boolean forFingerprint) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_FINGERPRINT, forFingerprint);
            return this;
        }

        public IntentBuilder setForFace(boolean forFace) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_FACE, forFace);
            return this;
        }

        public IntentBuilder setForBiometrics(boolean forBiometrics) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_BIOMETRICS, forBiometrics);
            return this;
        }

        /** Sets whether the lock setup flow is for resetting an existing supervision PIN */
        public IntentBuilder setForSupervisionReset(boolean forSupervisionReset) {
            mIntent.putExtra(EXTRA_KEY_FOR_SUPERVISION_RESET, forSupervisionReset);
            return this;
        }

        /** Sets the minimum password requirement in terms of complexity and metrics */
        public IntentBuilder setPasswordRequirement(@PasswordComplexity int level,
                PasswordMetrics metrics) {
            mIntent.putExtra(EXTRA_KEY_MIN_COMPLEXITY, level);
            mIntent.putExtra(EXTRA_KEY_MIN_METRICS, metrics);
            return this;
        }

        public IntentBuilder setReturnCredentials(boolean returnCredentials) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_RETURN_CREDENTIALS,
                    returnCredentials);
            return this;
        }

        /** Sets whether the phone generates the passphrase or PIN. */
        public IntentBuilder setGenerated(boolean generated) {
            mIntent.putExtra(EXTRA_KEY_GENERATED, generated);
            return this;
        }

        /**
         * Configures the launch such that at the end of the password enrollment, one of its
         * managed profile (specified by {@code profileId}) will have its lockscreen unified
         * to the parent user. The profile's current lockscreen credential needs to be specified by
         * {@code credential}.
         */
        public IntentBuilder setProfileToUnify(int profileId, LockscreenCredential credential) {
            mIntent.putExtra(EXTRA_KEY_UNIFICATION_PROFILE_ID, profileId);
            mIntent.putExtra(EXTRA_KEY_UNIFICATION_PROFILE_CREDENTIAL, credential);
            return this;
        }

        public Intent build() {
            return mIntent;
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ChooseLockPasswordFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    @Override
    protected boolean isToolbarEnabled() {
        return false;
    }

    /* package */ Class<? extends Fragment> getFragmentClass() {
        return ChooseLockPasswordFragment.class;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(SetupWizardUtils.getTheme(this, getIntent()));
        ThemeHelper.trySetDynamicColor(this);
        if (ThemeHelper.shouldApplyGlifExpressiveStyle(getApplicationContext())) {
            ThemeHelper.trySetSuwTheme(this);
        }
        super.onCreate(savedInstanceState);
        findViewById(R.id.content_parent).setFitsSystemWindows(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        // No picture of this screen in recents either.
        setRecentsScreenshotEnabled(false);
    }

    public static class ChooseLockPasswordFragment extends InstrumentedFragment
            implements OnEditorActionListener, TextWatcher, SaveAndFinishWorker.Listener,
            SaveAndFinishWorker.RefusalListener, WeakerRiskDialog.Listener,
            GeneratedCredentialPanel.Host {
        private static final String KEY_FIRST_PASSWORD = "first_password";
        private static final String KEY_UI_STAGE = "ui_stage";
        private static final String KEY_CURRENT_CREDENTIAL = "current_credential";
        private static final String FRAGMENT_TAG_SAVE_AND_FINISH = "save_and_finish_worker";
        private static final String KEY_IS_AUTO_CONFIRM_CHECK_MANUALLY_CHANGED =
                "auto_confirm_option_set_manually";

        private static final int MIN_AUTO_PIN_REQUIREMENT_LENGTH = 6;

        private LockscreenCredential mCurrentCredential;
        private LockscreenCredential mChosenPassword;
        private boolean mRequestGatekeeperPassword;
        private boolean mRequestWriteRepairModePassword;
        private boolean mReturnCredentials;
        private EditText mPasswordEntry;
        private TextViewInputDisabler mPasswordEntryInputDisabler;

        // Minimum password metrics enforced by admins.
        private PasswordMetrics mMinMetrics;
        private List<PasswordValidationError> mValidationErrors;

        @PasswordComplexity private int mMinComplexity = PASSWORD_COMPLEXITY_NONE;
        protected int mUserId;
        private byte[] mPasswordHistoryHashFactor;
        private int mUnificationProfileId = UserHandle.USER_NULL;

        private LockPatternUtils mLockPatternUtils;
        private SaveAndFinishWorker mSaveAndFinishWorker;
        private int mPasswordType = DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
        protected Stage mUiStage = Stage.Introduction;
        private PasswordRequirementAdapter mPasswordRequirementAdapter;
        private GlifLayout mLayout;
        protected boolean mForFingerprint;
        protected boolean mForFace;
        protected boolean mForBiometrics;
        protected boolean mForSupervisionReset;

        private LockscreenCredential mFirstPassword;
        private RecyclerView mPasswordRestrictionView;
        protected boolean mIsAlphaMode;
        protected FooterButton mSkipOrClearButton;
        private FooterButton mNextButton;
        private TextView mMessage;
        protected CheckBox mAutoPinConfirmOption;
        protected TextView mAutoConfirmSecurityMessage;
        protected boolean mIsAutoPinConfirmOptionSetManually;

        private TextChangedHandler mTextChangedHandler;

        private static final int CONFIRM_EXISTING_REQUEST = 58;
        static final int RESULT_FINISHED = RESULT_FIRST_USER;
        private boolean mIsErrorTooShort = true;
        private boolean mIsExpressiveStyle = false;

        // Set while the phone generates the passphrase or PIN instead of the user choosing it.
        @Nullable private GeneratedCredentialPanel mGeneratedPanel;
        private WeakerRiskGate mRiskGate;
        // The lock settings refused the save for want of the user's agreement to the risk.
        private boolean mSaveRefused;
        // The risk dialog is up for a first entry that counts as weaker: agreeing goes on to
        // the second entry, going back stays here.
        private boolean mRiskAskedForFirstEntry;
        private final ChosenPassphraseRater mRater = new ShapeRater();
        // The verdict under the field while the user types a passphrase of their own.
        @Nullable private OwnPassphraseVerdictView mVerdictView;
        // Whether the lock that is being saved counts as strong.
        private boolean mSavingStrongLock;

        /** Used to store the profile type for which pin/password is being set */
        public enum ProfileType {
            None,
            Managed,
            Private,
            Supervising,
            Other
        };
        protected ProfileType mProfileType;

        /**
         * Keep track internally of where the user is in choosing a pattern.
         */
        protected enum Stage {

            Introduction(
                    R.string.lockpassword_choose_your_password_header, // password
                    SET_WORK_PROFILE_PASSWORD_HEADER,
                    R.string.lockpassword_choose_your_profile_password_header,
                    R.string.lockpassword_choose_your_password_header_for_fingerprint,
                    R.string.lockpassword_choose_your_password_header_for_face,
                    R.string.lockpassword_choose_your_password_header_for_biometrics,
                    R.string.private_space_choose_your_password_header, // private space password
                    R.string.lockpassword_choose_your_pin_header, // pin
                    SET_WORK_PROFILE_PIN_HEADER,
                    R.string.lockpassword_choose_your_profile_pin_header,
                    R.string.lockpassword_choose_your_pin_header_for_fingerprint,
                    R.string.lockpassword_choose_your_pin_header_for_face,
                    R.string.lockpassword_choose_your_pin_header_for_biometrics,
                    R.string.private_space_choose_your_pin_header, // private space pin
                    R.string.supervision_choose_your_pin_header, // supervision pin
                    R.string.supervision_choose_your_new_pin_header, // supervision reset
                    R.string.lock_settings_picker_biometrics_added_security_message,
                    R.string.lock_settings_picker_biometrics_added_security_message,
                    0,
                    R.string.next_label),

            NeedToConfirm(
                    R.string.lockpassword_reenter_your_password_header,
                    REENTER_WORK_PROFILE_PASSWORD_HEADER,
                    R.string.lockpassword_reenter_your_profile_password_header,
                    R.string.lockpassword_reenter_your_password_header,
                    R.string.lockpassword_reenter_your_password_header,
                    R.string.lockpassword_reenter_your_password_header,
                    R.string.lockpassword_reenter_your_password_header,
                    R.string.lockpassword_reenter_your_pin_header,
                    REENTER_WORK_PROFILE_PIN_HEADER,
                    R.string.lockpassword_reenter_your_profile_pin_header,
                    R.string.lockpassword_reenter_your_pin_header,
                    R.string.lockpassword_reenter_your_pin_header,
                    R.string.lockpassword_reenter_your_pin_header,
                    R.string.lockpassword_reenter_your_pin_header,
                    R.string.supervision_confirm_your_pin_header,
                    R.string.supervision_confirm_your_pin_header,
                    0,
                    0,
                    R.string.lockpassword_reenter_your_pin_header,
                    R.string.lockpassword_confirm_label),

            ConfirmWrong(
                    R.string.lockpassword_confirm_passwords_dont_match,
                    UNDEFINED,
                    R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    UNDEFINED,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    0,
                    0,
                    0,
                    R.string.lockpassword_confirm_label);

            Stage(int hintInAlpha,
                    String hintOverrideInAlphaForProfile,
                    int hintInAlphaForProfile,
                    int hintInAlphaForFingerprint,
                    int hintInAlphaForFace,
                    int hintInAlphaForBiometrics,
                    int hintInAlphaForPrivateProfile,
                    int hintInNumeric,
                    String hintOverrideInNumericForProfile,
                    int hintInNumericForProfile,
                    int hintInNumericForFingerprint,
                    int hintInNumericForFace,
                    int hintInNumericForBiometrics,
                    int hintInNumericForPrivateProfile,
                    int hintInNumericForSupervisingProfile,
                    int hintInNumericForSupervisingProfileReset,
                    int messageInAlphaForBiometrics,
                    int messageInNumericForBiometrics,
                    int messageInNumericForSupervisingProfile,
                    int nextButtonText) {

                this.alphaHint = hintInAlpha;
                this.alphaHintOverrideForProfile = hintOverrideInAlphaForProfile;
                this.alphaHintForManagedProfile = hintInAlphaForProfile;
                this.alphaHintForFingerprint = hintInAlphaForFingerprint;
                this.alphaHintForFace = hintInAlphaForFace;
                this.alphaHintForBiometrics = hintInAlphaForBiometrics;
                this.alphaHintForPrivateProfile = hintInAlphaForPrivateProfile;

                this.numericHint = hintInNumeric;
                this.numericHintOverrideForProfile = hintOverrideInNumericForProfile;
                this.numericHintForManagedProfile = hintInNumericForProfile;
                this.numericHintForFingerprint = hintInNumericForFingerprint;
                this.numericHintForFace = hintInNumericForFace;
                this.numericHintForBiometrics = hintInNumericForBiometrics;
                this.numericHintForPrivateProfile = hintInNumericForPrivateProfile;
                this.numericHintForSupervisingProfile = hintInNumericForSupervisingProfile;
                this.numericHintForSupervisingProfileReset =
                        hintInNumericForSupervisingProfileReset;

                this.alphaMessageForBiometrics = messageInAlphaForBiometrics;
                this.numericMessageForBiometrics = messageInNumericForBiometrics;
                this.numericMessageForSupervisingProfile = messageInNumericForSupervisingProfile;

                this.buttonText = nextButtonText;
            }

            public static final int TYPE_NONE = 0;
            public static final int TYPE_FINGERPRINT = 1;
            public static final int TYPE_FACE = 2;
            public static final int TYPE_BIOMETRIC = 3;
            public static final int TYPE_SUPERVISION_RESET = 4;

            // Password header
            public final int alphaHint;
            public final int alphaHintForPrivateProfile;
            public final String alphaHintOverrideForProfile;
            public final int alphaHintForManagedProfile;
            public final int alphaHintForFingerprint;
            public final int alphaHintForFace;
            public final int alphaHintForBiometrics;

            // PIN header
            public final int numericHint;
            public final int numericHintForPrivateProfile;
            public final int numericHintForSupervisingProfile;
            public final int numericHintForSupervisingProfileReset;
            public final String numericHintOverrideForProfile;
            public final int numericHintForManagedProfile;
            public final int numericHintForFingerprint;
            public final int numericHintForFace;
            public final int numericHintForBiometrics;

            // Password description
            public final int alphaMessageForBiometrics;

            // PIN description
            public final int numericMessageForBiometrics;
            public final int numericMessageForSupervisingProfile;

            public final int buttonText;

            public String getHint(Context context, boolean isAlpha, int type, ProfileType profile) {
                if (isAlpha) {
                    if (profile.equals(ProfileType.Private)) {
                        return context.getString(alphaHintForPrivateProfile);
                    } else if (type == TYPE_FINGERPRINT) {
                        return context.getString(alphaHintForFingerprint);
                    } else if (type == TYPE_FACE) {
                        return context.getString(alphaHintForFace);
                    } else if (type == TYPE_BIOMETRIC) {
                        return context.getString(alphaHintForBiometrics);
                    } else if (profile.equals(ProfileType.Managed)) {
                        return context.getSystemService(DevicePolicyManager.class).getResources()
                                .getString(alphaHintOverrideForProfile,
                                        () -> context.getString(alphaHintForManagedProfile));
                    } else {
                        return context.getString(alphaHint);
                    }
                } else {
                    if (profile.equals(ProfileType.Private)) {
                        return context.getString(numericHintForPrivateProfile);
                    } else if (profile.equals(ProfileType.Supervising)) {
                        if (android.app.supervision.flags.Flags.enableSupervisionSettingsUiUpdates()
                                && type == TYPE_SUPERVISION_RESET) {
                            return context.getString(numericHintForSupervisingProfileReset);
                        }
                        return context.getString(numericHintForSupervisingProfile);
                    } else if (type == TYPE_FINGERPRINT) {
                        return context.getString(numericHintForFingerprint);
                    } else if (type == TYPE_FACE) {
                        return context.getString(numericHintForFace);
                    } else if (type == TYPE_BIOMETRIC) {
                        return context.getString(numericHintForBiometrics);
                    } else if (profile.equals(ProfileType.Managed)) {
                        return context.getSystemService(DevicePolicyManager.class).getResources()
                                .getString(numericHintOverrideForProfile,
                                        () -> context.getString(numericHintForManagedProfile));
                    } else {
                        return context.getString(numericHint);
                    }
                }
            }

            public @StringRes int getMessage(boolean isAlpha, int type, ProfileType profile) {
                switch (type) {
                    case TYPE_FINGERPRINT:
                    case TYPE_FACE:
                    case TYPE_BIOMETRIC:
                        return isAlpha ? alphaMessageForBiometrics : numericMessageForBiometrics;
                    case TYPE_SUPERVISION_RESET:
                    case TYPE_NONE:
                        if (!isAlpha && profile.equals(ProfileType.Supervising)) {
                            return numericMessageForSupervisingProfile;
                        }
                        // fall through

                    default:
                        return 0;
                }
            }
        }

        // required constructor for fragments
        public ChooseLockPasswordFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mLockPatternUtils = new LockPatternUtils(getActivity());
            Intent intent = getActivity().getIntent();
            if (!(getActivity() instanceof ChooseLockPassword)) {
                throw new SecurityException("Fragment contained in wrong activity");
            }
            // Only take this argument into account if it belongs to the current profile.
            mUserId = Utils.getUserIdFromBundle(getActivity(), intent.getExtras());
            mProfileType = getProfileType();
            mForFingerprint = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_FOR_FINGERPRINT, false);
            mForFace = intent.getBooleanExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_FACE, false);
            mForBiometrics = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_FOR_BIOMETRICS, false);
            if (android.app.supervision.flags.Flags.enableSupervisionSettingsUiUpdates()) {
                mForSupervisionReset = intent.getBooleanExtra(
                        EXTRA_KEY_FOR_SUPERVISION_RESET, false);
                if (savedInstanceState != null
                        && savedInstanceState.containsKey(EXTRA_KEY_FOR_SUPERVISION_RESET)) {
                    mForSupervisionReset =
                            savedInstanceState.getBoolean(EXTRA_KEY_FOR_SUPERVISION_RESET);
                }
            }
            mIsExpressiveStyle = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_USE_EXPRESSIVE_STYLE, false);

            mPasswordType = intent.getIntExtra(
                    LockPatternUtils.PASSWORD_TYPE_KEY, PASSWORD_QUALITY_NUMERIC);
            mUnificationProfileId = intent.getIntExtra(
                    EXTRA_KEY_UNIFICATION_PROFILE_ID, UserHandle.USER_NULL);

            mMinComplexity = intent.getIntExtra(EXTRA_KEY_MIN_COMPLEXITY, PASSWORD_COMPLEXITY_NONE);
            mMinMetrics = intent.getParcelableExtra(EXTRA_KEY_MIN_METRICS);
            if (mMinMetrics == null) mMinMetrics = new PasswordMetrics(CREDENTIAL_TYPE_NONE);

            mTextChangedHandler = new TextChangedHandler();
            mRiskGate = new WeakerRiskGate(this, mLockPatternUtils, mUserId, savedInstanceState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return mIsExpressiveStyle
                    ? inflater.inflate(R.layout.choose_lock_password_expressive, container, false)
                    : inflater.inflate(R.layout.choose_lock_password, container, false);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mLayout = (GlifLayout) view;

            // TODO(b/440023111):This can be removed once SetupDesignLib and SettingsLib have
            //  integrated the solution.
            if (Flags.removeProtectionLayout() && mIsExpressiveStyle) {
                final ProtectionLayout protect = mLayout.findViewById(
                        com.google.android.setupdesign.R.id.sud_layout_protection);
                if (protect != null) {
                    protect.setProtections(Collections.emptyList());
                }
            }
            // Make the password container consume the optical insets so the edit text is aligned
            // with the sides of the parent visually.
            ViewGroup container = view.findViewById(R.id.password_container);
            container.setOpticalInsets(Insets.NONE);

            final FooterBarMixin mixin = mLayout.getMixin(FooterBarMixin.class);
            mixin.setSecondaryButton(
                    new FooterButton.Builder(getActivity())
                            .setText(R.string.lockpassword_clear_label)
                            .setListener(this::onSkipOrClearButtonClick)
                            .setButtonType(FooterButton.ButtonType.SKIP)
                            .setTheme(
                                    com.google.android.setupdesign.R.style.SudGlifButton_Secondary)
                            .build()
            );
            mixin.setPrimaryButton(
                    new FooterButton.Builder(getActivity())
                            .setText(R.string.next_label)
                            .setListener(this::onNextButtonClick)
                            .setButtonType(FooterButton.ButtonType.NEXT)
                            .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary)
                            .build()
            );
            mSkipOrClearButton = mixin.getSecondaryButton();
            mNextButton = mixin.getPrimaryButton();

            mMessage = view.findViewById(R.id.sud_layout_description);

            mLayout.setIcon(getIcon());

            mIsAlphaMode = DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC == mPasswordType
                    || DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC == mPasswordType
                    || DevicePolicyManager.PASSWORD_QUALITY_COMPLEX == mPasswordType;

            final LinearLayout headerLayout = view.findViewById(
                    com.google.android.setupdesign.R.id.sud_layout_header);
            setupPasswordRequirementsView(headerLayout);

            mPasswordRestrictionView.setLayoutManager(new LinearLayoutManager(getActivity()));
            mPasswordRestrictionView.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            final TextInputLayout passwordEntryLayout = view.findViewById(
                    R.id.password_entry_layout);
            if (mIsExpressiveStyle && passwordEntryLayout != null) {
                final int textEntryLayoutHintId = mIsAlphaMode
                        ? R.string.unlock_set_unlock_mode_password
                        : R.string.unlock_set_unlock_mode_pin;
                passwordEntryLayout.setHint(textEntryLayoutHintId);
                passwordEntryLayout.setHintEnabled(true);
            }
            mPasswordEntry = view.findViewById(R.id.password_entry);
            mPasswordEntry.setOnEditorActionListener(this);
            mPasswordEntry.addTextChangedListener(this);
            mPasswordEntry.requestFocus();
            // What is typed here is a secret: it is not put in the saved state of the screen.
            mPasswordEntry.setSaveEnabled(false);
            view.setImportantForContentCapture(
                    View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
            mPasswordEntryInputDisabler = new TextViewInputDisabler(mPasswordEntry);

            // Fetch the AutoPinConfirmOption
            mAutoPinConfirmOption = view.findViewById(R.id.auto_pin_confirm_enabler);
            mAutoConfirmSecurityMessage = view.findViewById(R.id.auto_pin_confirm_security_message);
            mIsAutoPinConfirmOptionSetManually = false;
            setOnAutoConfirmOptionClickListener();
            if (mAutoPinConfirmOption != null) {
                mAutoPinConfirmOption.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
                mAutoPinConfirmOption.setVisibility(View.GONE);
                mAutoPinConfirmOption.setChecked(false);
            }

            final Activity activity = getActivity();

            int currentType = mPasswordEntry.getInputType();
            mPasswordEntry.setInputType(mIsAlphaMode ? currentType
                    : (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
            if (mIsAlphaMode) {
                mPasswordEntry.setContentDescription(
                        getString(R.string.unlock_accessibility_password));
            } else {
                mPasswordEntry.setContentDescription(
                        getString(R.string.unlock_accessibility_pin_area));
            }
            // Can't set via XML since setInputType resets the fontFamily to null
            mPasswordEntry.setTypeface(Typeface.create(
                    getContext().getString(com.android.internal.R.string.config_headlineFontFamily),
                    Typeface.NORMAL));

            Intent intent = getActivity().getIntent();
            final boolean confirmCredentials = intent.getBooleanExtra(
                    ChooseLockGeneric.CONFIRM_CREDENTIALS, true);
            mCurrentCredential = intent.getParcelableExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
            mRequestGatekeeperPassword = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_GK_PW_HANDLE, false);
            mRequestWriteRepairModePassword = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_WRITE_REPAIR_MODE_PW, false);
            mReturnCredentials = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_RETURN_CREDENTIALS, false);
            // Not while a save from before a recreation of the screen is still running: a new
            // generated PIN would take the place of the one that is being saved.
            final boolean isSaving = savedInstanceState != null && getFragmentManager()
                    .findFragmentByTag(FRAGMENT_TAG_SAVE_AND_FINISH) != null;
            if (!isSaving && intent.getBooleanExtra(EXTRA_KEY_GENERATED, false)) {
                mGeneratedPanel = new GeneratedCredentialPanel(getLayoutInflater(), container,
                        mIsAlphaMode, mLockPatternUtils, mUserId, this,
                        getChildFragmentManager());
            } else if (mIsAlphaMode) {
                mVerdictView = new OwnPassphraseVerdictView(getLayoutInflater(), container,
                        mPasswordEntry, getChildFragmentManager());
            }
            if (savedInstanceState == null) {
                updateStage(Stage.Introduction);
                if (!mIsAlphaMode && mGeneratedPanel == null
                        && mRiskGate.isNeededForWeakerLock()) {
                    // A PIN the user picks is weaker on this phone: say so before it is typed.
                    mRiskGate.show(WeakerRiskDialog.Kind.PIN);
                }
                if (confirmCredentials) {
                    final ChooseLockSettingsHelper.Builder builder =
                            new ChooseLockSettingsHelper.Builder(getActivity());
                    builder.setRequestCode(CONFIRM_EXISTING_REQUEST)
                            .setTitle(getString(R.string.unlock_set_unlock_launch_picker_title))
                            .setReturnCredentials(true)
                            .setRequestGatekeeperPasswordHandle(mRequestGatekeeperPassword)
                            .setRequestWriteRepairModePassword(mRequestWriteRepairModePassword)
                            .setUserId(mUserId)
                            .show();
                }
            } else {

                // restore from previous state. What was typed, or generated, so far is not
                // kept across a recreation of the screen: the choice starts again.
                updateStage(Stage.Introduction);
                mIsAutoPinConfirmOptionSetManually =
                        savedInstanceState.getBoolean(KEY_IS_AUTO_CONFIRM_CHECK_MANUALLY_CHANGED);

                mCurrentCredential = savedInstanceState.getParcelable(KEY_CURRENT_CREDENTIAL);

                // Re-attach to the exiting worker if there is one.
                mSaveAndFinishWorker = (SaveAndFinishWorker) getFragmentManager().findFragmentByTag(
                        FRAGMENT_TAG_SAVE_AND_FINISH);
            }

            if (activity instanceof SettingsActivity) {
                final SettingsActivity sa = (SettingsActivity) activity;
                String title = Stage.Introduction.getHint(
                        getContext(), mIsAlphaMode, getStageType(), mProfileType);
                if (showsOwnPassphraseHeaders()) {
                    title = getString(R.string.tally_own_passphrase_header);
                }
                sa.setTitle(title);
                mLayout.setHeaderText(title);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mCurrentCredential != null) {
                mCurrentCredential.zeroize();
            }
            if (mFirstPassword != null) {
                mFirstPassword.zeroize();
            }
            // A save that is still running holds the chosen password and zeroizes nothing
            // itself: leave it alone then.
            if (mChosenPassword != null && mSaveAndFinishWorker == null) {
                mChosenPassword.zeroize();
            }
            if (mGeneratedPanel != null) {
                mGeneratedPanel.onDestroy();
            }
            // Force a garbage collection immediately to remove remnant of user password shards
            // from memory.
            System.gc();
            System.runFinalization();
            System.gc();
        }

        protected int getStageType() {
            if (android.app.supervision.flags.Flags.enableSupervisionSettingsUiUpdates()
                    && mForSupervisionReset) {
                return Stage.TYPE_SUPERVISION_RESET;
            } else if (mForFingerprint) {
                return Stage.TYPE_FINGERPRINT;
            } else if (mForFace) {
                return Stage.TYPE_FACE;
            } else if (mForBiometrics) {
                return Stage.TYPE_BIOMETRIC;
            } else {
                return Stage.TYPE_NONE;
            }
        }

        private void setupPasswordRequirementsView(@Nullable ViewGroup view) {
            if (view == null) {
                return;
            }

            createHintMessageView(view);
            mPasswordRestrictionView.setLayoutManager(new LinearLayoutManager(getActivity()));
            mPasswordRequirementAdapter = new PasswordRequirementAdapter(getActivity());
            mPasswordRestrictionView.setAdapter(mPasswordRequirementAdapter);
            view.addView(mPasswordRestrictionView);
        }

        @VisibleForTesting
        View getPasswordRequirementsView() {
            return mPasswordRestrictionView;
        }

        private void createHintMessageView(ViewGroup view) {
            if (mPasswordRestrictionView != null) {
                return;
            }

            final TextView sucTitleView = view.findViewById(
                    com.google.android.setupdesign.R.id.suc_layout_title);
            final ViewGroup.MarginLayoutParams titleLayoutParams =
                    (ViewGroup.MarginLayoutParams) sucTitleView.getLayoutParams();
            mPasswordRestrictionView = new RecyclerView(getActivity());
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(titleLayoutParams.leftMargin, getResources().getDimensionPixelSize(
                    R.dimen.password_requirement_view_margin_top), titleLayoutParams.leftMargin, 0);
            mPasswordRestrictionView.setLayoutParams(lp);
        }

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.CHOOSE_LOCK_PASSWORD;
        }

        @Override
        public void onResume() {
            super.onResume();
            updateStage(mUiStage);
            if (mSaveAndFinishWorker != null) {
                mSaveAndFinishWorker.setListener(this);
            } else if (mGeneratedPanel != null && mGeneratedPanel.isShowStep()) {
                // Nothing to type yet: the generated passphrase or PIN is being shown.
            } else {
                mPasswordEntry.requestFocus();
                if (mPasswordEntry instanceof ImeAwareEditText) {
                    ((ImeAwareEditText) mPasswordEntry).scheduleShowSoftInput();
                } else if (mPasswordEntry instanceof ImeAwareTextInputEditText) {
                    ((ImeAwareTextInputEditText) mPasswordEntry).scheduleShowSoftInput();
                } else {
                    Log.w(TAG,
                            "mPasswordEntry neither ImeAwareEditText nor "
                                    + "ImeAwareTextInputEditText");
                }
            }
        }

        @Override
        public void onPause() {
            if (mSaveAndFinishWorker != null) {
                mSaveAndFinishWorker.setListener(null);
            } else {
                // Nothing secret stays on a screen that is not in front.
                mPasswordEntry.setText("");
            }
            if (mGeneratedPanel != null) {
                mGeneratedPanel.onPause();
            }
            super.onPause();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(KEY_UI_STAGE, mUiStage.name());
            // The new passphrase or PIN is not saved: see onViewCreated.
            mRiskGate.onSaveInstanceState(outState);
            if (mCurrentCredential != null) {
                outState.putParcelable(KEY_CURRENT_CREDENTIAL, mCurrentCredential.duplicate());
            }
            outState.putBoolean(KEY_IS_AUTO_CONFIRM_CHECK_MANUALLY_CHANGED,
                    mIsAutoPinConfirmOptionSetManually);
            outState.putBoolean(EXTRA_KEY_FOR_SUPERVISION_RESET, mForSupervisionReset);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode,
                Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            switch (requestCode) {
                case CONFIRM_EXISTING_REQUEST:
                    if (resultCode != Activity.RESULT_OK) {
                        getActivity().setResult(RESULT_FINISHED);
                        getActivity().finish();
                    } else {
                        mCurrentCredential = data.getParcelableExtra(
                                ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
                    }
                    break;
            }
        }

        @Nullable
        protected Intent getRedactionInterstitialIntent(Context context) {
            // The supervising profile does not have its own lock screen, and thus can skip the
            // redaction interstitial.
            if (isSupervisingProfile()) {
                return null;
            }
            return RedactionInterstitial.createStartIntent(context, mUserId);
        }

        protected void updateStage(Stage stage) {
            final Stage previousStage = mUiStage;
            mUiStage = stage;
            updateUi();

            // If the stage changed, announce the header for accessibility. This
            // is a no-op when accessibility is disabled.
            if (previousStage != stage) {
                getActivity().setTitle(mLayout.getHeaderText());
            }
        }

        /**
         * Validates PIN/Password and returns the validation result and updates mValidationErrors
         * to reflect validation results.
         *
         * @param credential credential the user typed in.
         * @return whether password satisfies all the requirements.
         */
        @VisibleForTesting
        boolean validatePassword(LockscreenCredential credential) {
            mValidationErrors = PasswordMetrics.validateCredential(mMinMetrics, mMinComplexity,
                    credential);
            // The hash factor needs a check of the current lock, which takes about a second:
            // only get it when a history is kept at all.
            if (mValidationErrors.isEmpty()
                    && getContext().getSystemService(DevicePolicyManager.class)
                            .getPasswordHistoryLength(null /* admin */, mUserId) > 0
                    && mLockPatternUtils.checkPasswordHistory(
                        credential.getCredential(), getPasswordHistoryHashFactor(), mUserId)) {
                mValidationErrors =
                        Collections.singletonList(new PasswordValidationError(RECENTLY_USED));
            }
            return mValidationErrors.isEmpty();
        }

        /**
         * Lazily compute and return the history hash factor of the current user (mUserId), used for
         * password history check.
         */
        private byte[] getPasswordHistoryHashFactor() {
            if (mPasswordHistoryHashFactor == null) {
                mPasswordHistoryHashFactor = mLockPatternUtils.getPasswordHistoryHashFactor(
                        mCurrentCredential != null ? mCurrentCredential
                                : LockscreenCredential.createNone(), mUserId);
            }
            return mPasswordHistoryHashFactor;
        }

        public void handleNext() {
            if (mSaveAndFinishWorker != null) return;
            if (mGeneratedPanel != null && mUiStage == Stage.Introduction) {
                // The generated passphrase or PIN takes the place of a first entry. It is
                // hidden now and has to be typed back.
                final LockscreenCredential generated = mGeneratedPanel.continueToTypeBack();
                if (generated != null) {
                    if (mFirstPassword != null) {
                        mFirstPassword.zeroize();
                    }
                    mFirstPassword = generated;
                    mPasswordEntry.setText("");
                    updateStage(Stage.NeedToConfirm);
                    focusPasswordEntry();
                }
                return;
            }
            // TODO(b/120484642): This is a point of entry for passwords from the UI
            final Editable passwordText = mPasswordEntry.getText();
            if (TextUtils.isEmpty(passwordText)) {
                return;
            }
            mChosenPassword = mIsAlphaMode ? LockscreenCredential.createPassword(passwordText)
                    : LockscreenCredential.createPin(passwordText);
            if (mUiStage == Stage.Introduction) {
                if (validatePassword(mChosenPassword)) {
                    if (mIsAlphaMode && mRiskGate.isNeededFor(mChosenPassword, false)) {
                        // Not the shape of a strong passphrase: it counts as weaker on this
                        // phone, like a PIN. It can be used once the user has read the risk.
                        mRiskAskedForFirstEntry = true;
                        mRiskGate.show(WeakerRiskDialog.Kind.PASSWORD);
                        return;
                    }
                    mFirstPassword = mChosenPassword;
                    mPasswordEntry.setText("");
                    updateStage(Stage.NeedToConfirm);
                } else {
                    mChosenPassword.zeroize();
                }
            } else if (mUiStage == Stage.NeedToConfirm) {
                if (mChosenPassword.equals(mFirstPassword)) {
                    if (mGeneratedPanel != null && !mGeneratedPanel.onTypedCorrectly()) {
                        // Typed back correctly. Once more, for practice, before it is saved.
                        mChosenPassword.zeroize();
                        mPasswordEntry.setText("");
                        updateUi();
                    } else if (mRiskGate.isNeededFor(mChosenPassword,
                            mGeneratedPanel != null)) {
                        // Reached without the risk screen, for example by an intent that
                        // names the kind of lock. It is shown now; agreeing saves.
                        mSaveRefused = true;
                        mRiskGate.show(getRiskKind());
                    } else {
                        startSaveAndFinish();
                    }
                } else {
                    CharSequence tmp = mPasswordEntry.getText();
                    if (tmp != null) {
                        Selection.setSelection((Spannable) tmp, 0, tmp.length());
                    }
                    updateStage(Stage.ConfirmWrong);
                    mChosenPassword.zeroize();
                    if (mGeneratedPanel != null) {
                        // A wrong entry of the generated passphrase or PIN is not kept for
                        // correcting: start it again. The header keeps saying what happened.
                        mPasswordEntry.setText("");
                    }
                }
            }
        }

        // Whether this screen has the headers of the own passphrase: a personal lock, typed
        // by the user. A profile keeps the stock headers, which name the profile.
        private boolean showsOwnPassphraseHeaders() {
            return mVerdictView != null && mProfileType == ProfileType.None;
        }

        protected void setNextEnabled(boolean enabled) {
            mNextButton.setEnabled(enabled);
        }

        protected void setNextText(int text) {
            mNextButton.setText(getActivity(), text);
        }

        protected void onSkipOrClearButtonClick(View view) {
            mPasswordEntry.setText("");
        }

        protected void onNextButtonClick(View view) {
            handleNext();
        }

        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            // Check if this was the result of hitting the enter or "done" key
            if (actionId == EditorInfo.IME_NULL
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_NEXT) {
                handleNext();
                return true;
            }
            return false;
        }

    String[] convertErrorCodeToMessages() {
        var pvec = new PasswordValidationErrorConverter(getContext(), mIsAlphaMode, mProfileType, mValidationErrors);
        String[] res = pvec.convertErrorCodeToMessages();
        mIsErrorTooShort = pvec.mIsErrorTooShort;
        return res;
    }

    public static class PasswordValidationErrorConverter {
        private final Context mContext;
        private final boolean mIsAlphaMode;
        private final ProfileType mProfileType;
        private final List<PasswordValidationError> mValidationErrors;
        public boolean mIsErrorTooShort = true;

        public PasswordValidationErrorConverter(Context context, boolean isAlphaMode,
                              ProfileType profileType,
                              List<PasswordValidationError> validationErrors) {
            mContext = context;
            mIsAlphaMode = isAlphaMode;
            mValidationErrors = validationErrors;
            mProfileType = profileType;
        }

        private Context getContext() {
            return mContext;
        }

        private String getString(int id) {
            return mContext.getString(id);
        }

        /**
         * @param errorCode error code returned from password validation.
         * @return an array of messages describing the error, important messages come first.
         */
        public String[] convertErrorCodeToMessages() {
            List<String> messages = new ArrayList<>();
            mIsErrorTooShort = false;
            for (PasswordValidationError error : mValidationErrors) {
                switch (error.errorCode) {
                    case CONTAINS_INVALID_CHARACTERS:
                        messages.add(getString(R.string.lockpassword_illegal_character));
                        break;
                    case NOT_ENOUGH_UPPER_CASE:
                        messages.add(StringUtil.getIcuPluralsString(getContext(), error.requirement,
                                R.string.lockpassword_password_requires_uppercase));
                        break;
                    case NOT_ENOUGH_LOWER_CASE:
                        messages.add(StringUtil.getIcuPluralsString(getContext(), error.requirement,
                                R.string.lockpassword_password_requires_lowercase));
                        break;
                    case NOT_ENOUGH_LETTERS:
                        messages.add(StringUtil.getIcuPluralsString(getContext(), error.requirement,
                                R.string.lockpassword_password_requires_letters));
                        break;
                    case NOT_ENOUGH_DIGITS:
                        messages.add(StringUtil.getIcuPluralsString(getContext(), error.requirement,
                                R.string.lockpassword_password_requires_numeric));
                        break;
                    case NOT_ENOUGH_SYMBOLS:
                        messages.add(StringUtil.getIcuPluralsString(getContext(), error.requirement,
                                R.string.lockpassword_password_requires_symbols));
                        break;
                    case NOT_ENOUGH_NON_LETTER:
                        messages.add(StringUtil.getIcuPluralsString(getContext(), error.requirement,
                                R.string.lockpassword_password_requires_nonletter));
                        break;
                    case NOT_ENOUGH_NON_DIGITS:
                        messages.add(StringUtil.getIcuPluralsString(getContext(), error.requirement,
                                R.string.lockpassword_password_requires_nonnumerical));
                        break;
                    case TOO_SHORT:
                        mIsErrorTooShort = true;
                        boolean isSupervisingProfile = isSupervisingProfile(mProfileType);
                        String message = StringUtil.getIcuPluralsString(getContext(),
                                error.requirement,
                                mIsAlphaMode
                                        ? R.string.lockpassword_password_too_short
                                        : (!android.app.supervision.flags.Flags
                                                .enableSupervisionPinUiUpdatesBugfix()
                                                && isSupervisingProfile)
                                                ? R.string.supervision_pin_length_message
                                                : R.string.lockpassword_pin_too_short);
                        if (!mIsAlphaMode
                                && error.requirement < MIN_AUTO_PIN_REQUIREMENT_LENGTH
                                && !isSupervisingProfile) {
                            Map<String, Object> arguments = new HashMap<>();
                            arguments.put("count", error.requirement);
                            arguments.put("minAutoConfirmLen", MIN_AUTO_PIN_REQUIREMENT_LENGTH);
                            message = StringUtil.getIcuPluralsString(getContext(),
                                    arguments,
                                    R.string.lockpassword_pin_too_short_autoConfirm_extra_message);
                        }
                        messages.add(message);
                        break;
                    case TOO_SHORT_WHEN_ALL_NUMERIC:
                        messages.add(
                                StringUtil.getIcuPluralsString(getContext(), error.requirement,
                                        R.string.lockpassword_password_too_short_all_numeric));
                        break;
                    case TOO_LONG:
                        messages.add(StringUtil.getIcuPluralsString(getContext(),
                                error.requirement + 1, mIsAlphaMode
                                        ? R.string.lockpassword_password_too_long
                                        : R.string.lockpassword_pin_too_long));
                        break;
                    case CONTAINS_SEQUENCE:
                        messages.add(getString(mIsAlphaMode
                                ? R.string.lockpassword_password_no_sequential_characters
                                : R.string.lockpassword_pin_no_sequential_digits));
                        break;
                    case RECENTLY_USED:
                        DevicePolicyManager devicePolicyManager =
                                getContext().getSystemService(DevicePolicyManager.class);
                        if (mIsAlphaMode) {
                            messages.add(devicePolicyManager.getResources().getString(
                                    PASSWORD_RECENTLY_USED,
                                    () -> getString(R.string.lockpassword_password_recently_used)));
                        } else {
                            messages.add(devicePolicyManager.getResources().getString(
                                    PIN_RECENTLY_USED,
                                    () -> getString(R.string.lockpassword_pin_recently_used)));
                        }
                        break;
                    default:
                        Log.wtf(TAG, "unknown error validating password: " + error);
                }
            }

            return messages.toArray(new String[0]);
        }
    }

        /**
         * Update the hint based on current Stage and length of password entry
         */
        protected void updateUi() {
            final boolean canInput = mSaveAndFinishWorker == null;

            LockscreenCredential password = mIsAlphaMode
                    ? LockscreenCredential.createPassword(mPasswordEntry.getText())
                    : LockscreenCredential.createPin(mPasswordEntry.getText());
            final int length = password.size();

            if (mGeneratedPanel != null && mUiStage == Stage.Introduction) {
                // The phone's passphrase or PIN is being shown. There is nothing to type and
                // no rule to meet; Next opens once it was looked at.
                mPasswordRestrictionView.setVisibility(View.GONE);
                setHeaderText(mGeneratedPanel.headerText(false));
                setNextEnabled(canInput && mGeneratedPanel.canContinue());
                mSkipOrClearButton.setVisibility(View.GONE);
                mAutoPinConfirmOption.setVisibility(View.GONE);
                mAutoConfirmSecurityMessage.setVisibility(View.GONE);
            } else if (mUiStage == Stage.Introduction) {
                mPasswordRestrictionView.setVisibility(View.VISIBLE);
                final boolean passwordCompliant = validatePassword(password);
                String[] messages = convertErrorCodeToMessages();
                if (mVerdictView != null && length == 0 && mValidationErrors.size() == 1
                        && mValidationErrors.get(0).errorCode == TOO_SHORT) {
                    // Nothing is typed yet and the only rule is the least length: not said
                    // before it is broken. The line under the field says what counts.
                    messages = new String[0];
                }
                if (showsOwnPassphraseHeaders()) {
                    // Also when coming back from the second entry.
                    setHeaderText(getString(R.string.tally_own_passphrase_header));
                }
                // Update the fulfillment of requirements.
                mPasswordRequirementAdapter.setRequirements(messages, mIsErrorTooShort);
                // set the visibility of pin_auto_confirm option accordingly
                setAutoPinConfirmOption(passwordCompliant, length);
                // Enable/Disable the next button accordingly.
                setNextEnabled(passwordCompliant);
            } else {
                // Hide password requirement view when we are just asking user to confirm the pw.
                mPasswordRestrictionView.setVisibility(View.GONE);
                setHeaderText(mGeneratedPanel != null
                        ? mGeneratedPanel.headerText(mUiStage == Stage.ConfirmWrong)
                        : showsOwnPassphraseHeaders() && mUiStage == Stage.NeedToConfirm
                        ? getString(R.string.tally_own_passphrase_again_header)
                        : mUiStage.getHint(getContext(), mIsAlphaMode, getStageType(),
                                mProfileType));
                setNextEnabled(canInput && length >= LockPatternUtils.MIN_LOCK_PASSWORD_SIZE);
                mSkipOrClearButton.setVisibility(toVisibility(canInput && length > 0));

                // Hide the pin_confirm option when we are just asking user to confirm the pwd.
                mAutoPinConfirmOption.setVisibility(View.GONE);
                mAutoConfirmSecurityMessage.setVisibility(View.GONE);
            }

            int message = mUiStage.getMessage(mIsAlphaMode, getStageType(), mProfileType);
            if (message != 0) {
                mMessage.setVisibility(View.VISIBLE);
                mMessage.setText(message);
            } else {
                mMessage.setVisibility(View.INVISIBLE);
            }
            if (mSaveAndFinishWorker != null) {
                // Saving takes about a second on this phone.
                mMessage.setVisibility(View.VISIBLE);
                mMessage.setText(R.string.tally_lock_saving);
            } else if (mGeneratedPanel != null) {
                // The panel carries the text for a generated passphrase or PIN.
                mMessage.setVisibility(View.GONE);
            } else if (mVerdictView != null && mUiStage == Stage.Introduction) {
                // One short line above the field. The verdict on the entry is under it.
                mMessage.setVisibility(View.VISIBLE);
                mMessage.setText(R.string.tally_own_passphrase_intro);
            } else if (mVerdictView != null) {
                // The second entry: the header and the field, nothing else.
                mMessage.setVisibility(View.GONE);
            }
            if (mVerdictView != null) {
                mVerdictView.show(mUiStage == Stage.Introduction && mSaveAndFinishWorker == null
                        ? getOwnPassphraseFeedback(password).verdict : null);
            }

            setNextText(mUiStage.buttonText);
            mPasswordEntryInputDisabler.setInputEnabled(canInput);
            password.zeroize();
        }

        /**
         * What the phone makes of a password or passphrase the user is typing: whether it
         * counts as strong or as weaker on this phone, and why. A check of its shape only.
         */
        private OwnPassphraseFeedback getOwnPassphraseFeedback(LockscreenCredential password) {
            // The characters are ASCII if the entry is valid; anything else fails the floor.
            final byte[] bytes = password.getCredential();
            final char[] chars = new char[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                chars[i] = (char) (bytes[i] & 0xff);
            }
            final OwnPassphraseFeedback feedback = OwnPassphraseFeedback.of(chars, chars.length,
                    LockStrength.of(password, false), mRater.rate(chars, chars.length));
            Arrays.fill(chars, '\0');
            return feedback;
        }

        private WeakerRiskDialog.Kind getRiskKind() {
            return mIsAlphaMode ? WeakerRiskDialog.Kind.PASSWORD : WeakerRiskDialog.Kind.PIN;
        }

        /**
         * Leaves this screen for the one that sets up a passphrase the phone generates, with
         * everything else this screen was started with. For "Use a passphrase" on the PIN
         * warning.
         */
        private void switchToGeneratedPassphrase() {
            final Activity activity = getActivity();
            final Intent intent = new Intent(activity.getIntent());
            intent.putExtra(LockPatternUtils.PASSWORD_TYPE_KEY,
                    DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);
            intent.putExtra(EXTRA_KEY_GENERATED, true);
            // Whoever waits for the result of this screen gets the new one's.
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(intent);
            activity.finish();
        }

        @Override
        public void onConfigurationChanged(android.content.res.Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            // The screen is not created anew for a rotation, a text size or a theme change, so
            // that nothing typed or generated is lost and nothing has to be saved for it.
            if (mGeneratedPanel != null) {
                mGeneratedPanel.onConfigurationChanged();
            }
        }

        private void focusPasswordEntry() {
            mPasswordEntry.requestFocus();
            if (mPasswordEntry instanceof ImeAwareEditText) {
                ((ImeAwareEditText) mPasswordEntry).scheduleShowSoftInput();
            } else if (mPasswordEntry instanceof ImeAwareTextInputEditText) {
                ((ImeAwareTextInputEditText) mPasswordEntry).scheduleShowSoftInput();
            }
        }

        @Override
        public void onGeneratedCredentialChanged() {
            if (getActivity() != null && mSaveAndFinishWorker == null) {
                if (mUiStage != Stage.Introduction) {
                    // A new passphrase or PIN was generated: what was typed back is void.
                    restartWithGeneratedCredential();
                } else {
                    updateUi();
                }
            }
        }

        @Override
        public void onShowGeneratedCredentialAgain() {
            if (mSaveAndFinishWorker == null && mGeneratedPanel != null) {
                mGeneratedPanel.showAgain();
                restartWithGeneratedCredential();
            }
        }

        @Override
        public boolean isGeneratedCredentialAcceptable(LockscreenCredential credential) {
            return validatePassword(credential);
        }

        // Back to the step where the generated passphrase or PIN can be shown.
        private void restartWithGeneratedCredential() {
            if (mFirstPassword != null) {
                mFirstPassword.zeroize();
                mFirstPassword = null;
            }
            mPasswordEntry.setText("");
            ConfirmDeviceCredentialUtils.hideImeImmediately(
                    getActivity().getWindow().getDecorView());
            updateStage(Stage.Introduction);
        }

        @Override
        public void onWeakerRiskAccepted() {
            mRiskGate.onAccepted();
            if (mRiskAskedForFirstEntry) {
                // The weaker password may be used: on to the second entry.
                mRiskAskedForFirstEntry = false;
                if (mChosenPassword != null && mUiStage == Stage.Introduction
                        && mSaveAndFinishWorker == null) {
                    mFirstPassword = mChosenPassword;
                    mPasswordEntry.setText("");
                    updateStage(Stage.NeedToConfirm);
                }
            } else if (mSaveRefused) {
                mSaveRefused = false;
                if (mChosenPassword != null && mSaveAndFinishWorker == null) {
                    startSaveAndFinish();
                }
            }
        }

        @Override
        public void onWeakerRiskDeclined() {
            if (mRiskAskedForFirstEntry) {
                // Stay here: the entry is still in the field and can be made longer.
                mRiskAskedForFirstEntry = false;
                if (mChosenPassword != null) {
                    mChosenPassword.zeroize();
                }
                return;
            }
            final boolean wasRefused = mSaveRefused;
            if (mSaveRefused) {
                // Nothing was saved. The entry is dropped.
                mSaveRefused = false;
                if (mChosenPassword != null) {
                    mChosenPassword.zeroize();
                }
            }
            if (mIsAlphaMode) {
                if (wasRefused) {
                    // "Make it longer": back to the first entry.
                    if (mFirstPassword != null) {
                        mFirstPassword.zeroize();
                        mFirstPassword = null;
                    }
                    mPasswordEntry.setText("");
                    updateStage(Stage.Introduction);
                } else {
                    getActivity().finish();
                }
            } else if (isSupervisingProfile()) {
                // A supervision PIN has no passphrase to offer instead.
                getActivity().finish();
            } else {
                // "Use a passphrase".
                switchToGeneratedPassphrase();
            }
        }

        @Override
        public void onChosenLockSaveRefused() {
            // The lock settings want the user's agreement to the risk first. Nothing was
            // changed. Show the risk; agreeing saves again.
            if (mSaveAndFinishWorker != null) {
                getFragmentManager().beginTransaction().remove(mSaveAndFinishWorker)
                        .commitAllowingStateLoss();
                mSaveAndFinishWorker = null;
            }
            mLayout.setProgressBarShown(false);
            mSaveRefused = true;
            updateUi();
            mRiskGate.show(getRiskKind());
        }

        protected int toVisibility(boolean visibleOrGone) {
            return visibleOrGone ? View.VISIBLE : View.GONE;
        }

        private void setAutoPinConfirmOption(boolean enabled, int length) {
            if (mAutoPinConfirmOption == null) {
                return;
            }
            if (enabled
                    && !mIsAlphaMode
                    && isAutoPinConfirmPossible(length)
                    && !isSupervisingProfile()) {
                mAutoPinConfirmOption.setVisibility(View.VISIBLE);
                mAutoConfirmSecurityMessage.setVisibility(View.VISIBLE);
            } else {
                mAutoPinConfirmOption.setVisibility(View.GONE);
                mAutoConfirmSecurityMessage.setVisibility(View.GONE);
                mAutoPinConfirmOption.setChecked(false);
            }
        }

        private boolean isAutoPinConfirmPossible(int currentPinLength) {
            return currentPinLength >= MIN_AUTO_PIN_REQUIREMENT_LENGTH;
        }

        private void setOnAutoConfirmOptionClickListener() {
            if (mAutoPinConfirmOption != null) {
                mAutoPinConfirmOption.setOnClickListener((v) -> {
                    mIsAutoPinConfirmOptionSetManually = true;
                });
            }
        }

        private void setHeaderText(String text) {
            // Only set the text if it is different than the existing one to avoid announcing again.
            if (!TextUtils.isEmpty(mLayout.getHeaderText())
                    && mLayout.getHeaderText().toString().equals(text)) {
                return;
            }
            mLayout.setHeaderText(text);
        }

        private Drawable getIcon() {
            if (isSupervisingProfile()) {
                Drawable iconDrawable = getActivity().getDrawable(
                        R.drawable.ic_account_child_invert_48);
                iconDrawable.mutate();
                iconDrawable.setTintList(mLayout.getPrimaryColor());
                return iconDrawable;
            } else {
                return getActivity().getDrawable(R.drawable.ic_lock);
            }
        }

        public void afterTextChanged(Editable s) {
            // Changing the text while error displayed resets to NeedToConfirm state. Not when
            // the wrong entry was just cleared: the error stays until something is typed.
            if (mUiStage == Stage.ConfirmWrong
                    && !(mGeneratedPanel != null && s.length() == 0)) {
                mUiStage = Stage.NeedToConfirm;
            }
            // Schedule the UI update.
            mTextChangedHandler.notifyAfterTextChanged();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        private void startSaveAndFinish() {
            if (mSaveAndFinishWorker != null) {
                Log.w(TAG, "startSaveAndFinish with an existing SaveAndFinishWorker.");
                return;
            }

            ConfirmDeviceCredentialUtils.hideImeImmediately(
                    getActivity().getWindow().getDecorView());

            mPasswordEntryInputDisabler.setInputEnabled(false);
            mSaveAndFinishWorker = new SaveAndFinishWorker();
            mSaveAndFinishWorker
                    .setListener(this)
                    .setRequestGatekeeperPasswordHandle(mRequestGatekeeperPassword)
                    .setRequestWriteRepairModePassword(mRequestWriteRepairModePassword)
                    .setReturnCredentials(mReturnCredentials)
                    .setWeakerRiskAccepted(mRiskGate.isAccepted());
            mSavingStrongLock = LockStrength.of(mChosenPassword, mGeneratedPanel != null)
                    == StrengthClass.STRONG;
            // The save takes about a second: show that something is happening.
            mLayout.setProgressBarShown(true);
            mMessage.setVisibility(View.VISIBLE);
            mMessage.setText(R.string.tally_lock_saving);

            getFragmentManager().beginTransaction().add(mSaveAndFinishWorker,
                    FRAGMENT_TAG_SAVE_AND_FINISH).commit();
            getFragmentManager().executePendingTransactions();

            final Intent intent = getActivity().getIntent();
            if (mUnificationProfileId != UserHandle.USER_NULL) {
                try (LockscreenCredential profileCredential = (LockscreenCredential)
                        intent.getParcelableExtra(EXTRA_KEY_UNIFICATION_PROFILE_CREDENTIAL)) {
                    mSaveAndFinishWorker.setProfileToUnify(mUnificationProfileId,
                            profileCredential);
                }
            }
            // update the setting before triggering the password save workflow,
            // so that pinLength information is stored accordingly when setting is turned on.
            mLockPatternUtils.setAutoPinConfirm(
                    (mAutoPinConfirmOption != null && mAutoPinConfirmOption.isChecked()),
                    mUserId);

            mSaveAndFinishWorker.start(mLockPatternUtils,
                    mChosenPassword, mCurrentCredential, mUserId);
        }

        @Override
        public void onChosenLockSaveFinished(boolean wasSecureBefore, Intent resultData) {
            getActivity().setResult(RESULT_FINISHED, resultData);

            if (mChosenPassword != null) {
                mChosenPassword.zeroize();
            }
            if (mCurrentCredential != null) {
                mCurrentCredential.zeroize();
            }
            if (mFirstPassword != null) {
                mFirstPassword.zeroize();
            }

            mPasswordEntry.setText("");

            if (mSavingStrongLock && mGeneratedPanel == null && mSaveAndFinishWorker != null
                    && mSaveAndFinishWorker.wasSaved()) {
                // Said once: for a generated passphrase on its last step, for one the user
                // chose here, as the screen closes.
                Toast.makeText(getActivity(),
                        GeneratedCredentialPanel.learningPeriodNote(getActivity()),
                        Toast.LENGTH_LONG).show();
            }

            final AccessibilityManager accessibilityManager =
                    (AccessibilityManager) getActivity().getSystemService(
                            Context.ACCESSIBILITY_SERVICE);

            if (accessibilityManager.isEnabled()) {
                if (mPasswordEntry != null) {
                    mPasswordEntry.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
                    mPasswordEntry.setStateDescription(
                            mIsAlphaMode ? getString(R.string.accessibility_setup_password_complete)
                                    : getString(R.string.accessibility_setup_pin_complete));
                }
            }

            if (!wasSecureBefore) {
                Intent intent = getRedactionInterstitialIntent(getActivity());
                if (intent != null) {
                    startActivity(intent);
                }
            }

            getActivity().finish();
        }

        class TextChangedHandler extends Handler {
            private static final int ON_TEXT_CHANGED = 1;
            private static final int DELAY_IN_MILLISECOND = 100;

            /**
             * With the introduction of delay, we batch processing the text changed event to reduce
             * unnecessary UI updates.
             */
            private void notifyAfterTextChanged() {
                removeMessages(ON_TEXT_CHANGED);
                sendEmptyMessageDelayed(ON_TEXT_CHANGED, DELAY_IN_MILLISECOND);
            }

            @Override
            public void handleMessage(Message msg) {
                if (getActivity() == null) {
                    return;
                }
                if (msg.what == ON_TEXT_CHANGED) {
                    updateUi();
                }
            }
        }

        private ProfileType getProfileType() {
            UserManager userManager = getContext().createContextAsUser(UserHandle.of(mUserId),
                    /*flags=*/0).getSystemService(UserManager.class);
            if (userManager.isManagedProfile()) {
                return ProfileType.Managed;
            } else if (userManager.isPrivateProfile()) {
                return ProfileType.Private;
            } else if (userManager.isUserOfType(USER_TYPE_PROFILE_SUPERVISING)) {
                return ProfileType.Supervising;
            } else if (userManager.isProfile()) {
                return ProfileType.Other;
            }
            return ProfileType.None;
        }

        private boolean isSupervisingProfile() {
            return isSupervisingProfile(mProfileType);
        }

        static boolean isSupervisingProfile(ProfileType profileType) {
            return profileType.equals(ProfileType.Supervising);
        }
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.safetycenter;

import android.app.PendingIntent;
import android.content.Context;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceIssue;

import com.android.settings.R;
import com.android.settings.security.ScreenLockPreferenceDetailsUtils;

/** Applies {@link TallyLockStatusRule} to what {@link LockScreenSafetySource} sends. */
final class TallyLockScreenSafety {

    static final String WEAKER_LOCK_ISSUE_ID = "TallyWeakerScreenLockIssue";
    static final String WEAKER_LOCK_ISSUE_TYPE_ID = "TallyWeakerScreenLockIssueType";
    static final String CHOOSE_SCREEN_LOCK_ACTION_ID = "TallyChooseScreenLockAction";

    private final TallyLockStatusRule.Verdict mVerdict;

    private TallyLockScreenSafety(TallyLockStatusRule.Verdict verdict) {
        mVerdict = verdict;
    }

    static TallyLockScreenSafety of(Context context, ScreenLockPreferenceDetailsUtils utils,
            boolean allowedByAdmin, boolean secure) {
        return new TallyLockScreenSafety(TallyLockStatusRule.of(allowedByAdmin, secure,
                allowedByAdmin && secure ? utils.getLockLabel() : null,
                context.getResources().getInteger(R.integer.config_tally_weaker_lock_status)));
    }

    int severityLevel(int stockSeverityLevel) {
        switch (mVerdict.level) {
            case RECOMMENDATION:
                return SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION;
            case NO_CLAIM:
                return SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED;
            default:
                return stockSeverityLevel;
        }
    }

    /**
     * Adds the card for a weaker lock. Built like stock's card for no screen lock, so a
     * dismissal works the same: the card goes, the row stays yellow.
     */
    void addIssue(Context context, SafetySourceData.Builder data, PendingIntent chooseLock) {
        final int title;
        final int summary;
        switch (mVerdict.card) {
            case PIN:
                title = R.string.tally_weaker_risk_title_pin;
                summary = R.string.tally_weaker_risk_message_pin;
                break;
            case PATTERN:
                title = R.string.tally_weaker_risk_title_pattern;
                summary = R.string.tally_weaker_risk_message_pattern;
                break;
            case PASSWORD:
                title = R.string.tally_weaker_risk_title_password;
                summary = R.string.tally_weaker_risk_message_password;
                break;
            default:
                return;
        }
        final SafetySourceIssue.Action action = new SafetySourceIssue.Action.Builder(
                CHOOSE_SCREEN_LOCK_ACTION_ID,
                context.getString(R.string.lock_settings_picker_title),
                chooseLock)
                .build();
        final SafetySourceIssue.Notification notification =
                new SafetySourceIssue.Notification.Builder(
                        context.getString(title), context.getString(summary))
                        .build();
        // One id for every kind, so a dismissal holds while the lock stays weaker.
        data.addIssue(new SafetySourceIssue.Builder(
                WEAKER_LOCK_ISSUE_ID,
                context.getString(title),
                context.getString(summary),
                SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION,
                WEAKER_LOCK_ISSUE_TYPE_ID)
                .setIssueCategory(SafetySourceIssue.ISSUE_CATEGORY_DEVICE)
                .addAction(action)
                .setIssueActionability(SafetySourceIssue.ISSUE_ACTIONABILITY_MANUAL)
                .setCustomNotification(notification)
                .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED)
                .build());
    }
}

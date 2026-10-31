/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.settings.display;

import android.content.Context;
import android.database.ContentObserver;
import android.ext.settings.ExtSettings;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;

import com.android.settings.ext.BoolSettingPrefController;

/**
 * Display page: the TALLY_POCKET_CHECK switch, next to Tap to wake. Only the doze double tap has
 * the check, so it is offered where Tap to wake uses that. The check covers the single tap too,
 * so the switch can be changed while either gesture is on.
 */
public class PocketCheckPreferenceController extends BoolSettingPrefController {

    private AmbientDisplayConfiguration mAmbientConfig;
    private ContentObserver mGestureObserver;

    public PocketCheckPreferenceController(Context context, String key) {
        super(context, key, ExtSettings.TALLY_POCKET_CHECK);
    }

    @VisibleForTesting
    PocketCheckPreferenceController setConfig(AmbientDisplayConfiguration config) {
        mAmbientConfig = config;
        return this;
    }

    private AmbientDisplayConfiguration getConfig() {
        if (mAmbientConfig == null) {
            mAmbientConfig = new AmbientDisplayConfiguration(mContext);
        }
        return mAmbientConfig;
    }

    @Override
    public int getAvailabilityStatus() {
        final int status = super.getAvailabilityStatus();
        if (status != AVAILABLE) {
            return status;
        }
        return DozeDoubleTapWake.usesDozeDoubleTap(mContext, getConfig())
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    /** Tap to wake or Tap to check phone is on, as SystemUI reads them. */
    private boolean tapGestureOn() {
        final int user = UserHandle.myUserId();
        return getConfig().doubleTapGestureEnabled(user) || getConfig().tapGestureEnabled(user);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setEnabled(tapGestureOn());
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        super.onResume(owner);
        mGestureObserver = new ContentObserver(mContext.getMainThreadHandler()) {
            @Override
            public void onChange(boolean selfChange) {
                if (preference != null) {
                    updateState(preference);
                }
            }
        };
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DOZE_DOUBLE_TAP_GESTURE), false,
                mGestureObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DOZE_TAP_SCREEN_GESTURE), false,
                mGestureObserver);
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        super.onPause(owner);
        if (mGestureObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mGestureObserver);
            mGestureObserver = null;
        }
    }
}

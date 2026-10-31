/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.settings.display;

import static com.android.settings.core.BasePreferenceController.AVAILABLE;
import static com.android.settings.core.BasePreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.display.AmbientDisplayConfiguration;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreferenceCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class PocketCheckPreferenceControllerTest {

    private static final String KEY = "tally_pocket_check";

    @Mock
    private AmbientDisplayConfiguration mConfig;
    private Context mContext;
    private Resources mResources;
    private PocketCheckPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(RuntimeEnvironment.application);
        mResources = spy(mContext.getResources());
        when(mContext.getResources()).thenReturn(mResources);
        setTapToWakeSupported(true);
        when(mConfig.doubleTapSensorAvailable()).thenReturn(true);
        mController = new PocketCheckPreferenceController(mContext, KEY).setConfig(mConfig);
    }

    private void setTapToWakeSupported(boolean supported) {
        doReturn(supported).when(mResources).getBoolean(
                com.android.internal.R.bool.config_supportDoubleTapWake);
    }

    @Test
    public void getAvailabilityStatus_tapToWakeUsesDozeDoubleTap_available() {
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_withoutDoubleTapSensor_unsupported() {
        when(mConfig.doubleTapSensorAvailable()).thenReturn(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_withoutTapToWake_unsupported() {
        setTapToWakeSupported(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void updateState_neitherTapGestureOn_disablesTheSwitch() {
        final SwitchPreferenceCompat preference = new SwitchPreferenceCompat(mContext);

        mController.updateState(preference);

        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void updateState_tapToWakeOn_enablesTheSwitch() {
        when(mConfig.doubleTapGestureEnabled(anyInt())).thenReturn(true);
        final SwitchPreferenceCompat preference = new SwitchPreferenceCompat(mContext);

        mController.updateState(preference);

        assertThat(preference.isEnabled()).isTrue();
    }

    @Test
    public void updateState_onlyTapToCheckPhoneOn_enablesTheSwitch() {
        // The check covers the single tap too.
        when(mConfig.tapGestureEnabled(anyInt())).thenReturn(true);
        final SwitchPreferenceCompat preference = new SwitchPreferenceCompat(mContext);

        mController.updateState(preference);

        assertThat(preference.isEnabled()).isTrue();
    }

    @Test
    public void tapGestureChanges_whileShowing_updateTheSwitch() {
        final SwitchPreferenceCompat preference = new SwitchPreferenceCompat(mContext);
        final LifecycleOwner owner = mock(LifecycleOwner.class);
        mController.updateState(preference);
        mController.onResume(owner);
        assertThat(preference.isEnabled()).isFalse();

        // Tap to wake is switched on on the same page.
        when(mConfig.doubleTapGestureEnabled(anyInt())).thenReturn(true);
        mContext.getContentResolver().notifyChange(
                Settings.Secure.getUriFor(Settings.Secure.DOZE_DOUBLE_TAP_GESTURE), null);
        ShadowLooper.idleMainLooper();
        assertThat(preference.isEnabled()).isTrue();

        // It is switched off again.
        when(mConfig.doubleTapGestureEnabled(anyInt())).thenReturn(false);
        mContext.getContentResolver().notifyChange(
                Settings.Secure.getUriFor(Settings.Secure.DOZE_DOUBLE_TAP_GESTURE), null);
        ShadowLooper.idleMainLooper();
        assertThat(preference.isEnabled()).isFalse();

        // Paused, it no longer follows.
        mController.onPause(owner);
        when(mConfig.doubleTapGestureEnabled(anyInt())).thenReturn(true);
        mContext.getContentResolver().notifyChange(
                Settings.Secure.getUriFor(Settings.Secure.DOZE_DOUBLE_TAP_GESTURE), null);
        ShadowLooper.idleMainLooper();
        assertThat(preference.isEnabled()).isFalse();
    }

    @Test
    public void isChecked_notSet_onByDefault() {
        assertThat(mController.isChecked()).isTrue();
    }

    @Test
    public void setChecked_false_turnsOffTheSetting() {
        assertThat(mController.setChecked(false)).isTrue();

        assertThat(mController.isChecked()).isFalse();
        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TALLY_POCKET_CHECK, 1)).isEqualTo(0);
    }

    @Test
    public void setChecked_true_turnsOnTheSetting() {
        mController.setChecked(false);

        assertThat(mController.setChecked(true)).isTrue();

        assertThat(mController.isChecked()).isTrue();
        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TALLY_POCKET_CHECK, 0)).isEqualTo(1);
    }
}

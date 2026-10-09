/*
 * Copyright (C) 2026 The DiamaneOS Project
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

package com.android.settings.display;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.display.AmbientDisplayConfiguration;

import androidx.preference.TwoStatePreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TapToWakePreferenceControllerTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mContext;
    @Mock
    private AmbientDisplayConfiguration mAmbientDisplayConfiguration;
    private TapToWakePreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportDoubleTapWake)).thenReturn(true);
        mController = new TapToWakePreferenceController(mContext)
                .setConfig(mAmbientDisplayConfiguration);
    }

    @Test
    public void usesDozeDoubleTap_withDoubleTapSensor_true() {
        when(mAmbientDisplayConfiguration.doubleTapSensorAvailable()).thenReturn(true);

        assertThat(TapToWakePreferenceController.usesDozeDoubleTap(
                mContext, mAmbientDisplayConfiguration)).isTrue();
    }

    @Test
    public void usesDozeDoubleTap_withoutDoubleTapSensor_false() {
        when(mAmbientDisplayConfiguration.doubleTapSensorAvailable()).thenReturn(false);

        assertThat(TapToWakePreferenceController.usesDozeDoubleTap(
                mContext, mAmbientDisplayConfiguration)).isFalse();
    }

    @Test
    public void updateState_dozeDoubleTap_showsDozeSetting() {
        when(mAmbientDisplayConfiguration.doubleTapSensorAvailable()).thenReturn(true);
        when(mAmbientDisplayConfiguration.doubleTapGestureEnabled(anyInt())).thenReturn(true);
        final TwoStatePreference preference = mock(TwoStatePreference.class);

        mController.updateState(preference);

        verify(preference).setChecked(true);
    }
}

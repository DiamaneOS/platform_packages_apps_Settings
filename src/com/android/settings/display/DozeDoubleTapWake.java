/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Context;
import android.hardware.display.AmbientDisplayConfiguration;

/** Tap to wake on devices that report the double tap as a doze sensor. */
public final class DozeDoubleTapWake {

    private DozeDoubleTapWake() {}

    /**
     * Whether Tap to wake switches Android's doze double tap (DOZE_DOUBLE_TAP_GESTURE) instead
     * of the power HAL mode (DOUBLE_TAP_TO_WAKE): the device supports double tap to wake and
     * reports the double tap as a doze sensor (config_dozeDoubleTapSensorType). SystemUI then
     * wakes the device on it after its doze checks, such as the proximity sensor, and
     * "Double-tap to check phone" is not offered as a second switch for the same setting.
     */
    public static boolean usesDozeDoubleTap(Context context, AmbientDisplayConfiguration config) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_supportDoubleTapWake)
                && config.doubleTapSensorAvailable();
    }
}

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

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.os.SystemProperties
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// LINT.IfChange
@RunWith(RobolectricTestRunner::class)
class FirmwareReleasePreferenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        setFirmwareRelease("")
    }

    @Test
    fun isAvailable_noRelease_unavailable() {
        setFirmwareRelease("")
        assertThat(FirmwareReleasePreference().isAvailable(context)).isFalse()
    }

    @Test
    fun isAvailable_malformedValue_unavailable() {
        for (value in listOf("foobar", "16.111", "FP6.QREL.16.111.0", "16.111.0 ", "Mixed")) {
            setFirmwareRelease(value)
            assertThat(FirmwareReleasePreference().isAvailable(context)).isFalse()
        }
    }

    @Test
    fun isAvailable_setAfterTheFirstCheck_available() {
        // The vendor sets the release after boot completes; the row appears then.
        val preference = FirmwareReleasePreference()
        setFirmwareRelease("")
        assertThat(preference.isAvailable(context)).isFalse()
        setFirmwareRelease("16.111.0")
        assertThat(preference.isAvailable(context)).isTrue()
    }

    @Test
    fun getSummary_release() {
        setFirmwareRelease("16.111.0")
        val preference = FirmwareReleasePreference()
        assertThat(preference.isAvailable(context)).isTrue()
        assertThat(preference.getSummary(context)).isEqualTo("16.111.0")
    }

    @Test
    fun getSummary_mixed() {
        setFirmwareRelease("mixed")
        assertThat(FirmwareReleasePreference().getSummary(context)).isEqualTo("Mixed releases")
    }

    @Test
    fun getSummary_unknown() {
        setFirmwareRelease("unknown")
        assertThat(FirmwareReleasePreference().getSummary(context)).isEqualTo("Unknown")
    }

    private fun setFirmwareRelease(value: String) {
        SystemProperties.set("ro.vendor.diamaneos.firmware_release", value)
    }
}
// LINT.ThenChange(FirmwareReleasePreferenceControllerTest.java)

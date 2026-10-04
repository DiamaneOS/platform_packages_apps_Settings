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
class VendorSecurityPatchLevelPreferenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val vendorSecurityPatchLevelPreference = VendorSecurityPatchLevelPreference()

    @After
    fun tearDown() {
        setVendorSecurityPatch("")
    }

    @Test
    fun isAvailable_noPatch_unavailable() {
        setVendorSecurityPatch("")
        assertThat(vendorSecurityPatchLevelPreference.isAvailable(context)).isFalse()
    }

    @Test
    fun isAvailable_patchIsNotDate_unavailable() {
        setVendorSecurityPatch("foobar")
        assertThat(vendorSecurityPatchLevelPreference.isAvailable(context)).isFalse()
    }

    @Test
    fun isAvailable_hasPatch_available() {
        setVendorSecurityPatch("2026-09-05")
        assertThat(vendorSecurityPatchLevelPreference.isAvailable(context)).isTrue()
    }

    @Test
    fun getSummary_patchIsDate() {
        setVendorSecurityPatch("2026-09-05")
        assertThat(vendorSecurityPatchLevelPreference.getSummary(context))
            .isEqualTo("September 5, 2026")
    }

    private fun setVendorSecurityPatch(patch: String) {
        SystemProperties.set("ro.vendor.build.security_patch", patch)
    }
}
// LINT.ThenChange(VendorSecurityPatchLevelPreferenceControllerTest.java)

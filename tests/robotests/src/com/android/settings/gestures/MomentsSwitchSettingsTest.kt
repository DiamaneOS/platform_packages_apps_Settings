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

package com.android.settings.gestures

import com.android.settings.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Moments page's note on the kernel's microphone block. */
@RunWith(RobolectricTestRunner::class)
class MomentsSwitchSettingsTest {
    @Test
    fun kernelNote_chosenNotArmed_asksForARestart() {
        assertThat(MomentsSwitchSettings.kernelNoteFor(chosen = true, enforced = false))
            .isEqualTo(R.string.tally_moments_kernel_after_restart)
    }

    @Test
    fun kernelNote_otherChoiceButArmed_saysItStaysUntilRestart() {
        assertThat(MomentsSwitchSettings.kernelNoteFor(chosen = false, enforced = true))
            .isEqualTo(R.string.tally_moments_kernel_until_restart)
    }

    @Test
    fun kernelNote_chosenAndArmed_and_neither() {
        assertThat(MomentsSwitchSettings.kernelNoteFor(chosen = true, enforced = true))
            .isEqualTo(R.string.tally_moments_kernel_on)
        assertThat(MomentsSwitchSettings.kernelNoteFor(chosen = false, enforced = false)).isNull()
    }
}

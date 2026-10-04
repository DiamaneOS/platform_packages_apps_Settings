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
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.utils.getLocale
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.preference.PreferenceBinding

// LINT.IfChange
class VendorSecurityPatchLevelPreference :
    PersistentPreference<String>,
    PreferenceMetadata,
    PreferenceAvailabilityProvider,
    PreferenceSummaryProvider,
    PreferenceBinding {

    private var currentPatch: String? = null

    override val key: String
        get() = KEY

    override val purpose: Int
        get() = R.string.tally_vendor_security_patch_purpose

    override val title: Int
        get() = R.string.tally_vendor_security_patch

    override val availabilityDescription =
        "The vendor partition must set a valid security patch level."

    override fun getAvailabilityStability() = PreconditionStability.STABLE_UNTIL_APK_UPDATE

    override fun isAvailable(context: Context) = context.getPatch().isNotEmpty()

    override val supportsWrite = false

    override val valueType = String::class.javaObjectType

    override fun storage(context: Context): KeyValueStore = createSummaryStorage(context, key)

    override fun getSummary(context: Context) = context.getPatch()

    private fun Context.getPatch(): String {
        currentPatch?.let { return it }
        val patch =
            VendorSecurityPatchLevelPreferenceController.getVendorSecurityPatch(getLocale()) ?: ""
        currentPatch = patch
        return patch
    }

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        preference.isSelectable = false
        preference.isCopyingEnabled = true
    }

    companion object {
        const val KEY = "vendor_security_key"
    }
}
// LINT.ThenChange(VendorSecurityPatchLevelPreferenceController.java)

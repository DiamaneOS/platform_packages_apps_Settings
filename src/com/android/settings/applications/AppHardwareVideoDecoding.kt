/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.applications

import android.content.Context
import android.content.pm.ApplicationInfo
import android.ext.settings.app.AswUseHardwareVideoDecoding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.android.settings.R
import com.android.settings.security.HardwareVideoDecoding
import com.android.settings.spa.app.appinfo.AswPreference
import com.android.settingslib.widget.FooterPreference

/**
 * Hardware video decoding per app, where the device offers Selected apps (Exploit protection >
 * Hardware video decoding). The switch can be changed only while that choice is Selected apps.
 * Never and All video set it for every app.
 */
object AswAdapterUseHardwareVideoDecoding : AswAdapter<AswUseHardwareVideoDecoding>() {

    override fun getAppSwitch() = AswUseHardwareVideoDecoding.I

    override fun getAswTitle(ctx: Context) = ctx.getText(R.string.tally_hw_video_decoding_title)

    override fun getOnTitle(ctx: Context) = ctx.getText(R.string.app_permission_summary_allowed)
    override fun getOffTitle(ctx: Context) = ctx.getText(R.string.app_permission_summary_not_allowed)

    override fun getDetailFragmentClass() = AppHardwareVideoDecodingFragment::class
}

/** The App info entry, on devices that offer Selected apps. */
@Composable
fun AppHardwareVideoDecodingPreference(app: ApplicationInfo) {
    val context = LocalContext.current
    if (!HardwareVideoDecoding.offersSelectedApps(context)) {
        return
    }
    AswPreference(context, app, AswAdapterUseHardwareVideoDecoding)
}

class AppHardwareVideoDecodingFragment : AswAppInfoFragment<AswUseHardwareVideoDecoding>() {

    override fun getAswAdapter() = AswAdapterUseHardwareVideoDecoding

    // Allowing the hardware decoder weakens the app's protection, so ask first, with
    // GrapheneOS's exploit protection warning.
    override fun completeStateChange(newEntryId: Int, curValue: Boolean,
                                     stateChangeAction: Runnable) {
        if (newEntryId == AswAppInfoFragment.ID_ON && !curValue) {
            AswExploitProtectionFragment.getExploitProtectionWarningOnDisable(
                    requireContext(), stateChangeAction).show()
        } else {
            stateChangeAction.run()
        }
    }

    // Set for every app by the device-wide choice.
    override fun getSummaryForImmutabilityReason(ir: Int): CharSequence? =
        getText(R.string.tally_hw_video_decoding_app_set_for_all)

    override fun updateFooter(fp: FooterPreference) {
        fp.setTitle(R.string.tally_hw_video_decoding_app_footer)
    }
}

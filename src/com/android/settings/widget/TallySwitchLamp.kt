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

package com.android.settings.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.PrimarySwitchPreference
import com.android.settingslib.RestrictedSwitchPreference
import com.android.settingslib.widget.MainSwitchBar
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.TallyFrameworkSwitch
import com.android.settingslib.widget.TallySwitch
import com.android.settingslib.widget.mainswitch.R as MainSwitchR

/**
 * A switch whose change takes effect later (Wi-Fi, Bluetooth, NFC): its Tally switch moves at once
 * and lights its lamp only once the system confirms that what it controls is on, as the Tally
 * prototype's switch does. Only the lamp follows [confirmedOn]; checking, clicks, restrictions and
 * accessibility stay the switch's own.
 */
interface TallyConfirmedSwitch {
    /**
     * Whether the system reports what the switch controls as on, not only asked for: the lamp
     * lights only while the switch is checked and this is true, so a switch that is not confirmed
     * shows unlit. Null lights the lamp with the checked state.
     */
    var confirmedOn: Boolean?
}

/** Sets a Tally switch's lamp confirmation (see [TallySwitch.confirmedOn]). */
object TallySwitchLamp {
    /**
     * Sets [confirmedOn] on [switchView] when it is a Tally switch; other switches light as ever.
     */
    @JvmStatic
    fun show(switchView: View?, confirmedOn: Boolean?) {
        when (switchView) {
            is TallySwitch -> switchView.confirmedOn = confirmedOn
            is TallyFrameworkSwitch -> switchView.confirmedOn = confirmedOn
        }
    }

    /**
     * Sets [confirmedOn] on [preference] when it is a [TallyConfirmedSwitch], for controllers that
     * hold their preference by a stock type.
     */
    @JvmStatic
    fun confirm(preference: Any?, confirmedOn: Boolean?) {
        (preference as? TallyConfirmedSwitch)?.confirmedOn = confirmedOn
    }
}

/** A [RestrictedSwitchPreference] whose switch lights once the system confirms its state. */
open class TallyRestrictedSwitchPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) :
    RestrictedSwitchPreference(context, attrs), TallyConfirmedSwitch {

    override var confirmedOn: Boolean? = null
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        TallySwitchLamp.show(
            holder.findViewById(androidx.preference.R.id.switchWidget),
            confirmedOn,
        )
    }
}

/** A [SwitchPreferenceCompat] whose switch lights once the system confirms its state. */
open class TallySwitchPreferenceCompat
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) :
    SwitchPreferenceCompat(context, attrs), TallyConfirmedSwitch {

    override var confirmedOn: Boolean? = null
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        TallySwitchLamp.show(
            holder.findViewById(androidx.preference.R.id.switchWidget),
            confirmedOn,
        )
    }
}

/**
 * A [PrimarySwitchPreference] (a row that opens a page, with its own switch) whose switch lights
 * once the system confirms its state.
 */
open class TallyPrimarySwitchPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) :
    PrimarySwitchPreference(context, attrs), TallyConfirmedSwitch {

    override var confirmedOn: Boolean? = null
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        TallySwitchLamp.show(
            holder.findViewById(androidx.preference.R.id.switchWidget),
            confirmedOn,
        )
    }
}

/** A [MainSwitchPreference] (a main switch row in the list) that lights once confirmed. */
open class TallyMainSwitchPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) :
    MainSwitchPreference(context, attrs), TallyConfirmedSwitch {

    override var confirmedOn: Boolean? = null
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val bar = holder.findViewById(MainSwitchR.id.settingslib_main_switch_bar) as? MainSwitchBar
        TallySwitchLamp.show(bar?.findViewById(android.R.id.switch_widget), confirmedOn)
    }
}

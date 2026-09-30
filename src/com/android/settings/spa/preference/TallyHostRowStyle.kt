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

package com.android.settings.spa.preference

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.DimenRes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.settingslib.spa.widget.preference.HostRowStyle
import com.android.settingslib.widget.SettingsThemeHelper
import com.android.settingslib.widget.theme.R as ThemeR
import kotlin.math.min

/**
 * Tally: the SettingsLib view rows' metrics (SettingsTheme's tally_rows.xml) and type (the theme's
 * list item text appearances), for a Compose row among them (a ComposePreference), so it is drawn
 * as the Tally row. Null outside the expressive theme, where the rows are stock.
 *
 * SettingsLib's list pads such a row by its NormalPaddingMixin padding on both sides, inside the
 * card, so the row's own start and end space make up the rest of the view rows' inset.
 */
internal fun tallyHostRowStyle(context: Context): HostRowStyle? {
    if (!SettingsThemeHelper.isExpressiveTheme(context)) return null
    val listPadding = context.dp(ThemeR.dimen.settingslib_expressive_space_small1)
    val inset = context.dp(ThemeR.dimen.settingslib_tally_row_inset) - listPadding
    return HostRowStyle(
        minHeight = context.dp(ThemeR.dimen.settingslib_tally_row_min_height),
        paddingStart = inset,
        paddingEnd = inset,
        paddingVertical = context.dp(ThemeR.dimen.settingslib_tally_row_padding_vertical),
        iconSize = context.dp(ThemeR.dimen.settingslib_tally_icon_size),
        iconGap = context.dp(ThemeR.dimen.settingslib_tally_icon_gap),
        titleStyle =
            context.textStyle(
                android.R.attr.textAppearanceListItem,
                ThemeR.dimen.settingslib_tally_item_line_height,
            ),
        bodyStyle =
            context.textStyle(
                android.R.attr.textAppearanceListItemSecondary,
                ThemeR.dimen.settingslib_tally_body_line_height,
            ),
    )
}

private fun Context.dp(@DimenRes id: Int): Dp =
    (resources.getDimension(id) / resources.displayMetrics.density).dp

/**
 * The family, weight, size and tracking of the theme's [appearance] (Tally's item or body type:
 * Sofia Sans), with [lineHeight], in sp so they follow the text size. The family also carries the
 * weight Bold text asks for, so neither is synthesised. The colours stay Spa's, which are the same
 * roles (on surface, on surface variant).
 */
private fun Context.textStyle(@AttrRes appearance: Int, @DimenRes lineHeight: Int): TextStyle {
    val value = TypedValue()
    if (!theme.resolveAttribute(appearance, value, true) || value.resourceId == 0) {
        return TextStyle.Default
    }
    val attrs = obtainStyledAttributes(value.resourceId, TEXT_ATTRS)
    try {
        val weight = attrs.getInt(TEXT_ATTRS.indexOf(android.R.attr.textFontWeight), NORMAL_WEIGHT)
        val familyName = attrs.getString(TEXT_ATTRS.indexOf(android.R.attr.fontFamily))
        val family =
            familyName?.let {
                FontFamily(
                    Font(DeviceFontFamilyName(it), FontWeight(weight)),
                    Font(
                        DeviceFontFamilyName(it),
                        FontWeight(min(weight + BOLD_TEXT_ADJUSTMENT, MAX_WEIGHT)),
                    ),
                )
            }
        return TextStyle(
            fontFamily = family,
            fontWeight = FontWeight(weight),
            fontSize =
                TypedValue()
                    .also { attrs.getValue(TEXT_ATTRS.indexOf(android.R.attr.textSize), it) }
                    .toSp(),
            letterSpacing = attrs.getFloat(TEXT_ATTRS.indexOf(android.R.attr.letterSpacing), 0f).em,
            lineHeight = TypedValue().also { resources.getValue(lineHeight, it, true) }.toSp(),
        )
    } finally {
        attrs.recycle()
    }
}

private fun TypedValue.toSp(): TextUnit =
    if (type == TypedValue.TYPE_DIMENSION && complexUnit == TypedValue.COMPLEX_UNIT_SP) {
        TypedValue.complexToFloat(data).sp
    } else {
        TextUnit.Unspecified
    }

// Sorted, as obtainStyledAttributes requires.
private val TEXT_ATTRS =
    intArrayOf(
            android.R.attr.textSize,
            android.R.attr.fontFamily,
            android.R.attr.letterSpacing,
            android.R.attr.textFontWeight,
        )
        .apply { sort() }

private const val NORMAL_WEIGHT = 400
/** How much heavier Bold text (Settings › Display) makes text, as the platform. */
private const val BOLD_TEXT_ADJUSTMENT = 300
private const val MAX_WEIGHT = 1000

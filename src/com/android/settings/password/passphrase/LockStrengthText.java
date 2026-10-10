/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.password.passphrase.StrengthComparison.Choice;
import com.android.settings.password.passphrase.StrengthComparison.Row;
import com.android.settingslib.utils.StringUtil;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * The words for strength figures. A time to guess never appears without "about", "less than"
 * or "more than", and the assumptions it rests on are one fixed sentence.
 */
public final class LockStrengthText {

    private LockStrengthText() {}

    /** An entropy in bits with one decimal, such as "77.5". */
    public static String bits(double entropyBits) {
        final NumberFormat format = NumberFormat.getNumberInstance();
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        // Rounded down, like the times: the figure shown is never above the real one.
        return format.format(Math.floor(entropyBits * 10) / 10);
    }

    /** An estimate in words, such as "about 110 billion years". */
    public static String time(Context context, GuessTimeEstimate estimate) {
        return estimate.describe((wording, amount, unit) -> {
            switch (wording) {
                case LESS_THAN:
                    return context.getString(R.string.tally_guess_time_less_than_second);
                case MORE_THAN:
                    return context.getString(R.string.tally_guess_time_more_than_billions);
                default:
                    final Map<String, Object> arguments = new HashMap<>();
                    arguments.put("count", amount);
                    return StringUtil.getIcuPluralsString(context, arguments, unitText(unit));
            }
        });
    }

    private static int unitText(GuessTimeEstimate.Unit unit) {
        switch (unit) {
            case SECONDS:
                return R.string.tally_guess_time_seconds;
            case MINUTES:
                return R.string.tally_guess_time_minutes;
            case HOURS:
                return R.string.tally_guess_time_hours;
            case DAYS:
                return R.string.tally_guess_time_days;
            case YEARS:
                return R.string.tally_guess_time_years;
            case THOUSANDS_OF_YEARS:
                return R.string.tally_guess_time_thousand_years;
            case MILLIONS_OF_YEARS:
                return R.string.tally_guess_time_million_years;
            default:
                return R.string.tally_guess_time_billion_years;
        }
    }

    /** The sentence that says what every estimate on these screens assumes. */
    public static String assumptions(Context context) {
        final NumberFormat format = NumberFormat.getIntegerInstance();
        return context.getString(R.string.tally_strength_assumptions,
                format.format(PlaceholderGuessingAssumptions.MACHINES),
                format.format(PlaceholderGuessingAssumptions.GUESSES_PER_SECOND_PER_MACHINE));
    }

    /**
     * The one row under a generated passphrase or PIN: the estimated time to guess it, said to
     * be an estimate. "Estimate: about 110 billion years to guess".
     */
    public static String strengthLine(Context context, double entropyBits) {
        final String time = time(context, CredentialStrength.estimateTimeToGuess(
                entropyBits, PlaceholderGuessingAssumptions.get()));
        return context.getString(R.string.tally_strength_line, time);
    }

    /**
     * What is behind the strength line: the exact strength, what the estimate assumes, and the
     * given lock choices side by side on the same assumptions.
     */
    public static String details(Context context, double entropyBits, Choice... choices) {
        final StringBuilder text = new StringBuilder();
        text.append(context.getString(R.string.tally_strength_details_bits, bits(entropyBits)));
        text.append("\n\n").append(assumptions(context));
        text.append("\n\n").append(context.getString(R.string.tally_compare_heading));
        for (Row row : StrengthComparison.rows(PlaceholderGuessingAssumptions.get(), choices)) {
            text.append('\n').append(line(context, row));
        }
        return text.toString();
    }

    private static String line(Context context, Row row) {
        final String time = time(context, row.estimate);
        switch (row.choice) {
            case PIN_6_DIGITS:
                return context.getString(R.string.tally_compare_pin6, time);
            case RANDOM_PIN_20:
                return context.getString(R.string.tally_compare_random_pin, time);
            case WORDS_5:
                return context.getString(R.string.tally_compare_words, 5, time);
            case WORDS_6:
                return context.getString(R.string.tally_compare_words, 6, time);
            case WORDS_7:
                return context.getString(R.string.tally_compare_words, 7, time);
            case WORDS_8:
                return context.getString(R.string.tally_compare_words, 8, time);
            default:
                // A pattern is not offered for new locks, so it has no line.
                throw new IllegalArgumentException("no text for " + row.choice);
        }
    }
}

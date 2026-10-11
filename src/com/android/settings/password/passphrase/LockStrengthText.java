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
import java.util.List;
import java.util.Map;

/**
 * The words for strength figures. A time to guess never appears without "about" or "more
 * than", or it is just "seconds"; what it rests on is said in four fixed sentences.
 */
public final class LockStrengthText {

    private LockStrengthText() {}

    // The locks in the details, in the order shown. All figures are for values picked at
    // random, on the assumptions of SetupGuessingAssumptions.
    private static final Choice[] COMPARED = {
        Choice.PIN_6_DIGITS, Choice.RANDOM_PIN_12, Choice.RANDOM_PIN_20,
        Choice.WORDS_5, Choice.WORDS_6, Choice.WORDS_7, Choice.WORDS_8,
    };

    /** An estimate in words, such as "about 3.4 billion years", or "seconds". */
    public static String time(Context context, GuessTimeEstimate estimate) {
        return estimate.describe((wording, amount, unit) -> {
            switch (wording) {
                case LESS_THAN:
                    // Under a minute: no figure.
                    return context.getString(R.string.tally_guess_time_under_minute);
                case MORE_THAN:
                    return context.getString(R.string.tally_guess_time_more_than_trillion);
                default:
                    final Map<String, Object> arguments = new HashMap<>();
                    arguments.put("count", amount);
                    return StringUtil.getIcuPluralsString(context, arguments, unitText(unit));
            }
        });
    }

    private static int unitText(GuessTimeEstimate.Unit unit) {
        switch (unit) {
            case MINUTES:
                return R.string.tally_guess_time_minutes;
            case HOURS:
                return R.string.tally_guess_time_hours;
            case DAYS:
                return R.string.tally_guess_time_days;
            case YEARS:
                // Also thousands of years, written out: "about 440,000 years".
                return R.string.tally_guess_time_years;
            case MILLIONS_OF_YEARS:
                return R.string.tally_guess_time_million_years;
            default:
                return R.string.tally_guess_time_billion_years;
        }
    }

    /**
     * What every estimate on these screens rests on, in four sentences: whom it is about, the
     * assumed rate with what that takes today, that it holds for random secrets only, and
     * that it is an average. The numbers are the ones the estimate is made with.
     */
    public static String caveats(Context context) {
        final NumberFormat format = NumberFormat.getIntegerInstance();
        return context.getString(R.string.tally_strength_caveat_attacker)
                + "\n\n" + context.getString(R.string.tally_strength_caveat_rate,
                        format.format(SetupGuessingAssumptions.GUESSES_PER_SECOND),
                        format.format(SetupGuessingAssumptions.GRAPHICS_CARDS_NEEDED),
                        format.format(SetupGuessingAssumptions.PURPOSE_BUILT_SPEEDUP))
                + "\n\n" + context.getString(R.string.tally_strength_caveat_random)
                + "\n\n" + context.getString(R.string.tally_strength_caveat_average);
    }

    /**
     * The one row under a generated passphrase or PIN: the estimated time to guess it, said to
     * be an estimate. "Estimate: about 3.4 billion years to guess".
     */
    public static String strengthLine(Context context, double entropyBits) {
        final String time = time(context, CredentialStrength.estimateTimeToGuess(
                entropyBits, SetupGuessingAssumptions.get()));
        return context.getString(R.string.tally_strength_line, time);
    }

    /**
     * What is behind the strength line: what the estimate rests on, and the lock choices side
     * by side on the same assumptions, one line each.
     */
    public static String details(Context context) {
        final StringBuilder text = new StringBuilder(caveats(context));
        text.append("\n\n").append(context.getString(R.string.tally_compare_heading));
        final List<Row> rows = StrengthComparison.rows(SetupGuessingAssumptions.get(), COMPARED);
        for (int i = 0; i < rows.size(); i++) {
            final Row row = rows.get(i);
            final Row next = i + 1 < rows.size() ? rows.get(i + 1) : null;
            if (row.choice == Choice.WORDS_7 && next != null && next.choice == Choice.WORDS_8
                    && time(context, row.estimate).equals(time(context, next.estimate))) {
                // Both beyond the scale: one line for the two.
                text.append('\n').append(context.getString(
                        R.string.tally_compare_words_range, 7, 8, time(context, row.estimate)));
                i++;
            } else {
                text.append('\n').append(line(context, row));
            }
        }
        return text.toString();
    }

    private static String line(Context context, Row row) {
        final String time = time(context, row.estimate);
        switch (row.choice) {
            case PIN_6_DIGITS:
                return context.getString(R.string.tally_compare_pin6, time);
            case RANDOM_PIN_12:
                return context.getString(R.string.tally_compare_random_pin, 12, time);
            case RANDOM_PIN_20:
                return context.getString(R.string.tally_compare_random_pin, 20, time);
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

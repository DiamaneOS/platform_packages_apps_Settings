/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import java.util.Arrays;

/**
 * Lays a generated passphrase or PIN out for reading and copying to paper. Works on char arrays
 * only: the caller wipes the result when it is no longer shown.
 */
public final class SecretDisplay {

    /** Digits of a PIN are shown in groups of this size. */
    public static final int DIGIT_GROUP = 4;

    private SecretDisplay() {}

    /**
     * One word per line, numbered: "1  abacus", "2  zoom". The words of {@code phrase} are
     * separated by single spaces.
     */
    public static char[] numberedWords(char[] phrase) {
        int words = 1;
        for (char c : phrase) {
            if (c == PassphraseGenerator.SEPARATOR) {
                words++;
            }
        }
        // Each word gets its number (one digit up to nine words, two beyond), two spaces, and
        // a line break in place of its separator.
        final char[] buffer = new char[phrase.length + words * 4];
        int length = 0;
        int number = 1;
        boolean atWordStart = true;
        for (char c : phrase) {
            if (atWordStart) {
                if (number >= 10) {
                    buffer[length++] = (char) ('0' + number / 10 % 10);
                }
                buffer[length++] = (char) ('0' + number % 10);
                buffer[length++] = ' ';
                buffer[length++] = ' ';
                atWordStart = false;
            }
            if (c == PassphraseGenerator.SEPARATOR) {
                buffer[length++] = '\n';
                number++;
                atWordStart = true;
            } else {
                buffer[length++] = c;
            }
        }
        final char[] result = Arrays.copyOf(buffer, length);
        wipe(buffer);
        return result;
    }

    /** The digits in groups of {@link #DIGIT_GROUP}, separated by spaces: "1234 5678 9012". */
    public static char[] groupedDigits(char[] digits) {
        if (digits.length == 0) {
            return new char[0];
        }
        final char[] result = new char[digits.length + (digits.length - 1) / DIGIT_GROUP];
        int length = 0;
        for (int i = 0; i < digits.length; i++) {
            if (i > 0 && i % DIGIT_GROUP == 0) {
                result[length++] = ' ';
            }
            result[length++] = digits[i];
        }
        return result;
    }

    /** Overwrites {@code chars} with zeros. */
    public static void wipe(char[] chars) {
        Arrays.fill(chars, '\0');
    }
}

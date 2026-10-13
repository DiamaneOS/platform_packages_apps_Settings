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

package com.android.settings.wifi.addappnetworks;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * DiamaneOS: the network in the text of a Wi-Fi QR code, "WIFI:T:WPA;S:name;P:password;H:true;;".
 * Fields come in any order, and a backslash makes the next character part of a value. Reads the
 * fields as WifiUriParser does for Settings' own scanner, but logs nothing.
 */
final class WifiCode {
    static final int OPEN = 0;
    static final int WPA = 1;
    static final int SAE = 2;

    private static final String PREFIX = "WIFI:";
    private static final int MAX_SSID_BYTES = 32;

    final int security;
    final String ssid;
    /** Null for an open network. */
    @Nullable
    final String password;
    final boolean hidden;

    private WifiCode(int security, String ssid, @Nullable String password, boolean hidden) {
        this.security = security;
        this.ssid = ssid;
        this.password = password;
        this.hidden = hidden;
    }

    /** Returns null unless the text is a code for a WPA/WPA2, WPA3 or open network. */
    @Nullable
    static WifiCode parse(@Nullable String text) {
        if (text == null || !text.startsWith(PREFIX)) {
            return null;
        }
        String type = "";
        String ssid = "";
        String password = "";
        boolean hidden = false;
        int i = PREFIX.length();
        while (i < text.length()) {
            final char key = text.charAt(i);
            if (key == ';' || Character.isWhitespace(key)) {
                i++;
                continue;
            }
            final boolean hasKey = text.startsWith(":", i + 1);
            final StringBuilder value = new StringBuilder();
            int j = hasKey ? i + 2 : i;
            for (; j < text.length(); j++) {
                char c = text.charAt(j);
                if (c == ';') {
                    break;
                }
                if (c == '\\') {
                    if (++j == text.length()) {
                        break;
                    }
                    c = text.charAt(j);
                }
                value.append(c);
            }
            if (hasKey) {
                switch (key) {
                    case 'T':
                        type = value.toString();
                        break;
                    case 'S':
                        ssid = value.toString();
                        break;
                    case 'P':
                        password = value.toString();
                        break;
                    case 'H':
                        hidden = "true".equalsIgnoreCase(value.toString());
                        break;
                    default:
                        break;
                }
            }
            i = j + 1;
        }

        if (ssid.isEmpty() || ssid.getBytes(StandardCharsets.UTF_8).length > MAX_SSID_BYTES) {
            return null;
        }
        if (type.isEmpty() || type.equals("nopass")) {
            return new WifiCode(OPEN, ssid, null, hidden);
        }
        if (password.isEmpty()) {
            return null;
        }
        if (type.startsWith("SAE")) {
            return new WifiCode(SAE, ssid, password, hidden);
        }
        if (type.startsWith("WPA") && !type.contains("EAP")) {
            return new WifiCode(WPA, ssid, password, hidden);
        }
        // WEP, enterprise networks, wireless debugging's pairing codes.
        return null;
    }
}

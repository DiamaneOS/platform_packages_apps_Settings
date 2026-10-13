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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Plain JUnit: {@link WifiCode} uses no Android classes. */
public class WifiCodeTest {

    @Test
    public void parse_wpa() {
        WifiCode code = WifiCode.parse("WIFI:T:WPA;S:mynetwork;P:mypass123;;");

        assertEquals(WifiCode.WPA, code.security);
        assertEquals("mynetwork", code.ssid);
        assertEquals("mypass123", code.password);
        assertFalse(code.hidden);
    }

    @Test
    public void parse_wpa3Hidden() {
        WifiCode code = WifiCode.parse("WIFI:T:SAE;S:mynetwork;P:mypass123;H:true;;");

        assertEquals(WifiCode.SAE, code.security);
        assertEquals("mypass123", code.password);
        assertTrue(code.hidden);
    }

    @Test
    public void parse_open() {
        for (String text : new String[] {
                "WIFI:T:nopass;S:cafe;;", "WIFI:S:cafe;;", "WIFI:T:nopass;S:cafe;P:unused;;"}) {
            WifiCode code = WifiCode.parse(text);

            assertEquals(WifiCode.OPEN, code.security);
            assertEquals("cafe", code.ssid);
            assertNull(code.password);
        }
    }

    @Test
    public void parse_anyOrderAndUnknownFields() {
        WifiCode code = WifiCode.parse("WIFI:S:mynetwork;R:1;PH2:MSCHAPV2;P:mypass123;T:WPA2;;");

        assertEquals(WifiCode.WPA, code.security);
        assertEquals("mynetwork", code.ssid);
        assertEquals("mypass123", code.password);
    }

    @Test
    public void parse_escapedCharacters() {
        WifiCode code = WifiCode.parse("WIFI:T:WPA;S:a\\;b\\:c\\\\d\\,e\\\"f;P:p\\;w:x\\\\;;");

        assertEquals("a;b:c\\d,e\"f", code.ssid);
        assertEquals("p;w:x\\", code.password);
    }

    @Test
    public void parse_notAWifiCode() {
        assertNull(WifiCode.parse(null));
        assertNull(WifiCode.parse(""));
        assertNull(WifiCode.parse("https://example.com/"));
        assertNull(WifiCode.parse("wifi:T:WPA;S:mynetwork;P:mypass123;;"));
        assertNull(WifiCode.parse("DPP:C:81/1;K:key;;"));
    }

    @Test
    public void parse_missingOrOverlongName() {
        assertNull(WifiCode.parse("WIFI:T:WPA;P:mypass123;;"));
        assertNull(WifiCode.parse("WIFI:T:nopass;S:;;"));
        assertEquals(32, WifiCode.parse("WIFI:S:12345678901234567890123456789012;;").ssid.length());
        // 33 bytes: as 33 letters, and as 11 characters of 3 bytes each.
        assertNull(WifiCode.parse("WIFI:S:123456789012345678901234567890123;;"));
        assertNull(WifiCode.parse("WIFI:S:" + "€".repeat(11) + ";;"));
    }

    @Test
    public void parse_missingPassword() {
        assertNull(WifiCode.parse("WIFI:T:WPA;S:mynetwork;;"));
        assertNull(WifiCode.parse("WIFI:T:SAE;S:mynetwork;P:;;"));
    }

    @Test
    public void parse_otherNetworkTypes() {
        assertNull(WifiCode.parse("WIFI:T:WEP;S:mynetwork;P:abcde;;"));
        assertNull(WifiCode.parse("WIFI:T:WPA2-EAP;S:mynetwork;I:me;P:mypass123;;"));
        assertNull(WifiCode.parse("WIFI:T:ADB;S:ADB_WIFI_abc;P:mypass123;;"));
    }
}

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

package com.android.settings.wifi.tether;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.settings.wifi.tether.TallyHotspotClients.Client;
import com.android.settings.wifi.tether.TallyHotspotClients.Lease;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Plain JUnit: {@link TallyHotspotClients} uses no Android classes. */
public class TallyHotspotClientsTest {
    private static final String LAPTOP = "a2:11:22:33:44:55";
    private static final String PHONE = "36:aa:bb:cc:dd:ee";
    private static final String TABLET = "02:00:00:00:00:01";

    private static List<Client> list(List<String> macs, Lease... leases) {
        return TallyHotspotClients.list(macs, Arrays.asList(leases));
    }


    @Test
    public void list_connectedDeviceWithLease_hasNameAndAddress() {
        assertEquals(
                Arrays.asList(new Client(LAPTOP, "thinkpad", "192.168.43.25")),
                list(Arrays.asList(LAPTOP), new Lease(LAPTOP, "192.168.43.25", "thinkpad", 100)));
    }



    @Test
    public void list_leaseOfDeviceThatIsNotConnected_isIgnored() {
        // A device that left, or a USB tethering client, still holds a lease.
        assertEquals(
                Arrays.asList(new Client(PHONE, null, null)),
                list(Arrays.asList(PHONE), new Lease(LAPTOP, "192.168.43.25", "thinkpad", 100)));
    }


    @Test
    public void list_twoLeases_newestGivesTheAddress() {
        assertEquals(
                Arrays.asList(new Client(LAPTOP, "new-name", "192.168.43.26")),
                list(Arrays.asList(LAPTOP),
                        new Lease(LAPTOP, "192.168.43.25", "old-name", 100),
                        new Lease(LAPTOP, "192.168.43.26", "new-name", 200)));
    }

    @Test
    public void list_newestLeaseHasNoName_olderNameIsUsed() {
        assertEquals(
                Arrays.asList(new Client(LAPTOP, "thinkpad", "192.168.43.26")),
                list(Arrays.asList(LAPTOP),
                        new Lease(LAPTOP, "192.168.43.26", " ", 200),
                        new Lease(LAPTOP, "192.168.43.25", "thinkpad", 100)));
    }


    @Test
    public void list_order_namedByNameThenUnnamedByMac() {
        final List<Client> clients = list(
                Arrays.asList("0a:00:00:00:00:09", PHONE, LAPTOP, TABLET, "0a:00:00:00:00:01"),
                new Lease(LAPTOP, "192.168.43.25", "thinkpad", 100),
                new Lease(TABLET, "192.168.43.30", "Anna's tablet", 100),
                new Lease(PHONE, "192.168.43.7", "anna's tablet", 100));
        assertEquals(Arrays.asList(
                new Client(TABLET, "Anna's tablet", "192.168.43.30"),
                new Client(PHONE, "anna's tablet", "192.168.43.7"),
                new Client(LAPTOP, "thinkpad", "192.168.43.25"),
                new Client("0a:00:00:00:00:01", null, null),
                new Client("0a:00:00:00:00:09", null, null)), clients);
    }


    @Test
    public void list_macCaseDiffersBetweenSources_stillJoined() {
        assertEquals(
                Arrays.asList(new Client(LAPTOP, "thinkpad", "192.168.43.25")),
                list(Arrays.asList("A2:11:22:33:44:55"),
                        new Lease(LAPTOP, "192.168.43.25", "thinkpad", 100)));
    }

    @Test
    public void list_brokenEntries_areSkipped() {
        assertEquals(
                Arrays.asList(new Client(PHONE, null, null)),
                list(Arrays.asList(null, "", PHONE),
                        null,
                        new Lease(null, "192.168.43.9", "ghost", 100),
                        new Lease(PHONE, "", null, 100)));
    }


    @Test
    public void cleanName_nothingToShow_isNull() {
        assertNull(TallyHotspotClients.cleanName(null));
        assertNull(TallyHotspotClients.cleanName(""));
        assertNull(TallyHotspotClients.cleanName(" \t\n "));
        assertNull(TallyHotspotClients.cleanName("\u0000​‮"));
    }

    @Test
    public void cleanName_whiteSpace_becomesSingleSpaces() {
        assertEquals("my laptop", TallyHotspotClients.cleanName("  my \r\n\t laptop "));
        assertEquals("a b", TallyHotspotClients.cleanName("a b"));
    }

    @Test
    public void cleanName_controlAndBidiCharacters_areDropped() {
        // A right-to-left override would let a name reorder the text shown after it.
        assertEquals("Annagpj.exe", TallyHotspotClients.cleanName("Anna‮gpj.exe"));
        assertEquals("phone", TallyHotspotClients.cleanName("\u001bphone\u0007"));
        assertEquals("ab", TallyHotspotClients.cleanName("a‍﻿⁦b⁩"));
        assertEquals("ab", TallyHotspotClients.cleanName("a\ud800b"));
        assertEquals("ab", TallyHotspotClients.cleanName("ab"));
    }

    @Test
    public void cleanName_longName_isCutWithEllipsis() {
        final StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 100; i++) longName.append('x');
        final String cut = TallyHotspotClients.cleanName(longName.toString());
        assertEquals(TallyHotspotClients.MAX_NAME_LENGTH + 1, cut.length());
        assertTrue(cut.endsWith("…"));

        final StringBuilder exact = new StringBuilder();
        for (int i = 0; i < TallyHotspotClients.MAX_NAME_LENGTH; i++) exact.append('x');
        assertEquals(exact.toString(), TallyHotspotClients.cleanName(exact.toString()));
    }

    @Test
    public void cleanName_longNameOfPairedCharacters_isNotCutInsideOne() {
        final StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 60; i++) longName.appendCodePoint(0x1F600);
        final String cut = TallyHotspotClients.cleanName(longName.toString());
        assertEquals(TallyHotspotClients.MAX_NAME_LENGTH + 1,
                cut.codePointCount(0, cut.length()));
        assertEquals(0x1F600, cut.codePointBefore(cut.length() - 1));
    }
}

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Works out who is on the Wi-Fi hotspot. The Wi-Fi service decides who is listed; Tethering's
 * address leases add the IP address and the name each device claims. A name is never proof.
 *
 * <p>No Android classes, so the rules run in plain JUnit.
 */
final class TallyHotspotClients {
    /** Longest name shown; a longer one is cut and ends in "…". */
    static final int MAX_NAME_LENGTH = 40;

    private TallyHotspotClients() {
    }

    /** One address lease, as Tethering reports it. */
    static final class Lease {
        final String mac;
        final String address;
        final String claimedName;
        final long expiry;

        Lease(String mac, String address, String claimedName, long expiry) {
            this.mac = mac;
            this.address = address;
            this.claimedName = claimedName;
            this.expiry = expiry;
        }
    }

    /** One connected device, ready to show. */
    static final class Client {
        final String mac;
        /** Cleaned claimed name, or null. */
        final String name;
        /** Address of the newest lease, or null. */
        final String address;

        Client(String mac, String name, String address) {
            this.mac = mac;
            this.name = name;
            this.address = address;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Client)) return false;
            final Client that = (Client) other;
            return mac.equals(that.mac) && Objects.equals(name, that.name)
                    && Objects.equals(address, that.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mac, name, address);
        }

        @Override
        public String toString() {
            return "Client(" + mac + ", " + name + ", " + address + ")";
        }
    }

    private static final Comparator<Lease> NEWEST_LEASE_FIRST = (a, b) -> {
        if (a.expiry != b.expiry) return a.expiry > b.expiry ? -1 : 1;
        return String.valueOf(a.address).compareTo(String.valueOf(b.address));
    };

    private static final Comparator<Client> NAMED_FIRST_BY_NAME = (a, b) -> {
        if ((a.name == null) != (b.name == null)) return a.name == null ? 1 : -1;
        if (a.name != null) {
            int byName = a.name.compareToIgnoreCase(b.name);
            if (byName == 0) byName = a.name.compareTo(b.name);
            if (byName != 0) return byName;
        }
        return a.mac.compareTo(b.mac);
    };

    /** The connected devices, each once: named ones by name, then the rest by MAC address. */
    static List<Client> list(Collection<String> connectedMacs, Collection<Lease> leases) {
        final TreeSet<String> macs = new TreeSet<>();
        if (connectedMacs != null) {
            for (String mac : connectedMacs) {
                if (mac != null && !mac.isEmpty()) macs.add(normalize(mac));
            }
        }
        final Map<String, List<Lease>> leasesByMac = new HashMap<>();
        if (leases != null) {
            for (Lease lease : leases) {
                if (lease == null || lease.mac == null) continue;
                final String mac = normalize(lease.mac);
                if (!macs.contains(mac)) continue;
                leasesByMac.computeIfAbsent(mac, key -> new ArrayList<>()).add(lease);
            }
        }
        final List<Client> clients = new ArrayList<>(macs.size());
        for (String mac : macs) {
            String name = null;
            String address = null;
            final List<Lease> own = leasesByMac.get(mac);
            if (own != null) {
                Collections.sort(own, NEWEST_LEASE_FIRST);
                for (Lease lease : own) {
                    if (address == null) address = emptyToNull(lease.address);
                    if (name == null) name = cleanName(lease.claimedName);
                }
            }
            clients.add(new Client(mac, name, address));
        }
        Collections.sort(clients, NAMED_FIRST_BY_NAME);
        return clients;
    }

    /**
     * Makes a claimed name safe to show on one line: drops control, bidi and other invisible
     * characters, joins white space and cuts to {@link #MAX_NAME_LENGTH}. Null if nothing is left.
     */
    static String cleanName(String claimed) {
        if (claimed == null) return null;
        final StringBuilder out = new StringBuilder();
        boolean spaceDue = false;
        for (int i = 0; i < claimed.length(); ) {
            final int codePoint = claimed.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                spaceDue = out.length() > 0;
                continue;
            }
            if (isHidden(codePoint)) continue;
            if (spaceDue) {
                out.append(' ');
                spaceDue = false;
            }
            out.appendCodePoint(codePoint);
        }
        if (out.length() == 0) return null;
        if (out.codePointCount(0, out.length()) <= MAX_NAME_LENGTH) return out.toString();
        final String cut = out.substring(0, out.offsetByCodePoints(0, MAX_NAME_LENGTH));
        return cut.trim() + "…";
    }

    private static boolean isHidden(int codePoint) {
        switch (Character.getType(codePoint)) {
            case Character.CONTROL:
            case Character.FORMAT:
            case Character.SURROGATE:
            case Character.PRIVATE_USE:
            case Character.UNASSIGNED:
                return true;
            default:
                return false;
        }
    }

    private static String normalize(String mac) {
        return mac.toLowerCase(Locale.ROOT);
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }
}

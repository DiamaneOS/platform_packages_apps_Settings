/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */
package com.android.settings.password.passphrase;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeptEntryTest {

    /** Stands in for a credential: it only knows whether it was overwritten. */
    private static final class Secret {
        boolean mWiped;
    }

    private static KeptEntry<Secret> kept(Secret first, Secret chosen, boolean beingSaved,
            char[] typed) {
        return new KeptEntry<>(secret -> secret.mWiped = true, "NeedToConfirm", first, chosen,
                beingSaved, typed, true, false, true);
    }

    @Test
    public void keeps_whatTheScreenHadInHand() {
        final Secret first = new Secret();
        final Secret chosen = new Secret();
        final char[] typed = {'a', 'b'};

        final KeptEntry<Secret> entry = kept(first, chosen, false, typed);

        assertEquals("NeedToConfirm", entry.stage());
        assertSame(first, entry.first());
        assertSame(chosen, entry.chosen());
        assertArrayEquals(new char[] {'a', 'b'}, entry.typed());
        assertTrue(entry.riskAskedForFirstEntry());
        assertFalse(entry.saveRefused());
        assertTrue(entry.savingStrongLock());
    }

    @Test
    public void handedOver_theEntriesLiveOn_theTypedCopyIsOverwritten() {
        final Secret first = new Secret();
        final Secret chosen = new Secret();
        final char[] typed = {'a', 'b'};
        final KeptEntry<Secret> entry = kept(first, chosen, false, typed);

        entry.handedOver();

        assertFalse(first.mWiped);
        assertFalse(chosen.mWiped);
        assertArrayEquals(new char[] {'\0', '\0'}, typed);
        assertEquals(0, entry.typed().length);
        assertNull(entry.first());
        assertNull(entry.chosen());
    }

    @Test
    public void handedOverThenWiped_theEntriesOfTheNewScreenAreLeftAlone() {
        final Secret first = new Secret();
        final KeptEntry<Secret> entry = kept(first, first, false, new char[0]);
        entry.handedOver();

        entry.wipe();

        assertFalse(first.mWiped);
    }

    @Test
    public void wipe_overwritesEverything() {
        final Secret first = new Secret();
        final Secret chosen = new Secret();
        final char[] typed = {'a', 'b', 'c'};
        final KeptEntry<Secret> entry = kept(first, chosen, false, typed);

        entry.wipe();

        assertTrue(first.mWiped);
        assertTrue(chosen.mWiped);
        assertArrayEquals(new char[] {'\0', '\0', '\0'}, typed);
        assertEquals(0, entry.typed().length);
        assertNull(entry.first());
        assertNull(entry.chosen());
    }

    @Test
    public void wipe_leavesAnEntryThatIsBeingSaved() {
        final Secret first = new Secret();
        final Secret chosen = new Secret();
        final KeptEntry<Secret> entry = kept(first, chosen, true, new char[0]);

        entry.wipe();

        assertTrue(first.mWiped);
        assertFalse(chosen.mWiped);
    }

    @Test
    public void wipe_leavesAnEntryThatIsBeingSaved_alsoWhenItIsTheFirstEntry() {
        final Secret both = new Secret();
        final KeptEntry<Secret> entry = kept(both, both, true, new char[0]);

        entry.wipe();

        assertFalse(both.mWiped);
    }

    @Test
    public void wipe_withNothingKept_doesNothing() {
        final KeptEntry<Secret> entry = kept(null, null, false, new char[0]);

        entry.wipe();
        entry.wipe();

        assertNull(entry.first());
        assertEquals(0, entry.typed().length);
    }
}

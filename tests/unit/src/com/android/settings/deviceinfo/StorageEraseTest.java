/* Copyright (C) 2026 DiamaneOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.deviceinfo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class StorageEraseTest {
    @Test
    public void eachNewConfirmationAllowsOneEraseAcrossIntentParcels() {
        Intent confirmation = new Intent();
        StorageErase.attachRetryConfirmation(confirmation);
        Parcel parcel = Parcel.obtain();
        try {
            confirmation.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Intent copy = Intent.CREATOR.createFromParcel(parcel);
            assertTrue(StorageErase.consumeRetryConfirmation(copy));
            assertFalse(StorageErase.consumeRetryConfirmation(confirmation));
        } finally {
            parcel.recycle();
        }

        StorageErase.attachRetryConfirmation(confirmation);
        assertTrue(StorageErase.consumeRetryConfirmation(confirmation));
    }

    @Test
    public void arbitraryBinderDoesNotAuthorizeErase() {
        Bundle extras = new Bundle();
        extras.putBinder(StorageErase.RETRY, new Binder());
        assertFalse(StorageErase.consumeRetryConfirmation(new Intent().putExtras(extras)));
    }

    @Test
    public void offerConfirmationOnlyWhenFormatWriterIsKnownToHaveStopped() {
        DiskInfo disk = new DiskInfo("disk:179,0", DiskInfo.FLAG_SD);
        disk.size = disk.mediumBytes = 4096;
        disk.mediumGeneration = "41";
        VolumeInfo volume = new VolumeInfo("public:179,1", VolumeInfo.TYPE_PUBLIC, disk, null);
        StorageManager storage = mock(StorageManager.class);
        when(storage.getVolumes()).thenReturn(List.of(volume));

        volume.state = VolumeInfo.STATE_FORMATTED_UNVERIFIED;
        assertTrue(StorageErase.canOfferRetry(storage, volume));
        volume.state = VolumeInfo.STATE_FORMAT_RESULT_UNVERIFIED;
        assertTrue(StorageErase.canOfferRetry(storage, volume));
        volume.state = VolumeInfo.STATE_UNOPENABLE;
        assertFalse(StorageErase.canOfferRetry(storage, volume));
    }
}

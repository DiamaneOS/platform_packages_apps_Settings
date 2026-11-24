/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeActions;
import android.os.storage.VolumeInfo;

/** DiamaneOS: the medium selected in a confirmation, kept until vold completes its verified conversion. */
final class StorageErase {
    static final String GENERATION = "diamaneos.storage.erase.generation";
    static final String BYTES = "diamaneos.storage.erase.bytes";
    static final String RETRY = "diamaneos.storage.erase.retry";

    private StorageErase() {}

    static boolean canOfferRetry(StorageManager storage, VolumeInfo volume) {
        return volume != null && volume.getType() == VolumeInfo.TYPE_PUBLIC
                && VolumeActions.isFormatAwaitingVerification(volume.getState())
                && VolumeActions.canFormatDisk(volume.getDisk(), storage.getVolumes(), true);
    }

    static boolean capture(Context context, String id, Bundle args, boolean retry) {
        StorageManager storage = context.getSystemService(StorageManager.class);
        DiskInfo disk = storage.findDiskById(id);
        if (!VolumeActions.canFormatDisk(disk, storage.getVolumes(), retry)) return false;
        args.putString(GENERATION, disk.mediumGeneration);
        args.putLong(BYTES, disk.mediumBytes);
        args.putBoolean(RETRY, retry);
        return true;
    }

    static boolean allowed(Context context, String id, Bundle args) {
        if (args == null) return false;
        StorageManager storage = context.getSystemService(StorageManager.class);
        DiskInfo disk = storage.findDiskById(id);
        return VolumeActions.canFormatDisk(disk, storage.getVolumes(), args.getBoolean(RETRY))
                && java.util.Objects.equals(args.getString(GENERATION), disk.mediumGeneration)
                && args.getLong(BYTES) == disk.mediumBytes;
    }

    static void attachRetryConfirmation(Intent intent) {
        Bundle extras = new Bundle();
        extras.putBinder(RETRY, new RetryConfirmation());
        intent.putExtras(extras);
    }

    static boolean consumeRetryConfirmation(Intent intent) {
        Bundle extras = intent.getExtras();
        return extras != null && extras.getBinder(RETRY) instanceof RetryConfirmation confirmation
                && confirmation.consume();
    }

    // Rotation retains the in-flight task. A recreated task needs a new user confirmation.
    private static final class RetryConfirmation extends Binder {
        private boolean mUsed;

        synchronized boolean consume() {
            if (mUsed) return false;
            mUsed = true;
            return true;
        }
    }
}

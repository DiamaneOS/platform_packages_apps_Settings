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

package com.android.settings.spa.network;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.test.core.app.ApplicationProvider;

import com.android.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
public class AddSimFlowTest {

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());

    @Before
    public void setUp() {
        doNothing().when(mContext).startActivity(any(Intent.class));
    }

    private void installEsimManager(boolean enabled) {
        PackageInfo info = new PackageInfo();
        info.packageName = "de.diamaneos.euicc";
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = info.packageName;
        info.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        info.applicationInfo.enabled = enabled;
        Shadows.shadowOf(mContext.getPackageManager()).installPackage(info);
    }

    @Test
    public void startAddSimFlow_eSimSupportOff_saysWhy() {
        installEsimManager(false);

        SimsSectionKt.startAddSimFlow(mContext);

        assertThat(ShadowToast.getTextOfLatestToast())
                .isEqualTo("eSIM support is off. To add an eSIM, turn it on in "
                        + mContext.getString(R.string.network_dashboard_title) + ".");
        verify(mContext, never()).startActivity(any(Intent.class));
    }

    @Test
    public void startAddSimFlow_eSimSupportOn_opensAddSim() {
        installEsimManager(true);

        SimsSectionKt.startAddSimFlow(mContext);

        assertThat(ShadowToast.shownToastCount()).isEqualTo(0);
        verify(mContext).startActivity(any(Intent.class));
    }

    @Test
    public void startAddSimFlow_noEsimManager_opensAddSim() {
        SimsSectionKt.startAddSimFlow(mContext);

        assertThat(ShadowToast.shownToastCount()).isEqualTo(0);
        verify(mContext).startActivity(any(Intent.class));
    }
}

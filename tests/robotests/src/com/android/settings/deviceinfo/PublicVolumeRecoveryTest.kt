/*
 * Copyright (C) 2026 DiamaneOS
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
package com.android.settings.deviceinfo

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.storage.DiskInfo
import android.os.storage.StorageManager
import android.os.storage.VolumeInfo
import android.view.View
import android.widget.Button
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.test.core.app.ApplicationProvider
import com.android.settings.R
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper

/**
 * DiamaneOS: the storage page of an SD card in the states of the filesystem VM. It stays open for a card that could
 * not be opened, was not written back or waits for a VM place, offers Mount there, never offers Format for those or a
 * read-only card, says when a read-only card is being checked, and shows Eject again once the card is mounted.
 */
@RunWith(RobolectricTestRunner::class)
class PublicVolumeRecoveryTest {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var storageManager: StorageManager
    @Mock private lateinit var volume: VolumeInfo
    @Mock private lateinit var disk: DiskInfo
    @Mock private lateinit var file: File

    private lateinit var context: Context
    private var state = VolumeInfo.STATE_MOUNTED

    // VolumeInfo has public fields named like its getters: the getters are called by name.
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context.applicationContext as Application)
            .setSystemService(Context.STORAGE_SERVICE, storageManager)
        `when`(volume.getId()).thenReturn("public:179,1")
        `when`(volume.getDiskId()).thenReturn("disk:179,0")
        `when`(volume.getType()).thenReturn(VolumeInfo.TYPE_PUBLIC)
        `when`(volume.getState()).thenAnswer { state }
        `when`(volume.isMountedReadable()).thenAnswer {
            state == VolumeInfo.STATE_MOUNTED || state == VolumeInfo.STATE_MOUNTED_READ_ONLY
        }
        `when`(volume.getStateDescription()).thenAnswer {
            when (state) {
                VolumeInfo.STATE_UNOPENABLE -> com.android.internal.R.string.ext_media_status_unopenable
                VolumeInfo.STATE_MOUNTED_READ_ONLY -> com.android.internal.R.string.ext_media_status_mounted_ro
                else -> com.android.internal.R.string.ext_media_status_mounted
            }
        }
        `when`(volume.getPath()).thenReturn(file)
        `when`(volume.getFsUuid()).thenReturn("1234-5678")
        `when`(file.totalSpace).thenReturn(1000000L)
        `when`(file.freeSpace).thenReturn(100000L)
        `when`(storageManager.findVolumeById("public:179,1")).thenReturn(volume)
        `when`(storageManager.findDiskById("disk:179,0")).thenReturn(disk)
        `when`(storageManager.getBestVolumeDescription(volume)).thenReturn("SD card")
    }

    @Test
    fun aMountedCardKeepsItsPageWithFormatAndEject() {
        state = VolumeInfo.STATE_MOUNTED
        launch().use { scenario ->
            scenario.onFragment { fragment ->
                assertThat(titles(fragment)).contains(context.getString(R.string.storage_menu_format_option))
                assertThat(eject(fragment)?.visibility).isEqualTo(View.VISIBLE)
            }
        }
    }

    @Test
    fun aCardThatCouldNotBeOpenedKeepsItsPageWithMountAndWithoutFormat() {
        state = VolumeInfo.STATE_UNOPENABLE
        launch().use { scenario ->
            scenario.onFragment { fragment ->
                assertThat(fragment.requireActivity().isFinishing).isFalse()
                assertThat(titles(fragment)).containsAtLeast(
                    context.getString(com.android.internal.R.string.ext_media_status_unopenable),
                    context.getString(R.string.storage_menu_mount))
                assertThat(titles(fragment)).doesNotContain(context.getString(R.string.storage_menu_format_option))
                assertThat(eject(fragment)?.visibility ?: View.GONE).isEqualTo(View.GONE)
            }
        }
    }

    @Test
    fun aCardNotWrittenBackOrWaitingKeepsItsPage() {
        for (s in intArrayOf(VolumeInfo.STATE_NOT_WRITTEN_BACK, VolumeInfo.STATE_WAITING_FOR_PLACE)) {
            state = s
            launch().use { scenario ->
                scenario.onFragment { fragment ->
                    assertThat(fragment.requireActivity().isFinishing).isFalse()
                    assertThat(titles(fragment)).doesNotContain(
                        context.getString(R.string.storage_menu_format_option))
                }
            }
        }
    }

    @Test
    fun aReadOnlyCardOffersExploreAndEjectButNeverFormat() {
        state = VolumeInfo.STATE_MOUNTED_READ_ONLY
        launch().use { scenario ->
            scenario.onFragment { fragment ->
                val titles = titles(fragment)
                assertThat(titles).contains(context.getString(com.android.internal.R.string.ext_media_browse_action))
                assertThat(titles).contains(
                    context.getString(com.android.internal.R.string.ext_media_status_mounted_ro))
                assertThat(titles).doesNotContain(context.getString(R.string.storage_menu_format_option))
                assertThat(eject(fragment)?.visibility).isEqualTo(View.VISIBLE)
            }
        }
    }

    @Test
    fun aReadOnlyCardBeingCheckedSaysSo() {
        state = VolumeInfo.STATE_MOUNTED_READ_ONLY
        volume.checkPending = true
        launch().use { scenario ->
            scenario.onFragment { fragment ->
                assertThat(titles(fragment)).contains(
                    context.getString(com.android.internal.R.string.ext_media_status_checking))
            }
        }
    }

    @Test
    fun ejectComesBackOnceARetriedCardIsMounted() {
        state = VolumeInfo.STATE_UNOPENABLE
        launch().use { scenario ->
            scenario.onFragment { fragment ->
                state = VolumeInfo.STATE_MOUNTED
                fragment.update()
                ShadowLooper.idleMainLooper()
                assertThat(eject(fragment)?.visibility).isEqualTo(View.VISIBLE)
                assertThat(titles(fragment)).contains(context.getString(R.string.storage_menu_format_option))
            }
        }
    }

    private fun launch() =
        launchFragmentInContainer<PublicVolumeSettings>(
            fragmentArgs = Bundle().apply { putString(VolumeInfo.EXTRA_VOLUME_ID, "public:179,1") },
            themeResId = R.style.Theme_SubSettings,
        )

    private fun titles(fragment: PublicVolumeSettings): List<String> {
        fragment.update()
        ShadowLooper.idleMainLooper()
        val group: PreferenceGroup = fragment.preferenceScreen
        return (0 until group.preferenceCount).map { i: Int -> group.getPreference(i) }
            .map { p: Preference -> p.title?.toString() ?: "" }
    }

    private fun eject(fragment: PublicVolumeSettings): Button? {
        val text = context.getString(R.string.storage_menu_unmount)
        val root = fragment.requireActivity().window.decorView
        val found = ArrayList<View>()
        root.findViewsWithText(found, text, View.FIND_VIEWS_WITH_TEXT)
        return found.filterIsInstance<Button>().firstOrNull()
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.widget;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.widget.HighlightablePreferenceGroupAdapterTest.TestActivity;
import com.android.settingslib.widget.DrawableStateLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        com.android.settings.testutils.shadow.ShadowFragment.class,
})
public class HighlightablePreferenceGroupAdapterBackgroundTest {

    private static final String TEST_KEY = "key";

    @Mock
    private PreferenceCategory mPreferenceCategory;

    private Context mContext;
    private HighlightablePreferenceGroupAdapter mAdapter;
    private PreferenceViewHolder mViewHolder;
    private Preference mPreference;

    private TestActivity mActivity;
    private FrameLayout mRootView;

    @Before
    public void setUp() {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class);
        mActivity = controller.create().start().resume().visible().get();
        mRootView = mActivity.findViewById(android.R.id.content);

        // Use the Activity as the context from now on.
        mContext = mActivity;

        MockitoAnnotations.initMocks(this);
        mPreference = new Preference(mContext);
        mPreference.setKey(TEST_KEY);
        when(mPreferenceCategory.getContext()).thenReturn(mContext);
        mAdapter = spy(new HighlightablePreferenceGroupAdapter(mPreferenceCategory, TEST_KEY,
                false /* highlighted*/));
        when(mAdapter.getItem(anyInt())).thenReturn(mPreference);

        View itemView = View.inflate(mContext,
                androidx.preference.R.layout.preference,
                mRootView);
        mViewHolder = PreferenceViewHolder.createInstanceForTests(itemView);
    }

    @Test
    public void cancelledFadeOut_putsTheRowsOwnBackgroundBack() {
        final StateRow row = new StateRow(mContext);
        final Drawable own = new ColorDrawable(Color.RED);
        row.setBackground(own);
        mRootView.addView(row);
        final PreferenceViewHolder holder = PreferenceViewHolder.createInstanceForTests(row);
        ReflectionHelpers.setField(mAdapter, "mHighlightPosition", 10);
        mAdapter.updateBackground(holder, 10);

        // The highlight fades out, and a second removal cancels that fade (a cancel, then an end).
        ReflectionHelpers.callInstanceMethod(mAdapter, "removeHighlightBackground",
                ClassParameter.from(PreferenceViewHolder.class, holder),
                ClassParameter.from(boolean.class, true),
                ClassParameter.from(int.class, 10));
        final Object fadeOut = row.getTag(R.id.active_background_animator);
        assertThat(fadeOut).isInstanceOf(ValueAnimator.class);
        ((ValueAnimator) fadeOut).cancel();

        assertThat(row.getBackground()).isSameInstanceAs(own);
        assertThat(row.getTag(R.id.preference_highlighted)).isEqualTo(false);
        assertThat(row.getTag(R.id.active_background_animator)).isNull();
    }

    /** A row drawn by one of SettingsLib's drawable-state layouts, as a switch row is. */
    private static class StateRow extends FrameLayout implements DrawableStateLayout {
        private int[] mExtraDrawableState;

        StateRow(Context context) {
            super(context);
        }

        @Override
        public int[] getExtraDrawableState() {
            return mExtraDrawableState;
        }

        @Override
        public void setExtraDrawableState(int[] state) {
            mExtraDrawableState = state;
        }
    }
}

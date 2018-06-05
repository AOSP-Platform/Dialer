/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.speakeasy;

import android.content.Context;
import android.preference.PreferenceFragment;
import com.android.dialer.inject.HasRootComponent;
import com.android.incallui.speakeasy.Annotations.SpeakEasyChipResourceId;
import com.android.incallui.speakeasy.Annotations.SpeakEasySettingsFragment;
import com.android.incallui.speakeasy.Annotations.SpeakEasySettingsObject;
import com.google.common.base.Optional;
import dagger.Subcomponent;

/** Dagger component to get SpeakEasyCallManager. */
@Subcomponent
public abstract class SpeakEasyComponent {

  public abstract SpeakEasyCallManager speakEasyCallManager();

  public abstract @SpeakEasySettingsFragment Optional<PreferenceFragment>
      speakEasySettingsFragment();

  public abstract @SpeakEasySettingsObject Optional<Object> speakEasySettingsObject();

  public abstract @SpeakEasyChipResourceId Optional<Integer> speakEasyChip();

  public static SpeakEasyComponent get(Context context) {
    return ((SpeakEasyComponent.HasComponent)
            ((HasRootComponent) context.getApplicationContext()).component())
        .speakEasyComponent();
  }

  /** Used to refer to the root application component. */
  public interface HasComponent {
    SpeakEasyComponent speakEasyComponent();
  }
}

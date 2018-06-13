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
 * limitations under the License
 */

package com.android.dialer.callcomposer.camera.shadows;

import android.net.Uri;
import android.support.annotation.NonNull;
import com.android.dialer.callcomposer.camera.CameraManager;
import com.android.dialer.callcomposer.camera.CameraManager.MediaCallback;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

/** Shadow class for {@link CameraManager}. */
@Implements(CameraManager.class)
public class ShadowCameraManager {

  private static MediaCallback callback;

  public static void onMediaReady() {
    callback.onMediaReady(Uri.parse("foo"), "image/jpeg", 0, 0);
  }

  @Implementation
  public void takePicture(final float heightPercent, @NonNull final MediaCallback callback) {
    ShadowCameraManager.callback = callback;
  }

  @Resetter
  public static void resetManager() {
    CameraManager.get().resetCameraManager();
  }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.util.shadows;

import android.content.Context;
import com.android.dialer.util.CallUtil;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadow class for {@link CallUtil}. */
@Implements(CallUtil.class)
public final class ShadowCallUtil {

  private static int videoCallingAvailability;
  private static boolean isVideoEnabled;

  @Implementation
  public static int getVideoCallingAvailability(Context context) {
    return videoCallingAvailability;
  }

  @Implementation
  public static boolean isVideoEnabled(Context context) {
    return isVideoEnabled;
  }

  public static void setVideoCallingAvailability(int videoCallingAvailability) {
    ShadowCallUtil.videoCallingAvailability = videoCallingAvailability;
  }

  public static void setIsVideoEnabled(boolean isVideoEnabled) {
    ShadowCallUtil.isVideoEnabled = isVideoEnabled;
  }
}

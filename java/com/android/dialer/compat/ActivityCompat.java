/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.dialer.compat;

import android.app.Activity;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

/** Utility for calling methods introduced after Marshmallow for Activities. */
public class ActivityCompat {

  public static boolean isInMultiWindowMode(Activity activity) {
    return VERSION.SDK_INT >= VERSION_CODES.N && activity.isInMultiWindowMode();
  }
}

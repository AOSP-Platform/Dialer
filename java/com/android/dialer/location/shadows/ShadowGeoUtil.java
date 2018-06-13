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

package com.android.dialer.location.shadows;

import android.content.Context;
import com.android.dialer.location.GeoUtil;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** A shadow GeoUtil that always returns the same CurrentCountryIso */
@Implements(GeoUtil.class)
public final class ShadowGeoUtil {
  public static final String COUNTRY_ISO = "US";

  @Implementation
  public static String getCurrentCountryIso(Context context) {
    return COUNTRY_ISO;
  }
}

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

package com.android.contacts.common.compat;

import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract.CommonDataKinds.Phone;

public class PhoneCompat {

  // TODO: Use N APIs
  private static final Uri ENTERPRISE_CONTENT_FILTER_URI =
      Uri.withAppendedPath(Phone.CONTENT_URI, "filter_enterprise");

  public static Uri getContentFilterUri() {
    if (VERSION.SDK_INT >= VERSION_CODES.N) {
      return ENTERPRISE_CONTENT_FILTER_URI;
    }
    return Phone.CONTENT_FILTER_URI;
  }
}

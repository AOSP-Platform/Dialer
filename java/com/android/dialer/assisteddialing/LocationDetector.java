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
 * limitations under the License.
 */

package com.android.dialer.assisteddialing;

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import com.android.dialer.common.LogUtil;
import java.util.Locale;
import java.util.Optional;

// TODO(erfanian): Improve definition of roaming and home country in finalized API.
/**
 * LocationDetector is responsible for determining the Roaming location of the User, in addition to
 * User's home country.
 */
final class LocationDetector {

  private final TelephonyManager telephonyManager;

  public LocationDetector(@NonNull TelephonyManager telephonyManager) {
    if (telephonyManager == null) {
      throw new NullPointerException("Provided TelephonyManager was null");
    }
    this.telephonyManager = telephonyManager;
  }

  // TODO(erfanian):  confirm this is based on ISO 3166-1 alpha-2. libphonenumber expects Unicode's
  // CLDR
  // TODO(erfanian):  confirm these are still valid in a multi-sim environment.
  /**
   * Returns what we believe to be the User's home country. This should resolve to
   * PROPERTY_ICC_OPERATOR_ISO_COUNTRY
   */
  @SuppressWarnings("AndroidApiChecker") // Use of optional
  @TargetApi(VERSION_CODES.N)
  public Optional<String> getUpperCaseUserHomeCountry() {
    String simCountryIso = telephonyManager.getSimCountryIso();
    if (simCountryIso != null) {
      return Optional.of(telephonyManager.getSimCountryIso().toUpperCase(Locale.US));
    }
    LogUtil.i("LocationDetector.getUpperCaseUserHomeCountry", "user home country was null");
    return Optional.empty();
  }

  /** Returns what we believe to be the User's current (roaming) country */
  @SuppressWarnings("AndroidApiChecker") // Use of optional
  @TargetApi(VERSION_CODES.N)
  public Optional<String> getUpperCaseUserRoamingCountry() {
    // TODO Increase coverage of location resolution??
    String networkCountryIso = telephonyManager.getNetworkCountryIso();
    if (networkCountryIso != null) {
      return Optional.of(telephonyManager.getNetworkCountryIso().toUpperCase(Locale.US));
    }
    LogUtil.i("LocationDetector.getUpperCaseUserRoamingCountry", "user roaming country was null");
    return Optional.empty();
  }
}

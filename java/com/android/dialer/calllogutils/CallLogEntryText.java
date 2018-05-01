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

package com.android.dialer.calllogutils;

import android.content.Context;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.time.Clock;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the primary text and secondary text for call log entries.
 *
 * <p>These text values are shown in the main call log list or in the top item of the bottom sheet
 * menu.
 */
public final class CallLogEntryText {

  /**
   * The primary text for bottom sheets is the same as shown in the entry list.
   *
   * <p>(In the entry list, the number of calls and additional icons are displayed as images
   * following the primary text.)
   */
  public static CharSequence buildPrimaryText(Context context, CoalescedRow row) {
    // Always prefer the presentation name, like "Restricted".
    Optional<String> presentationName =
        PhoneNumberDisplayUtil.getNameForPresentation(context, row.getNumberPresentation());
    if (presentationName.isPresent()) {
      return presentationName.get();
    }

    if (row.getIsVoicemailCall() && !TextUtils.isEmpty(row.getVoicemailCallTag())) {
      return row.getVoicemailCallTag();
    }

    // Otherwise prefer the name.
    if (!TextUtils.isEmpty(row.getNumberAttributes().getName())) {
      return row.getNumberAttributes().getName();
    }

    // Otherwise prefer the formatted number.
    if (!TextUtils.isEmpty(row.getFormattedNumber())) {
      return row.getFormattedNumber();
    }

    // If there's no formatted number, just return "Unknown".
    return context.getText(R.string.new_call_log_unknown);
  }

  /**
   * The secondary text to show in the main call log entry list.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>For numbers that are not spam or blocked: $Label(, Duo video|Carrier video)?|$Location •
   *       Date
   *   <li>For blocked non-spam numbers: Blocked • $Label(, Duo video|Carrier video)?|$Location •
   *       Date
   *   <li>For spam but not blocked numbers: Spam • $Label(, Duo video|Carrier video)? • Date
   *   <li>For blocked spam numbers: Blocked • Spam • $Label(, Duo video|Carrier video)? • Date
   * </ul>
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>Mobile, Duo video • Now
   *   <li>Duo video • 10 min ago
   *   <li>Mobile • 11:45 PM
   *   <li>Mobile • Sun
   *   <li>Blocked • Mobile, Duo video • Now
   *   <li>Blocked • Brooklyn, NJ • 10 min ago
   *   <li>Spam • Mobile • Now
   *   <li>Spam • Now
   *   <li>Blocked • Spam • Mobile • Now
   *   <li>Brooklyn, NJ • Jan 15
   * </ul>
   *
   * <p>See {@link CallLogDates#newCallLogTimestampLabel(Context, long, long)} for date rules.
   */
  public static CharSequence buildSecondaryTextForEntries(
      Context context, Clock clock, CoalescedRow row) {
    List<CharSequence> components = new ArrayList<>();

    if (row.getNumberAttributes().getIsBlocked()) {
      components.add(context.getText(R.string.new_call_log_secondary_blocked));
    }
    if (row.getNumberAttributes().getIsSpam()) {
      components.add(context.getText(R.string.new_call_log_secondary_spam));
    }

    components.add(getNumberTypeLabel(context, row));

    components.add(
        CallLogDates.newCallLogTimestampLabel(
            context, clock.currentTimeMillis(), row.getTimestamp()));
    return joinSecondaryTextComponents(components);
  }

  /**
   * The secondary text to show in the top item of the bottom sheet.
   *
   * <p>This is basically the same as {@link #buildSecondaryTextForEntries(Context, Clock,
   * CoalescedRow)} except that instead of suffixing with the time of the call, we suffix with the
   * formatted number.
   */
  public static CharSequence buildSecondaryTextForBottomSheet(Context context, CoalescedRow row) {
    /*
     * Rules:
     *   For numbers that are not spam or blocked:
     *     $Label(, Duo video|Carrier video)?|$Location [• NumberIfNoName]?
     *   For blocked non-spam numbers:
     *     Blocked • $Label(, Duo video|Carrier video)?|$Location [• NumberIfNoName]?
     *   For spam but not blocked numbers:
     *     Spam • $Label(, Duo video|Carrier video)? [• NumberIfNoName]?
     *   For blocked spam numbers:
     *     Blocked • Spam • $Label(, Duo video|Carrier video)? [• NumberIfNoName]?
     *
     * The number is shown at the end if there is no name for the entry. (It is shown in primary
     * text otherwise.)
     *
     * Examples:
     *   Mobile, Duo video • 555-1234
     *   Duo video • 555-1234
     *   Mobile • 555-1234
     *   Blocked • Mobile • 555-1234
     *   Blocked • Brooklyn, NJ • 555-1234
     *   Spam • Mobile • 555-1234
     *   Mobile • 555-1234
     *   Brooklyn, NJ
     */
    List<CharSequence> components = new ArrayList<>();

    if (row.getNumberAttributes().getIsBlocked()) {
      components.add(context.getText(R.string.new_call_log_secondary_blocked));
    }
    if (row.getNumberAttributes().getIsSpam()) {
      components.add(context.getText(R.string.new_call_log_secondary_spam));
    }

    components.add(getNumberTypeLabel(context, row));

    // If there's a presentation name, we showed it in the primary text and shouldn't show any name
    // or number here.
    Optional<String> presentationName =
        PhoneNumberDisplayUtil.getNameForPresentation(context, row.getNumberPresentation());
    if (presentationName.isPresent()) {
      return joinSecondaryTextComponents(components);
    }

    if (TextUtils.isEmpty(row.getNumberAttributes().getName())) {
      // If the name is empty the number is shown as the primary text and there's nothing to add.
      return joinSecondaryTextComponents(components);
    }
    if (TextUtils.isEmpty(row.getFormattedNumber())) {
      // If there's no number, don't append anything.
      return joinSecondaryTextComponents(components);
    }
    components.add(row.getFormattedNumber());
    return joinSecondaryTextComponents(components);
  }

  /**
   * Returns a value such as "Mobile, Duo video" without the time of the call or formatted number
   * appended.
   *
   * <p>When the secondary text is shown in call log entry list, this prefix is suffixed with the
   * time of the call, and when it is shown in a bottom sheet, it is suffixed with the formatted
   * number.
   */
  private static CharSequence getNumberTypeLabel(Context context, CoalescedRow row) {
    StringBuilder secondaryText = new StringBuilder();

    // The number type label comes first (e.g., "Mobile", "Work", "Home", etc).
    String numberTypeLabel = row.getNumberAttributes().getNumberTypeLabel();
    secondaryText.append(numberTypeLabel);

    // Add video call info if applicable.
    if ((row.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO) {
      if (secondaryText.length() > 0) {
        secondaryText.append(", ");
      }

      boolean isDuoCall =
          DuoComponent.get(context).getDuo().isDuoAccount(row.getPhoneAccountComponentName());
      secondaryText.append(
          context.getText(
              isDuoCall ? R.string.new_call_log_duo_video : R.string.new_call_log_carrier_video));
    }

    // Show the location if
    // (1) there is no number type label, and
    // (2) the number is not spam.
    if (TextUtils.isEmpty(numberTypeLabel) && !row.getNumberAttributes().getIsSpam()) {
      // If number attributes contain a location (obtained from a PhoneLookup), use it instead
      // of the one from the annotated call log.
      String location =
          !TextUtils.isEmpty(row.getNumberAttributes().getGeolocation())
              ? row.getNumberAttributes().getGeolocation()
              : row.getGeocodedLocation();
      if (!TextUtils.isEmpty(location)) {
        if (secondaryText.length() > 0) {
          secondaryText.append(", ");
        }
        secondaryText.append(location);
      }
    }

    return secondaryText;
  }

  private static CharSequence joinSecondaryTextComponents(List<CharSequence> components) {
    return TextUtils.join(
        " • ", Collections2.filter(components, (text) -> !TextUtils.isEmpty(text)));
  }
}

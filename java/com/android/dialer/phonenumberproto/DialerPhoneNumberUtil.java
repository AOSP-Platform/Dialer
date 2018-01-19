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

package com.android.dialer.phonenumberproto;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.telephony.PhoneNumberUtils;
import com.android.dialer.DialerInternalPhoneNumber;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.DialerPhoneNumber.RawInput;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.MatchType;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * Wrapper for selected methods in {@link PhoneNumberUtil} which uses the {@link DialerPhoneNumber}
 * lite proto instead of the {@link com.google.i18n.phonenumbers.Phonenumber.PhoneNumber} POJO.
 *
 * <p>All methods should be called on a worker thread.
 */
public class DialerPhoneNumberUtil {
  private final PhoneNumberUtil phoneNumberUtil;

  @WorkerThread
  public DialerPhoneNumberUtil(@NonNull PhoneNumberUtil phoneNumberUtil) {
    Assert.isWorkerThread();
    this.phoneNumberUtil = Assert.isNotNull(phoneNumberUtil);
  }

  /**
   * Parses the provided raw phone number into a {@link DialerPhoneNumber}.
   *
   * @see PhoneNumberUtil#parse(CharSequence, String)
   */
  @WorkerThread
  public DialerPhoneNumber parse(@Nullable String numberToParse, @Nullable String defaultRegion) {
    Assert.isWorkerThread();

    DialerPhoneNumber.Builder dialerPhoneNumber = DialerPhoneNumber.newBuilder();
    RawInput.Builder rawInput = RawInput.newBuilder();
    // Numbers can be null or empty for incoming "unknown" calls.
    if (numberToParse != null) {
      rawInput.setNumber(numberToParse);
    }
    if (defaultRegion != null) {
      rawInput.setCountryIso(defaultRegion);
    }
    dialerPhoneNumber.setRawInput(rawInput.build());

    // If the number is a service number, just store the raw input and don't bother trying to parse
    // it. PhoneNumberUtil#parse ignores these characters which can lead to confusing behavior, such
    // as the numbers "#123" and "123" being considered the same. The "#" can appear in the middle
    // of a service number and the "*" can appear at the beginning (see a bug).
    if (numberToParse != null && (numberToParse.contains("#") || numberToParse.startsWith("*"))) {
      return dialerPhoneNumber.build();
    }

    try {
      dialerPhoneNumber.setDialerInternalPhoneNumber(
          Converter.pojoToProto(phoneNumberUtil.parse(numberToParse, defaultRegion)));
    } catch (NumberParseException e) {
      LogUtil.w("DialerPhoneNumberUtil.parse", "couldn't parse phone number", e);
    }
    return dialerPhoneNumber.build();
  }

  /**
   * Parses the provided raw phone number into a Future result of {@link DialerPhoneNumber}.
   *
   * <p>Work is run on the provided {@link ListeningExecutorService}.
   *
   * @see PhoneNumberUtil#parse(CharSequence, String)
   */
  @AnyThread
  public ListenableFuture<DialerPhoneNumber> parse(
      @Nullable String numberToParse,
      @Nullable String defaultRegion,
      @NonNull ListeningExecutorService service) {
    return service.submit(() -> parse(numberToParse, defaultRegion));
  }

  /**
   * Returns true if the two numbers:
   *
   * <ul>
   *   <li>were parseable by libphonenumber (see {@link #parse(String, String)}),
   *   <li>are a {@link MatchType#SHORT_NSN_MATCH}, {@link MatchType#NSN_MATCH}, or {@link
   *       MatchType#EXACT_MATCH}, and
   *   <li>have the same post-dial digits.
   * </ul>
   *
   * <p>If either number is not parseable, returns true if their raw inputs have the same network
   * and post-dial portions.
   *
   * <p>An empty number is never considered to match another number.
   *
   * @see PhoneNumberUtil#isNumberMatch(PhoneNumber, PhoneNumber)
   */
  @WorkerThread
  public boolean isMatch(
      @NonNull DialerPhoneNumber firstNumberIn, @NonNull DialerPhoneNumber secondNumberIn) {
    Assert.isWorkerThread();
    if (!Assert.isNotNull(firstNumberIn).hasDialerInternalPhoneNumber()
        || !Assert.isNotNull(secondNumberIn).hasDialerInternalPhoneNumber()) {
      // An empty number should not be combined with any other number.
      if (firstNumberIn.getRawInput().getNumber().isEmpty()
          || secondNumberIn.getRawInput().getNumber().isEmpty()) {
        return false;
      }
      // Both the network and post-dial portions of the number should match.
      return sameNetworkPortion(firstNumberIn, secondNumberIn)
          && samePostDialPortion(firstNumberIn, secondNumberIn);
    }
    MatchType matchType =
        isNumberMatch(
            firstNumberIn.getDialerInternalPhoneNumber(),
            secondNumberIn.getDialerInternalPhoneNumber());

    return (matchType == MatchType.SHORT_NSN_MATCH
            || matchType == MatchType.NSN_MATCH
            || matchType == MatchType.EXACT_MATCH)
        && samePostDialPortion(firstNumberIn, secondNumberIn);
  }

  private static boolean sameNetworkPortion(DialerPhoneNumber number1, DialerPhoneNumber number2) {
    return PhoneNumberUtils.extractNetworkPortion(number1.getRawInput().getNumber())
        .equals(PhoneNumberUtils.extractNetworkPortion(number2.getRawInput().getNumber()));
  }

  private static boolean samePostDialPortion(DialerPhoneNumber number1, DialerPhoneNumber number2) {
    return PhoneNumberUtils.extractPostDialPortion(number1.getRawInput().getNumber())
        .equals(PhoneNumberUtils.extractPostDialPortion(number2.getRawInput().getNumber()));
  }

  /**
   * Compares the provided phone numbers.
   *
   * @see PhoneNumberUtil#isNumberMatch(com.google.i18n.phonenumbers.Phonenumber.PhoneNumber,
   *     com.google.i18n.phonenumbers.Phonenumber.PhoneNumber)
   */
  @WorkerThread
  private MatchType isNumberMatch(
      @NonNull DialerInternalPhoneNumber firstNumberIn,
      @NonNull DialerInternalPhoneNumber secondNumberIn) {
    Assert.isWorkerThread();
    return phoneNumberUtil.isNumberMatch(
        Converter.protoToPojo(Assert.isNotNull(firstNumberIn)),
        Converter.protoToPojo(Assert.isNotNull(secondNumberIn)));
  }

  /**
   * Formats the provided number to E164 format or return a normalized version of the raw number if
   * the number is not valid according to {@link PhoneNumberUtil#isValidNumber(PhoneNumber)}.
   *
   * @see #formatToValidE164(DialerPhoneNumber)
   * @see PhoneNumberUtils#normalizeNumber(String)
   */
  @WorkerThread
  public String normalizeNumber(DialerPhoneNumber number) {
    Assert.isWorkerThread();
    return formatToValidE164(number)
        .or(PhoneNumberUtils.normalizeNumber(number.getRawInput().getNumber()));
  }

  /**
   * If the provided number is "valid" (see {@link PhoneNumberUtil#isValidNumber(PhoneNumber)}),
   * formats it to E.164. Otherwise, returns {@link Optional#absent()}.
   *
   * <p>This method is analogous to {@link PhoneNumberUtils#formatNumberToE164(String, String)} (but
   * works with an already parsed {@link DialerPhoneNumber} object).
   *
   * @see PhoneNumberUtil#isValidNumber(PhoneNumber)
   * @see PhoneNumberUtil#format(PhoneNumber, PhoneNumberFormat)
   * @see PhoneNumberUtils#formatNumberToE164(String, String)
   */
  @WorkerThread
  public Optional<String> formatToValidE164(DialerPhoneNumber number) {
    Assert.isWorkerThread();
    if (number.hasDialerInternalPhoneNumber()) {
      PhoneNumber phoneNumber = Converter.protoToPojo(number.getDialerInternalPhoneNumber());
      if (phoneNumberUtil.isValidNumber(phoneNumber)) {
        return Optional.fromNullable(phoneNumberUtil.format(phoneNumber, PhoneNumberFormat.E164));
      }
    }
    return Optional.absent();
  }
}

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

package com.android.dialer.calllog.ui;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;
import com.android.dialer.CoalescedIds;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.CoalescedAnnotatedCallLog;
import com.android.dialer.calllog.model.CoalescedRow;
import com.google.protobuf.InvalidProtocolBufferException;

/** CursorLoader for the coalesced annotated call log. */
final class CoalescedAnnotatedCallLogCursorLoader extends CursorLoader {

  // Indexes for CoalescedAnnotatedCallLog.ALL_COLUMNS
  private static final int ID = 0;
  private static final int TIMESTAMP = 1;
  private static final int NAME = 2;
  private static final int NUMBER = 3;
  private static final int FORMATTED_NUMBER = 4;
  private static final int PHOTO_URI = 5;
  private static final int PHOTO_ID = 6;
  private static final int LOOKUP_URI = 7;
  private static final int NUMBER_TYPE_LABEL = 8;
  private static final int IS_READ = 9;
  private static final int NEW = 10;
  private static final int GEOCODED_LOCATION = 11;
  private static final int PHONE_ACCOUNT_COMPONENT_NAME = 12;
  private static final int PHONE_ACCOUNT_ID = 13;
  private static final int PHONE_ACCOUNT_LABEL = 14;
  private static final int PHONE_ACCOUNT_COLOR = 15;
  private static final int FEATURES = 16;
  private static final int IS_BUSINESS = 17;
  private static final int IS_VOICEMAIL = 18;
  private static final int CALL_TYPE = 19;
  private static final int CAN_REPORT_AS_INVALID_NUMBER = 20;
  private static final int CP2_INFO_INCOMPLETE = 21;
  private static final int COALESCED_IDS = 22;

  CoalescedAnnotatedCallLogCursorLoader(Context context) {
    // CoalescedAnnotatedCallLog requires that PROJECTION be ALL_COLUMNS and the following params be
    // null.
    super(
        context,
        CoalescedAnnotatedCallLog.CONTENT_URI,
        CoalescedAnnotatedCallLog.ALL_COLUMNS,
        null,
        null,
        null);
  }

  /** Creates a new {@link CoalescedRow} from the provided cursor using the current position. */
  static CoalescedRow toRow(Cursor cursor) {
    DialerPhoneNumber number;
    try {
      number = DialerPhoneNumber.parseFrom(cursor.getBlob(NUMBER));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Couldn't parse DialerPhoneNumber bytes");
    }

    CoalescedIds coalescedIds;
    try {
      coalescedIds = CoalescedIds.parseFrom(cursor.getBlob(COALESCED_IDS));
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("Couldn't parse CoalescedIds bytes");
    }

    return CoalescedRow.builder()
        .setId(cursor.getInt(ID))
        .setTimestamp(cursor.getLong(TIMESTAMP))
        .setName(cursor.getString(NAME))
        .setNumber(number)
        .setFormattedNumber(cursor.getString(FORMATTED_NUMBER))
        .setPhotoUri(cursor.getString(PHOTO_URI))
        .setPhotoId(cursor.getLong(PHOTO_ID))
        .setLookupUri(cursor.getString(LOOKUP_URI))
        .setNumberTypeLabel(cursor.getString(NUMBER_TYPE_LABEL))
        .setIsRead(cursor.getInt(IS_READ) == 1)
        .setIsNew(cursor.getInt(NEW) == 1)
        .setGeocodedLocation(cursor.getString(GEOCODED_LOCATION))
        .setPhoneAccountComponentName(cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME))
        .setPhoneAccountId(cursor.getString(PHONE_ACCOUNT_ID))
        .setPhoneAccountLabel(cursor.getString(PHONE_ACCOUNT_LABEL))
        .setPhoneAccountColor(cursor.getInt(PHONE_ACCOUNT_COLOR))
        .setFeatures(cursor.getInt(FEATURES))
        .setIsBusiness(cursor.getInt(IS_BUSINESS) == 1)
        .setIsVoicemail(cursor.getInt(IS_VOICEMAIL) == 1)
        .setCallType(cursor.getInt(CALL_TYPE))
        .setCanReportAsInvalidNumber(cursor.getInt(CAN_REPORT_AS_INVALID_NUMBER) == 1)
        .setCp2InfoIncomplete(cursor.getInt(CP2_INFO_INCOMPLETE) == 1)
        .setCoalescedIds(coalescedIds)
        .build();
  }

  static long getTimestamp(Cursor cursor) {
    return cursor.getLong(TIMESTAMP);
  }
}

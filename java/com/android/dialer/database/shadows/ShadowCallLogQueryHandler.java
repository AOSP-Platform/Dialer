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

package com.android.dialer.database.shadows;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import com.android.dialer.database.CallLogQueryHandler;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadow for {@link CallLogQueryHandler}. */
@Implements(CallLogQueryHandler.class)
public class ShadowCallLogQueryHandler {

  private static boolean fetchVoicemailStatusCalled = false;
  private static boolean fetchVoicemailUnreadCountCalled = false;
  private static Cursor missedCallsCursor;
  private static Cursor unreadVoicemailsCursor;

  private CallLogQueryHandler.Listener listener;

  @Implementation
  public void __constructor__(
      Context context, ContentResolver contentResolver, CallLogQueryHandler.Listener listener) {
    this.listener = listener;
  }

  @Implementation
  public void __constructor__(
      Context context,
      ContentResolver contentResolver,
      CallLogQueryHandler.Listener listener,
      int limit) {
    this.listener = listener;
  }

  @Implementation
  public void fetchMissedCallsUnreadCount() {
    listener.onMissedCallsUnreadCountFetched(missedCallsCursor);
  }

  @Implementation
  public void fetchVoicemailStatus() {
    fetchVoicemailStatusCalled = true;
  }

  @Implementation
  public void fetchCalls(int callType, long newerThan) {
    // No-op for now
  }

  @Implementation
  public void fetchVoicemailUnreadCount() {
    fetchVoicemailUnreadCountCalled = true;
    listener.onVoicemailUnreadCountFetched(unreadVoicemailsCursor);
  }

  @Implementation
  public void markMissedCallsAsRead() {
    setMissedCallsCount(0);
    listener.onMissedCallsUnreadCountFetched(missedCallsCursor);
  }

  public static void setMissedCallsCount(int count) {
    MatrixCursor cursor = new MatrixCursor(new String[] {}, count);
    for (int i = 0; i < count; i++) {
      cursor.addRow(new Object[] {});
    }
    missedCallsCursor = cursor;
  }

  public static boolean wasFetchVoicemailStatusCalled() {
    return fetchVoicemailStatusCalled;
  }

  public static boolean wasFetchVoicemailUnreadCountCalled() {
    return fetchVoicemailUnreadCountCalled;
  }

  public static int getMissedCallsCount() {
    return missedCallsCursor.getCount();
  }

  public static void setUnreadVoicemailCount(int count) {
    MatrixCursor cursor = new MatrixCursor(new String[] {}, count);
    for (int i = 0; i < count; i++) {
      cursor.addRow(new Object[] {});
    }
    unreadVoicemailsCursor = cursor;
  }

  public static int getUnreadVoicemailCount() {
    return unreadVoicemailsCursor.getCount();
  }
}

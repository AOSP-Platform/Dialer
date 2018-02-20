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

package com.android.dialer.phonelookup.blockednumber;

import android.database.ContentObserver;
import android.net.Uri;
import android.support.annotation.MainThread;
import com.android.dialer.calllog.notifier.RefreshAnnotatedCallLogNotifier;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import javax.inject.Inject;

/**
 * Mark the annotated call log as dirty and notify that a refresh is in order when the content
 * changes.
 */
// TODO(a bug): Consider making this class available to all data sources and PhoneLookups.
class MarkDirtyObserver extends ContentObserver {

  private final RefreshAnnotatedCallLogNotifier refreshAnnotatedCallLogNotifier;

  @Inject
  MarkDirtyObserver(RefreshAnnotatedCallLogNotifier refreshAnnotatedCallLogNotifier) {
    super(ThreadUtil.getUiThreadHandler());
    this.refreshAnnotatedCallLogNotifier = refreshAnnotatedCallLogNotifier;
  }

  @MainThread
  @Override
  public void onChange(boolean selfChange, Uri uri) {
    Assert.isMainThread();
    LogUtil.enterBlock("SystemBlockedNumberPhoneLookup.FilteredNumberObserver.onChange");
    refreshAnnotatedCallLogNotifier.markDirtyAndNotify();
  }
}

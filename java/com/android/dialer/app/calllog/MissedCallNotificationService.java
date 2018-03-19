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
package com.android.dialer.app.calllog;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.content.Context;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import com.android.dialer.common.LogUtil;

import com.android.dialer.common.concurrent.DialerExecutors;

import me.leolin.shortcutbadger.ShortcutBadger;
/**
 * start by MissedCallNotificationReceiver to trigger a refresh of the missed call notification.
 * Include both an explicit broadcast from Telecom and a reboot.
 */

public class MissedCallNotificationService extends Service {
    private static final String TAG = "MissedCallNotificationService";
    public static final String COUNT = "Count";
    public static final String NUMBER = "Number";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.i(TAG, "onStartCommand start");
        if (intent == null) {
            LogUtil.i(TAG, "onStartCommand  intent is null");
            return super.onStartCommand(intent, flags, startId);
        }

        Bundle args = intent.getExtras();
        final int count = args.getInt(COUNT);
        final String phoneNumber = args.getString(NUMBER);

        DialerExecutors.createNonUiTaskBuilder(MissedCallNotifier.getIstance(getApplicationContext()))
                .onSuccess(
                        output -> {
                            LogUtil.i(
                                    TAG,
                                    "update missed call notifications successful");
                             updateBadgeCount(this, count);
                        })
                .onFailure(
                        throwable -> {
                            LogUtil.i(
                                    TAG,
                                    "update missed call notifications failed");
                        })
                .build()
                .executeParallel(new Pair<>(count, phoneNumber));
        LogUtil.i(TAG, "onStartCommand end");
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static void updateBadgeCount(Context context, int count) {
        boolean success = ShortcutBadger.applyCount(context, count);
        LogUtil.i(
            TAG,
            "updateBadgeCount: %d success: %b",
            count,
            success);
  }
}


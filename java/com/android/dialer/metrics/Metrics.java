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

package com.android.dialer.metrics;

import android.app.Application;
import android.content.Context;

/** Logs metrics. */
public interface Metrics {

  /** Start a timer. */
  void startTimer(Context context, String timerEventName);

  /** Stop a timer. */
  void stopTimer(String timerEventName);

  /** Start a jank recorder. */
  void startJankRecorder(String eventName);

  /** Stop a jank recorder. */
  void stopJankRecorder(String eventName);

  /** Record memory. */
  void recordMemory(String memoryEventName);

  /** Initiazer for metrics. */
  interface Initializer {
    /** Initialize metrics for the application . */
    void initialize(Application application);
  }
}

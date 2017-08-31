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

package com.android.dialer.simulator.impl.testing.espresso;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.simulator.Simulator.Event;
import com.android.dialer.simulator.impl.SimulatorConnection;
import com.google.android.apps.common.testing.ui.espresso.IdlingRegistry;
import com.google.android.apps.common.testing.ui.espresso.IdlingResource;

/** Listens to simulator connection events to indicate when the test is idle. */
final class SimulatorConnectionIdlingResource implements IdlingResource {
  @NonNull private final SimulatorConnection connection;
  @NonNull private final Event event;
  private final Listener listener = new Listener();
  @Nullable private ResourceCallback resourceCallback;
  private boolean isIdle = true;

  SimulatorConnectionIdlingResource(@NonNull SimulatorConnection connection, @NonNull Event event) {
    this.connection = Assert.isNotNull(connection);
    this.event = Assert.isNotNull(event);
  }

  void register() {
    evaluateIdleCondition();
    connection.addListener(listener);
    IdlingRegistry.getInstance().register(this);
  }

  void unregister() {
    connection.removeListener(listener);
    IdlingRegistry.getInstance().unregister(this);
  }

  @Override
  public String getName() {
    return "SimulatorConnectionIdlingResource";
  }

  @Override
  public boolean isIdleNow() {
    return isIdle;
  }

  @Override
  public void registerIdleTransitionCallback(ResourceCallback callback) {
    this.resourceCallback = callback;
    evaluateIdleCondition();
  }

  private void evaluateIdleCondition() {
    Event lastEvent = getLastEvent(connection);
    boolean newIsIdle = isMatchingEvent(lastEvent, event);
    if (isIdle != newIsIdle) {
      LogUtil.i(
          "SimulatorConnectionIdlingResource.evaluateIdleCondition",
          "idle state changed: %b -> %b",
          isIdle,
          newIsIdle);
      isIdle = newIsIdle;
      if (isIdle && resourceCallback != null) {
        resourceCallback.onTransitionToIdle();
      }
    }
  }

  private static Event getLastEvent(@NonNull SimulatorConnection connection) {
    if (connection.getEvents().isEmpty()) {
      return null;
    }
    return connection.getEvents().get(connection.getEvents().size() - 1);
  }

  private static boolean isMatchingEvent(
      @Nullable Event currentEvent, @NonNull Event expectedEvent) {
    if (currentEvent == null) {
      return false;
    }
    if (currentEvent.type != expectedEvent.type) {
      return false;
    }
    // If the data fields are null then match against any value.
    if (expectedEvent.data1 != null && TextUtils.equals(currentEvent.data1, expectedEvent.data1)) {
      return false;
    }
    if (expectedEvent.data2 != null && TextUtils.equals(currentEvent.data2, expectedEvent.data2)) {
      return false;
    }
    return true;
  }

  private final class Listener implements SimulatorConnection.Listener {
    @Override
    public void onEvent(@NonNull SimulatorConnection connection, @NonNull Event event) {
      LogUtil.i("SimulatorConnectionIdlingResource.Listener.onEvent", "event: " + event.type);
      if (SimulatorConnectionIdlingResource.this.connection.equals(connection)) {
        evaluateIdleCondition();
      }
    }
  }
}

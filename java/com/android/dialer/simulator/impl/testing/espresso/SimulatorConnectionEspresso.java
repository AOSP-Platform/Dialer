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

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.telecom.Connection;
import com.android.dialer.common.Assert;
import com.android.dialer.simulator.Simulator.Event;
import com.android.dialer.simulator.impl.SimulatorConnection;
import com.android.dialer.simulator.impl.SimulatorConnectionService;
import com.android.dialer.testing.espresso.DialerEspressoUtils;
import java.util.Random;

/** Listens to simulator connection events to indicate when the test is idle. */
public final class SimulatorConnectionEspresso {
  @NonNull
  public static String addNewOutgoingCall(
      @NonNull Context context, @NonNull Bundle extras, @NonNull String phoneNumber) {
    Assert.isNotNull(context);
    Assert.isNotNull(extras);
    Assert.isNotNull(phoneNumber);

    String callTag = createUniqueCallTag();
    Bundle newExtras = new Bundle(extras);
    newExtras.putBoolean(callTag, true);

    SimulatorConnectionService.addNewOutgoingCall(context, newExtras, phoneNumber);
    return callTag;
  }

  @NonNull
  public static String addNewIncomingCall(
      @NonNull Context context, @NonNull Bundle extras, @NonNull String callerId) {
    Assert.isNotNull(context);
    Assert.isNotNull(extras);
    Assert.isNotNull(callerId);

    String callTag = createUniqueCallTag();
    Bundle newExtras = new Bundle(extras);
    newExtras.putBoolean(callTag, true);

    SimulatorConnectionService.addNewIncomingCall(context, newExtras, callerId);
    return callTag;
  }

  @NonNull
  public static Connection getConnection(@NonNull String connectionTag) {
    Assert.isNotNull(connectionTag);
    for (Connection connection : SimulatorConnectionService.getInstance().getAllConnections()) {
      if (isTestConnection(connection, connectionTag)) {
        return connection;
      }
    }
    throw Assert.createIllegalStateFailException();
  }

  @NonNull
  public static Event waitForNextEvent(@NonNull String connectionTag, @Event.Type int eventType) {
    return waitForNextEvent(connectionTag, new Event(eventType));
  }

  @NonNull
  public static Event waitForNextEvent(@NonNull String connectionTag, @NonNull Event event) {
    Assert.isNotNull(connectionTag);
    Assert.isNotNull(event);
    SimulatorConnection connection = (SimulatorConnection) getConnection(connectionTag);
    SimulatorConnectionIdlingResource idlingResource =
        new SimulatorConnectionIdlingResource(connection, event);
    idlingResource.register();
    DialerEspressoUtils.waitForIdle();
    idlingResource.unregister();

    return connection.getEvents().get(connection.getEvents().size() - 1);
  }

  public static void teardown(@NonNull Context context) {
    for (Connection connection : SimulatorConnectionService.getInstance().getAllConnections()) {
      connection.destroy();
    }
  }

  @NonNull
  private static String createUniqueCallTag() {
    int callId = new Random().nextInt();
    return String.format("simulator_call_%x", Math.abs(callId));
  }

  private static boolean isTestConnection(
      @NonNull Connection connection, @NonNull String connectionTag) {
    return connection.getExtras().getBoolean(connectionTag);
  }

  private SimulatorConnectionEspresso() {}
}

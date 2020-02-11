/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.incallui;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;

public class ProximityListener {

    public static final int PROXIMITY_UNKNOWN = -1;
    public static final int PROXIMITY_OFF = 0;
    public static final int PROXIMITY_ON = 1;
    private static final int PROXIMITY_CHANGED = 1234;

    private SensorManager sensorManager;
    private Sensor sensor;
    private int mState;

    private ProximityChangedListener listener;
    Handler mHandler =
            new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case PROXIMITY_CHANGED:
                            synchronized (this) {
                                if (listener != null) {
                                    listener.proximityChanged(mState);
                                }
                            }
                            break;
                    }
                }
            };

    SensorEventListener sensorListener =
            new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    int state = event.values[0] == 0 ? PROXIMITY_ON : PROXIMITY_OFF;
                    setState(state);
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                    // ignore
                }
            };

    public ProximityListener(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

    }

    public void setListener(ProximityChangedListener listener) {
        this.listener = listener;
    }

    public void enable(boolean enable) {
        synchronized (this) {
            if (enable) {
                sensorManager.registerListener(sensorListener, sensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                sensorManager.unregisterListener(sensorListener);
                mHandler.removeMessages(PROXIMITY_CHANGED);
            }
        }
    }

    private void setState(int state) {
        synchronized (this) {
            if (mState == state) {
                return;
            }
            mState = state;
            mHandler.removeMessages(PROXIMITY_CHANGED);
            final Message m = mHandler.obtainMessage(PROXIMITY_CHANGED);
            mHandler.sendMessage(m);
        }
    }

    public interface ProximityChangedListener {
        void proximityChanged(int state);
    }
}

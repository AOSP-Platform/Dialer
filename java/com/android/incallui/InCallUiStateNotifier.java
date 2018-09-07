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
 * limitations under the License.
 */

package com.android.incallui;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import com.android.dialer.common.LogUtil;
import com.google.common.base.Preconditions;

/**
 * This class listens to below events and notifes whether InCallUI is visible to the user or not.
 * a. InCallActivity's lifecycle events (onStop/onStart)
 * b. Display state change events (DISPLAY_ON/DISPLAY_OFF)
 */
public class InCallUiStateNotifier implements DisplayManager.DisplayListener {

    private List<InCallUiStateNotifierListener> inCallUiStateNotifierListeners =
            new CopyOnWriteArrayList<>();
    private static InCallUiStateNotifier inCallUiStateNotifier;
    private DisplayManager displayManager;
    private Context context;

    /**
     * Tracks whether the application is in the background. {@code True} if the application is in
     * the background, {@code false} otherwise.
     */
    private boolean isInBackground;

    /**
     * Tracks whether display is ON/OFF. {@code True} if display is ON, {@code false} otherwise.
     */
    private boolean isDisplayOn;

    /**
     * Handles set up of the {@class InCallUiStateNotifier}. Instantiates the context needed by
     * the class and adds a listener to listen to display state changes.
     */
    public void setUp(Context conText) {
        context = conText;
        displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        displayManager.registerDisplayListener(this, null);
        isDisplayOn = isDisplayOn(
                displayManager.getDisplay(Display.DEFAULT_DISPLAY).getState());
        LogUtil.d("InCallUiStateNotifier.setUp", " isDisplayOn: " + isDisplayOn);
    }

    /**
     * Private constructor. Must use getInstance() to get this singleton.
     */
    private InCallUiStateNotifier() {
    }

    /**
     * This method returns a singleton instance of {@class InCallUiStateNotifier}
     */
    public static synchronized InCallUiStateNotifier getInstance() {
        if (inCallUiStateNotifier == null) {
            inCallUiStateNotifier = new InCallUiStateNotifier();
        }
        return inCallUiStateNotifier;
    }

   /**
     * Adds a new {@link InCallUiStateNotifierListener}.
     *
     * @param listener The listener.
     */
    public void addListener(InCallUiStateNotifierListener listener) {
        addListener(listener, false);
    }

   /**
     * Adds a new {@link InCallUiStateNotifierListener}.
     *
     * @param listener The listener.
     * @param notifyNow if true notify current UI state to clients.
     */
    public void addListener(InCallUiStateNotifierListener listener, boolean notifyNow) {
        Preconditions.checkNotNull(listener);
        if (notifyNow) {
           listener.onUiShowing(isUiShowing());
        }
        inCallUiStateNotifierListeners.add(listener);
    }

    /**
     * Remove a {@link InCallUiStateNotifierListener}.
     *
     * @param listener The listener.
     */
    public void removeListener(InCallUiStateNotifierListener listener) {
        if (listener != null) {
            inCallUiStateNotifierListeners.remove(listener);
        } else {
            LogUtil.e("InCallUiStateNotifier.removeListener", " Can't remove null listener");
        }
    }

    /**
     * Notfies when visibility of InCallUI is changed. For eg.
     * when UE moves in/out of the foreground, display either turns ON/OFF
     * @param showing true if InCallUI is visible, false  otherwise.
     */
    private void notifyOnUiShowing(boolean showing) {
        Preconditions.checkNotNull(inCallUiStateNotifierListeners);
        for (InCallUiStateNotifierListener listener : inCallUiStateNotifierListeners) {
            listener.onUiShowing(showing);
        }
    }

    /**
     * Handles tear down of the {@class InCallUiStateNotifier}. Sets the context to null and
     * unregisters it's display listener.
     */
    public void tearDown() {
        displayManager.unregisterDisplayListener(this);
        displayManager = null;
        context = null;
        inCallUiStateNotifierListeners.clear();
    }

    /**
     * checks to see whether InCallUI experience is visible to the user or not.
     * returns true if InCallUI experience is visible to the user else false.
     */
    private boolean isUiShowing() {
        /* Not in background and display is ON does mean that InCallUI is visible/showing.
        Return true in such cases else false */
        return isInBackground && isDisplayOn;
    }

    /**
     * Checks whether the display is ON.
     *
     * @param displayState The display's current state.
     */
    public static boolean isDisplayOn(int displayState) {
        return displayState == Display.STATE_ON ||
                displayState == Display.STATE_DOZE ||
                displayState == Display.STATE_DOZE_SUSPEND;
    }

    /**
     * Called when UE goes in/out of the foreground.
     * @param showing true if UE is in the foreground, false otherwise.
     */
    public void onUiShowing(boolean showing) {

        //Check UI's old state before updating corresponding state variable(s) 	180
        final boolean wasShowing = isUiShowing();

        isInBackground = !showing;

        //Check UI's new state after updating corresponding state variable(s)
        final boolean isShowing = isUiShowing();

        LogUtil.d("InCallUiStateNotifier.onUiShowing", " wasShowing: " + wasShowing +
                " isShowing: " + isShowing);
        //notify if there is a change in UI state
        if (wasShowing != isShowing) {
            notifyOnUiShowing(showing);
        }
    }

    /**
     * This method overrides onDisplayRemoved method of {@interface DisplayManager.DisplayListener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onDisplayRemoved(int displayId) {
    }

    /**
     * This method overrides onDisplayAdded method of {@interface DisplayManager.DisplayListener}
     * Added for completeness. No implementation yet.
     */
    @Override
    public void onDisplayAdded(int displayId) {
    }

    /**
     * This method overrides onDisplayAdded method of {@interface DisplayManager.DisplayListener}
     * The method gets invoked whenever the properties of a logical display have changed.
     */
    @Override
    public void onDisplayChanged(int displayId) {
        final int displayState = displayManager.getDisplay(displayId).getState();
        LogUtil.d("InCallUiStateNotifier.onDisplayChanged", " displayState: " + displayState +
                " displayId: " + displayId);

        /* Ignore display changed indications if they are received for displays
         * other than default display
         */
        if (displayId != Display.DEFAULT_DISPLAY) {
            LogUtil.w("InCallUiStateNotifier.onDisplayChanged", " onDisplayChanged Ignoring...");
            return;
        }

        //Check UI's old state before updating corresponding state variable(s)
        final boolean wasShowing = isUiShowing();

        isDisplayOn = isDisplayOn(displayState);

        //Check UI's new state after updating corresponding state variable(s)
        final boolean isShowing = isUiShowing();

        LogUtil.d("InCallUiStateNotifier.onDisplayChanged", " wasShowing: " + wasShowing +
                " isShowing: " + isShowing);
        //notify if there is a change in UI state
        if (wasShowing != isShowing) {
            notifyOnUiShowing(isDisplayOn);
        }
    }
}

/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.telecomm.InCallAdapter;
import android.telephony.PhoneNumberUtils;

/**
 * Logic for call buttons.
 */
public class DialpadPresenter extends Presenter<DialpadPresenter.DialpadUi>
        implements InCallPresenter.InCallStateListener {

    private Call mCall;

    @Override
    public void onUiReady(DialpadUi ui) {
        super.onUiReady(ui);
    }

    @Override
    public void onStateChange(InCallPresenter.InCallState state, CallList callList) {
        mCall = callList.getActiveCall();
        Log.d(this, "DialpadPresenter mCall = " + mCall);
    }

    /**
     * Processes the specified digit as a DTMF key, by playing the
     * appropriate DTMF tone, and appending the digit to the EditText
     * field that displays the DTMF digits sent so far.
     *
     */
    public final void processDtmf(char c) {
        Log.d(this, "Processing dtmf key " + c);
        // if it is a valid key, then update the display and send the dtmf tone.
        if (PhoneNumberUtils.is12Key(c) && mCall != null) {
            Log.d(this, "updating display and sending dtmf tone for '" + c + "'");

            // Append this key to the "digits" widget.
            getUi().appendDigitsToField(c);
            // Plays the tone through Telecomm.
            TelecommAdapter.getInstance().playDtmfTone(mCall.getCallId(), c);
        } else {
            Log.d(this, "ignoring dtmf request for '" + c + "'");
        }
    }

    /**
     * Stops the local tone based on the phone type.
     */
    public void stopTone() {
        if (mCall != null) {
            Log.d(this, "stopping remote tone");
            TelecommAdapter.getInstance().stopDtmfTone(mCall.getCallId());
        }
    }

    public interface DialpadUi extends Ui {
        void setVisible(boolean on);
        void appendDigitsToField(char digit);
    }
}

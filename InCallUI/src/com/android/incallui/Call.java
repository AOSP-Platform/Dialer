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
 * limitations under the License.
 */

package com.android.incallui;

import android.net.Uri;
import android.telecomm.CallCapabilities;
import android.telecomm.CallNumberPresentation;
import android.telecomm.CallServiceDescriptor;
import android.telecomm.GatewayInfo;
import android.telephony.DisconnectCause;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Ints;

import java.util.List;
import java.util.Locale;

/**
 * Class object used across CallHandlerService APIs.
 * Describes a single call and its state.
 */
public final class Call {
    /* Defines different states of this call */
    public static class State {
        public static final int INVALID = 0;
        public static final int IDLE = 1;           /* The call is idle.  Nothing active */
        public static final int ACTIVE = 2;         /* There is an active call */
        public static final int INCOMING = 3;       /* A normal incoming phone call */
        public static final int CALL_WAITING = 4;   /* Incoming call while another is active */
        public static final int DIALING = 5;        /* An outgoing call during dial phase */
        public static final int REDIALING = 6;      /* Subsequent dialing attempt after a failure */
        public static final int ONHOLD = 7;         /* An active phone call placed on hold */
        public static final int DISCONNECTING = 8;  /* A call is being ended. */
        public static final int DISCONNECTED = 9;   /* State after a call disconnects */
        public static final int CONFERENCED = 10;   /* Call part of a conference call */

        public static boolean isConnected(int state) {
            switch(state) {
                case ACTIVE:
                case INCOMING:
                case CALL_WAITING:
                case DIALING:
                case REDIALING:
                case ONHOLD:
                case CONFERENCED:
                    return true;
                default:
            }
            return false;
        }

        public static boolean isDialing(int state) {
            return state == DIALING || state == REDIALING;
        }

        public static String toString(int state) {
            switch (state) {
                case INVALID:
                    return "INVALID";
                case IDLE:
                    return "IDLE";
                case ACTIVE:
                    return "ACTIVE";
                case INCOMING:
                    return "INCOMING";
                case CALL_WAITING:
                    return "CALL_WAITING";
                case DIALING:
                    return "DIALING";
                case REDIALING:
                    return "REDIALING";
                case ONHOLD:
                    return "ONHOLD";
                case DISCONNECTING:
                    return "DISCONNECTING";
                case DISCONNECTED:
                    return "DISCONNECTED";
                case CONFERENCED:
                    return "CONFERENCED";
                default:
                    return "UNKOWN";
            }
        }
    }

    private String mCallId;
    private int mState = State.INVALID;
    private int mDisconnectCause = DisconnectCause.NOT_VALID;
    private int mCapabilities;
    private long mConnectTimeMillis = 0;
    private Uri mHandle;
    private GatewayInfo mGatewayInfo;
    private CallServiceDescriptor mCurrentCallServiceDescriptor;
    private CallServiceDescriptor mHandoffCallServiceDescriptor;
    private String mParentCallId;
    private List<String> mChildCallIds;

    public Call(String callId) {
        mCallId = callId;
    }

    public String getCallId() {
        return mCallId;
    }

    public String getNumber() {
        if (mGatewayInfo != null) {
            return mGatewayInfo.getOriginalHandle().getSchemeSpecificPart();
        }
        return mHandle == null ? null : mHandle.getSchemeSpecificPart();
    }

    public Uri getHandle() {
        return mHandle;
    }

    public void setHandle(Uri handle) {
        mHandle = handle;
    }

    public int getState() {
        if (mParentCallId != null) {
            return State.CONFERENCED;
        } else {
            return mState;
        }
    }

    public void setState(int state) {
        mState = state;
    }

    public CallNumberPresentation getNumberPresentation() {
        return CallNumberPresentation.ALLOWED;
    }

    public CallNumberPresentation getCnapNamePresentation() {
        return CallNumberPresentation.ALLOWED;
    }

    public String getCnapName() {
        return "";
    }

    /** Returns call disconnect cause; values are defined in {@link DisconnectCause}. */
    public int getDisconnectCause() {
        if (mState == State.DISCONNECTED || mState == State.IDLE) {
            return mDisconnectCause;
        }

        return DisconnectCause.NOT_DISCONNECTED;
    }

    /** Sets the call disconnect cause; values are defined in {@link DisconnectCause}. */
    public void setDisconnectCause(int cause) {
        mDisconnectCause = cause;
    }

    /** Sets a bit mask of capabilities unique to this call. */
    public void setCapabilities(int capabilities) {
        mCapabilities = (CallCapabilities.ALL & capabilities);
    }

    /** Checks if the call supports the given set of capabilities supplied as a bit mask. */
    public boolean can(int capabilities) {
        return (capabilities == (capabilities & mCapabilities));
    }

    /** Sets the time when the call first became active. */
    public void setConnectTimeMillis(long connectTimeMillis) {
        mConnectTimeMillis = connectTimeMillis;
    }

    /** Gets the time when the call first became active. */
    public long getConnectTimeMillis() {
        return mConnectTimeMillis;
    }

    public boolean isConferenceCall() {
        return mChildCallIds != null && !mChildCallIds.isEmpty();
    }

    public GatewayInfo getGatewayInfo() {
        return mGatewayInfo;
    }

    public void setGatewayInfo(GatewayInfo gatewayInfo) {
        mGatewayInfo = gatewayInfo;
    }

    /** The descriptor for the call service currently routing this call. */
    public CallServiceDescriptor getCurrentCallServiceDescriptor() {
        return mCurrentCallServiceDescriptor;
    }

    public void setCurrentCallServiceDescriptor(CallServiceDescriptor descriptor) {
        mCurrentCallServiceDescriptor = descriptor;
    }

    /**
     * The descriptor for the call service that this call is being switched to, null if handoff is
     * not in progress.
     */
    public CallServiceDescriptor getHandoffCallServiceDescriptor() {
        return mHandoffCallServiceDescriptor;
    }

    public void setHandoffCallServiceDescriptor(CallServiceDescriptor descriptor) {
        mHandoffCallServiceDescriptor = descriptor;
    }

    public void setChildCallIds(List<String> callIds) {
        mChildCallIds = callIds;
    }

    public List<String> getChildCallIds() {
        return mChildCallIds;
    }

    public void setParentId(String callId) {
        mParentCallId = callId;
    }

    public String getParentId() {
        return mParentCallId;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "[%s, %s, %s, children:%s, parent:%s]",
                mCallId,
                State.toString(mState),
                CallCapabilities.toString(mCapabilities),
                mChildCallIds,
                mParentCallId);
    }
}

/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.util.TelecomUtil;

/**
 * Provides operations for managing notifications.
 * <p>
 * It handles the following actions:
 * <ul>
 * <li>{@link #ACTION_MARK_NEW_VOICEMAILS_AS_OLD}: marks all the new voicemails in the call log as
 * old; this is called when a notification is dismissed.</li>
 * <li>{@link #ACTION_UPDATE_NOTIFICATIONS}: updates the content of the new items notification; it
 * may include an optional extra {@link #EXTRA_NEW_VOICEMAIL_URI}, containing the URI of the new
 * voicemail that has triggered this update (if any).</li>
 * </ul>
 */
public class CallLogNotificationsService extends IntentService {
    private static final String TAG = "CallLogNotificationsService";

    /** Action to mark all the new voicemails as old. */
    public static final String ACTION_MARK_NEW_VOICEMAILS_AS_OLD =
            "com.android.dialer.calllog.ACTION_MARK_NEW_VOICEMAILS_AS_OLD";

    /**
     * Action to update the notifications.
     * <p>
     * May include an optional extra {@link #EXTRA_NEW_VOICEMAIL_URI}.
     */
    public static final String ACTION_UPDATE_NOTIFICATIONS =
            "com.android.dialer.calllog.UPDATE_NOTIFICATIONS";

    /**
     * Extra to included with {@link #ACTION_UPDATE_NOTIFICATIONS} to identify the new voicemail
     * that triggered an update.
     * <p>
     * It must be a {@link Uri}.
     */
    public static final String EXTRA_NEW_VOICEMAIL_URI = "NEW_VOICEMAIL_URI";

    private VoicemailQueryHandler mVoicemailQueryHandler;

    public CallLogNotificationsService() {
        super("CallLogNotificationsService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            Log.d(TAG, "onHandleIntent: could not handle null intent");
            return;
        }

        if (!PermissionsUtil.hasPermission(this, android.Manifest.permission.READ_CALL_LOG)) {
            return;
        }

        if (ACTION_MARK_NEW_VOICEMAILS_AS_OLD.equals(intent.getAction())) {
            if (mVoicemailQueryHandler == null) {
                mVoicemailQueryHandler = new VoicemailQueryHandler(this, getContentResolver());
            }
            mVoicemailQueryHandler.markNewVoicemailsAsOld();
        } else if (ACTION_UPDATE_NOTIFICATIONS.equals(intent.getAction())) {
            Uri voicemailUri = (Uri) intent.getParcelableExtra(EXTRA_NEW_VOICEMAIL_URI);
            DefaultVoicemailNotifier.getInstance(this).updateNotification(voicemailUri);
        } else {
            Log.d(TAG, "onHandleIntent: could not handle: " + intent);
        }
    }

    /**
     * Updates notifications for any new voicemails.
     *
     * @param context a valid context.
     * @param voicemailUri The uri pointing to the voicemail to update the notification for. If
     *         {@code null}, then notifications for all new voicemails will be updated.
     */
    public static void updateVoicemailNotifications(Context context, Uri voicemailUri) {
        if (TelecomUtil.hasReadWriteVoicemailPermissions(context)) {
            Intent serviceIntent = new Intent(context, CallLogNotificationsService.class);
            serviceIntent.setAction(CallLogNotificationsService.ACTION_UPDATE_NOTIFICATIONS);
            // If voicemailUri is null, then notifications for all voicemails will be updated.
            if (voicemailUri != null) {
                serviceIntent.putExtra(
                        CallLogNotificationsService.EXTRA_NEW_VOICEMAIL_URI, voicemailUri);
            }
            context.startService(serviceIntent);
        }
    }
}

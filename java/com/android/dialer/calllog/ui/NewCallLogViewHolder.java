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
 * limitations under the License
 */
package com.android.dialer.calllog.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.v4.os.BuildCompat;
import android.support.v7.widget.RecyclerView;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllog.ui.NewCallLogAdapter.PopCounts;
import com.android.dialer.calllog.ui.menu.NewCallLogMenu;
import com.android.dialer.calllogutils.CallLogEntryText;
import com.android.dialer.calllogutils.CallLogRowActions;
import com.android.dialer.calllogutils.NumberAttributesConverter;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.compat.AppCompatConstants;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.oem.MotorolaUtils;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.telecom.TelecomUtil;
import com.android.dialer.time.Clock;
import com.android.dialer.widget.ContactPhotoView;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** {@link RecyclerView.ViewHolder} for the new call log. */
final class NewCallLogViewHolder extends RecyclerView.ViewHolder {

  private final Activity activity;
  private final ContactPhotoView contactPhotoView;
  private final TextView primaryTextView;
  private final TextView callCountTextView;
  private final TextView secondaryTextView;
  private final ImageView callTypeIcon;
  private final ImageView hdIcon;
  private final ImageView wifiIcon;
  private final ImageView assistedDialIcon;
  private final TextView phoneAccountView;
  private final ImageView menuButton;

  private final Clock clock;
  private final RealtimeRowProcessor realtimeRowProcessor;
  private final ExecutorService uiExecutorService;
  private final PopCounts popCounts;

  private long currentRowId;

  NewCallLogViewHolder(
      View view, Clock clock, RealtimeRowProcessor realtimeRowProcessor, PopCounts popCounts) {
    super(view);
    this.activity = (Activity) view.getContext();
    contactPhotoView = view.findViewById(R.id.contact_photo_view);
    primaryTextView = view.findViewById(R.id.primary_text);
    callCountTextView = view.findViewById(R.id.call_count);
    secondaryTextView = view.findViewById(R.id.secondary_text);
    callTypeIcon = view.findViewById(R.id.call_type_icon);
    hdIcon = view.findViewById(R.id.hd_icon);
    wifiIcon = view.findViewById(R.id.wifi_icon);
    assistedDialIcon = view.findViewById(R.id.assisted_dial_icon);
    phoneAccountView = view.findViewById(R.id.phone_account);
    menuButton = view.findViewById(R.id.menu_button);

    this.clock = clock;
    this.realtimeRowProcessor = realtimeRowProcessor;
    this.popCounts = popCounts;
    uiExecutorService = DialerExecutorComponent.get(activity).uiExecutor();
  }

  /** @param cursor a cursor from {@link CoalescedAnnotatedCallLogCursorLoader}. */
  void bind(Cursor cursor) {
    CoalescedRow row = CoalescedAnnotatedCallLogCursorLoader.toRow(cursor);
    currentRowId = row.getId(); // Used to make sure async updates are applied to the correct views

    // Even if there is additional real time processing necessary, we still want to immediately show
    // what information we have, rather than an empty card. For example, if CP2 information needs to
    // be queried on the fly, we can still show the phone number until the contact name loads.
    displayRow(row);

    // Note: This leaks the view holder via the callback (which is an inner class), but this is OK
    // because we only create ~10 of them (and they'll be collected assuming all jobs finish).
    Futures.addCallback(
        realtimeRowProcessor.applyRealtimeProcessing(row),
        new RealtimeRowFutureCallback(row),
        uiExecutorService);
  }

  private void displayRow(CoalescedRow row) {
    // TODO(zachh): Handle RTL properly.
    primaryTextView.setText(CallLogEntryText.buildPrimaryText(activity, row));
    secondaryTextView.setText(CallLogEntryText.buildSecondaryTextForEntries(activity, clock, row));

    if (isUnreadMissedCall(row)) {
      primaryTextView.setTextAppearance(R.style.primary_textview_unread_call);
      callCountTextView.setTextAppearance(R.style.primary_textview_unread_call);
      secondaryTextView.setTextAppearance(R.style.secondary_textview_unread_call);
      phoneAccountView.setTextAppearance(R.style.phoneaccount_textview_unread_call);
    } else {
      primaryTextView.setTextAppearance(R.style.primary_textview);
      callCountTextView.setTextAppearance(R.style.primary_textview);
      secondaryTextView.setTextAppearance(R.style.secondary_textview);
      phoneAccountView.setTextAppearance(R.style.phoneaccount_textview);
    }

    setNumberCalls(row);
    setPhoto(row);
    setFeatureIcons(row);
    setCallTypeIcon(row);
    setPhoneAccounts(row);
    setOnClickListenerForRow(row);
    setOnClickListenerForMenuButon(row);
  }

  private void setNumberCalls(CoalescedRow row) {
    int numberCalls = row.getCoalescedIds().getCoalescedIdCount();
    if (numberCalls > 1) {
      callCountTextView.setText(String.format(Locale.getDefault(), "(%d)", numberCalls));
      callCountTextView.setVisibility(View.VISIBLE);
    } else {
      callCountTextView.setVisibility(View.GONE);
    }
  }

  private boolean isUnreadMissedCall(CoalescedRow row) {
    // Show missed call styling if the most recent call in the group was missed and it is still
    // marked as not read. The "NEW" column is presumably used for notifications and voicemails
    // only.
    return row.getCallType() == Calls.MISSED_TYPE && !row.getIsRead();
  }

  private void setPhoto(CoalescedRow row) {
    contactPhotoView.setPhoto(
        NumberAttributesConverter.toPhotoInfoBuilder(row.getNumberAttributes())
            .setFormattedNumber(row.getFormattedNumber())
            .setIsVideo((row.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO)
            .setIsRtt(
                BuildCompat.isAtLeastP()
                    && (row.getFeatures() & Calls.FEATURES_RTT) == Calls.FEATURES_RTT)
            .setIsVoicemail(row.getIsVoicemailCall())
            .build());
  }

  private void setFeatureIcons(CoalescedRow row) {
    ColorStateList colorStateList =
        ColorStateList.valueOf(
            activity.getColor(
                isUnreadMissedCall(row)
                    ? R.color.feature_icon_unread_color
                    : R.color.feature_icon_read_color));

    // Handle HD Icon
    if ((row.getFeatures() & Calls.FEATURES_HD_CALL) == Calls.FEATURES_HD_CALL) {
      hdIcon.setVisibility(View.VISIBLE);
      hdIcon.setImageTintList(colorStateList);
    } else {
      hdIcon.setVisibility(View.GONE);
    }

    // Handle Wifi Icon
    if (MotorolaUtils.shouldShowWifiIconInCallLog(activity, row.getFeatures())) {
      wifiIcon.setVisibility(View.VISIBLE);
      wifiIcon.setImageTintList(colorStateList);
    } else {
      wifiIcon.setVisibility(View.GONE);
    }

    // Handle Assisted Dialing Icon
    if ((row.getFeatures() & TelephonyManagerCompat.FEATURES_ASSISTED_DIALING)
        == TelephonyManagerCompat.FEATURES_ASSISTED_DIALING) {
      assistedDialIcon.setVisibility(View.VISIBLE);
      assistedDialIcon.setImageTintList(colorStateList);
    } else {
      assistedDialIcon.setVisibility(View.GONE);
    }
  }

  private void setCallTypeIcon(CoalescedRow row) {
    @DrawableRes int resId;
    switch (row.getCallType()) {
      case AppCompatConstants.CALLS_INCOMING_TYPE:
      case AppCompatConstants.CALLS_ANSWERED_EXTERNALLY_TYPE:
        resId = R.drawable.quantum_ic_call_received_vd_theme_24;
        break;
      case AppCompatConstants.CALLS_OUTGOING_TYPE:
        resId = R.drawable.quantum_ic_call_made_vd_theme_24;
        break;
      case AppCompatConstants.CALLS_MISSED_TYPE:
        resId = R.drawable.quantum_ic_call_missed_vd_theme_24;
        break;
      case AppCompatConstants.CALLS_VOICEMAIL_TYPE:
        throw new IllegalStateException("Voicemails not expected in call log");
      case AppCompatConstants.CALLS_BLOCKED_TYPE:
        resId = R.drawable.quantum_ic_block_vd_theme_24;
        break;
      default:
        // It is possible for users to end up with calls with unknown call types in their
        // call history, possibly due to 3rd party call log implementations (e.g. to
        // distinguish between rejected and missed calls). Instead of crashing, just
        // assume that all unknown call types are missed calls.
        resId = R.drawable.quantum_ic_call_missed_vd_theme_24;
        break;
    }
    callTypeIcon.setImageResource(resId);

    if (isUnreadMissedCall(row)) {
      callTypeIcon.setImageTintList(
          ColorStateList.valueOf(activity.getColor(R.color.call_type_icon_unread_color)));
    } else {
      callTypeIcon.setImageTintList(
          ColorStateList.valueOf(activity.getColor(R.color.call_type_icon_read_color)));
    }
  }

  private void setPhoneAccounts(CoalescedRow row) {
    PhoneAccountHandle phoneAccountHandle =
        TelecomUtil.composePhoneAccountHandle(
            row.getPhoneAccountComponentName(), row.getPhoneAccountId());
    if (phoneAccountHandle == null) {
      phoneAccountView.setVisibility(View.GONE);
      return;
    }

    String phoneAccountLabel = PhoneAccountUtils.getAccountLabel(activity, phoneAccountHandle);
    if (TextUtils.isEmpty(phoneAccountLabel)) {
      phoneAccountView.setVisibility(View.GONE);
      return;
    }

    @ColorInt
    int phoneAccountColor = PhoneAccountUtils.getAccountColor(activity, phoneAccountHandle);
    if (phoneAccountColor == PhoneAccount.NO_HIGHLIGHT_COLOR) {
      phoneAccountColor =
          activity
              .getResources()
              .getColor(R.color.dialer_secondary_text_color, activity.getTheme());
    }

    phoneAccountView.setText(phoneAccountLabel);
    phoneAccountView.setTextColor(phoneAccountColor);
    phoneAccountView.setVisibility(View.VISIBLE);
  }

  private void setOnClickListenerForRow(CoalescedRow row) {
    if (!PhoneNumberHelper.canPlaceCallsTo(
        row.getNumber().getNormalizedNumber(), row.getNumberPresentation())) {
      itemView.setOnClickListener(null);
      return;
    }
    itemView.setOnClickListener(view -> CallLogRowActions.startCallForRow(activity, row));
  }

  private void setOnClickListenerForMenuButon(CoalescedRow row) {
    menuButton.setOnClickListener(NewCallLogMenu.createOnClickListener(activity, row));
  }

  private class RealtimeRowFutureCallback implements FutureCallback<CoalescedRow> {
    private final CoalescedRow originalRow;

    RealtimeRowFutureCallback(CoalescedRow originalRow) {
      this.originalRow = originalRow;
    }

    @Override
    public void onSuccess(CoalescedRow updatedRow) {
      // If the user scrolled then this ViewHolder may not correspond to the completed task and
      // there's nothing to do.
      if (originalRow.getId() != currentRowId) {
        popCounts.didNotPop++;
        return;
      }
      // Only update the UI if the updated row differs from the original row (which has already
      // been displayed).
      if (!updatedRow.equals(originalRow)) {
        displayRow(updatedRow);
        popCounts.popped++;
        return;
      }
      popCounts.didNotPop++;
    }

    @Override
    public void onFailure(Throwable throwable) {
      throw new RuntimeException("realtime processing failed", throwable);
    }
  }
}

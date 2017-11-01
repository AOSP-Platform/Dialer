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

package com.android.dialer.calldetails;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calldetails.CallDetailsHeaderViewHolder.CallbackActionListener;
import com.android.dialer.calllogutils.CallTypeHelper;
import com.android.dialer.calllogutils.CallbackActionHelper;
import com.android.dialer.calllogutils.CallbackActionHelper.CallbackAction;
import com.android.dialer.common.Assert;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.duo.DuoComponent;
import java.util.List;

/** Adapter for RecyclerView in {@link CallDetailsActivity}. */
final class CallDetailsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final int HEADER_VIEW_TYPE = 1;
  private static final int CALL_ENTRY_VIEW_TYPE = 2;
  private static final int FOOTER_VIEW_TYPE = 3;

  private final DialerContact contact;
  private final CallbackActionListener callbackActionListener;
  private final CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener;
  private final CallTypeHelper callTypeHelper;
  private List<CallDetailsEntry> callDetailsEntries;

  CallDetailsAdapter(
      Context context,
      @NonNull DialerContact contact,
      @NonNull List<CallDetailsEntry> callDetailsEntries,
      CallbackActionListener callbackActionListener,
      CallDetailsFooterViewHolder.ReportCallIdListener reportCallIdListener) {
    this.contact = Assert.isNotNull(contact);
    this.callDetailsEntries = callDetailsEntries;
    this.callbackActionListener = callbackActionListener;
    this.reportCallIdListener = reportCallIdListener;
    callTypeHelper = new CallTypeHelper(context.getResources(), DuoComponent.get(context).getDuo());
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    switch (viewType) {
      case HEADER_VIEW_TYPE:
        return new CallDetailsHeaderViewHolder(
            inflater.inflate(R.layout.contact_container, parent, false), callbackActionListener);
      case CALL_ENTRY_VIEW_TYPE:
        return new CallDetailsEntryViewHolder(
            inflater.inflate(R.layout.call_details_entry, parent, false));
      case FOOTER_VIEW_TYPE:
        return new CallDetailsFooterViewHolder(
            inflater.inflate(R.layout.call_details_footer, parent, false), reportCallIdListener);
      default:
        throw Assert.createIllegalStateFailException(
            "No ViewHolder available for viewType: " + viewType);
    }
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    if (position == 0) { // Header
      ((CallDetailsHeaderViewHolder) holder).updateContactInfo(contact, getCallbackAction());
    } else if (position == getItemCount() - 1) {
      ((CallDetailsFooterViewHolder) holder).setPhoneNumber(contact.getNumber());
    } else {
      CallDetailsEntryViewHolder viewHolder = (CallDetailsEntryViewHolder) holder;
      CallDetailsEntry entry = callDetailsEntries.get(position - 1);
      viewHolder.setCallDetails(
          contact.getNumber(),
          entry,
          callTypeHelper,
          !entry.getHistoryResultsList().isEmpty() && position != getItemCount() - 2);
    }
  }

  @Override
  public int getItemViewType(int position) {
    if (position == 0) { // Header
      return HEADER_VIEW_TYPE;
    } else if (position == getItemCount() - 1) {
      return FOOTER_VIEW_TYPE;
    } else {
      return CALL_ENTRY_VIEW_TYPE;
    }
  }

  @Override
  public int getItemCount() {
    return callDetailsEntries.size() + 2; // Header + footer
  }

  void updateCallDetailsEntries(List<CallDetailsEntry> entries) {
    callDetailsEntries = entries;
    notifyDataSetChanged();
  }

  private @CallbackAction int getCallbackAction() {
    Assert.checkState(!callDetailsEntries.isEmpty());

    CallDetailsEntry entry = callDetailsEntries.get(0);
    return CallbackActionHelper.getCallbackAction(
        contact.getNumber(), entry.getFeatures(), entry.getIsDuoCall());
  }
}

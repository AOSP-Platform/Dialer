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
package com.android.dialer.phonelookup.consolidator;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.BlockedState;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info.Cp2ContactInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.PeopleApiInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.PeopleApiInfo.InfoType;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Consolidates information from a {@link PhoneLookupInfo}.
 *
 * <p>For example, a single {@link PhoneLookupInfo} may contain different name information from many
 * different {@link PhoneLookup} implementations. This class defines the rules for deciding which
 * name should be selected for display to the user, by prioritizing the data from some {@link
 * PhoneLookup PhoneLookups} over others.
 */
public final class PhoneLookupInfoConsolidator {

  /** Integers representing {@link PhoneLookup} implementations that can provide a contact's name */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({NameSource.NONE, NameSource.CP2_LOCAL, NameSource.CP2_REMOTE, NameSource.PEOPLE_API})
  @interface NameSource {
    int NONE = 0; // used when none of the other sources can provide the name
    int CP2_LOCAL = 1;
    int CP2_REMOTE = 2;
    int PEOPLE_API = 3;
  }

  /**
   * Sources that can provide information about a contact's name.
   *
   * <p>Each source is one of the values in NameSource, as defined above.
   *
   * <p>Sources are sorted in the order of priority. For example, if source CP2_LOCAL can provide
   * the name, we will use that name in the UI and ignore all the other sources. If source CP2_LOCAL
   * can't provide the name, source CP2_REMOTE will be consulted.
   *
   * <p>The reason for defining a name source is to avoid mixing info from different sub-messages in
   * PhoneLookupInfo proto when we are supposed to stick with only one sub-message. For example, if
   * a PhoneLookupInfo proto has both cp2_local_info and cp2_remote_info but only cp2_remote_info
   * has a photo URI, PhoneLookupInfoConsolidator should provide an empty photo URI as CP2_LOCAL has
   * higher priority and we should not use cp2_remote_info's photo URI to display the contact's
   * photo.
   */
  private static final ImmutableList<Integer> NAME_SOURCES_IN_PRIORITY_ORDER =
      ImmutableList.of(NameSource.CP2_LOCAL, NameSource.CP2_REMOTE, NameSource.PEOPLE_API);

  private final Context appContext;
  private final @NameSource int nameSource;
  private final PhoneLookupInfo phoneLookupInfo;

  @Nullable private final Cp2ContactInfo firstCp2LocalContact;
  @Nullable private final Cp2ContactInfo firstCp2RemoteContact;

  public PhoneLookupInfoConsolidator(Context appContext, PhoneLookupInfo phoneLookupInfo) {
    this.appContext = appContext;
    this.phoneLookupInfo = phoneLookupInfo;

    this.firstCp2LocalContact = getFirstLocalContact();
    this.firstCp2RemoteContact = getFirstRemoteContact();
    this.nameSource = selectNameSource();
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the name associated with that number.
   *
   * <p>Examples of this are a local contact's name or a business name received from caller ID.
   *
   * <p>If no name can be obtained from the {@link PhoneLookupInfo}, an empty string will be
   * returned.
   */
  public String getName() {
    switch (nameSource) {
      case NameSource.CP2_LOCAL:
        return Assert.isNotNull(firstCp2LocalContact).getName();
      case NameSource.CP2_REMOTE:
        return Assert.isNotNull(firstCp2RemoteContact).getName();
      case NameSource.PEOPLE_API:
        return phoneLookupInfo.getPeopleApiInfo().getDisplayName();
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the photo URI associated with that number.
   *
   * <p>If no photo URI can be obtained from the {@link PhoneLookupInfo}, an empty string will be
   * returned.
   */
  public String getPhotoUri() {
    switch (nameSource) {
      case NameSource.CP2_LOCAL:
        return Assert.isNotNull(firstCp2LocalContact).getPhotoUri();
      case NameSource.CP2_REMOTE:
        return Assert.isNotNull(firstCp2RemoteContact).getPhotoUri();
      case NameSource.PEOPLE_API:
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the photo ID associated with that number, or 0 if there is none.
   */
  public long getPhotoId() {
    switch (nameSource) {
      case NameSource.CP2_LOCAL:
        return Math.max(Assert.isNotNull(firstCp2LocalContact).getPhotoId(), 0);
      case NameSource.CP2_REMOTE:
        return Math.max(Assert.isNotNull(firstCp2RemoteContact).getPhotoId(), 0);
      case NameSource.PEOPLE_API:
      case NameSource.NONE:
        return 0;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns the lookup URI associated with that number, or an empty string if no lookup URI can be
   * obtained.
   */
  public String getLookupUri() {
    switch (nameSource) {
      case NameSource.CP2_LOCAL:
        return Assert.isNotNull(firstCp2LocalContact).getLookupUri();
      case NameSource.CP2_REMOTE:
        return Assert.isNotNull(firstCp2RemoteContact).getLookupUri();
      case NameSource.PEOPLE_API:
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns a localized string representing the number type such as "Home" or "Mobile", or a custom
   * value set by the user.
   *
   * <p>If no label can be obtained from the {@link PhoneLookupInfo}, an empty string will be
   * returned.
   */
  public String getNumberLabel() {
    if (phoneLookupInfo.hasDialerBlockedNumberInfo()
        && phoneLookupInfo
            .getDialerBlockedNumberInfo()
            .getBlockedState()
            .equals(BlockedState.BLOCKED)) {
      return appContext.getString(R.string.blocked_number_new_call_log_label);
    }

    switch (nameSource) {
      case NameSource.CP2_LOCAL:
        return Assert.isNotNull(firstCp2LocalContact).getLabel();
      case NameSource.CP2_REMOTE:
        return Assert.isNotNull(firstCp2RemoteContact).getLabel();
      case NameSource.PEOPLE_API:
      case NameSource.NONE:
        return "";
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number belongs to a business place.
   */
  public boolean isBusiness() {
    return phoneLookupInfo.hasPeopleApiInfo()
        && phoneLookupInfo.getPeopleApiInfo().getInfoType() == InfoType.NEARBY_BUSINESS;
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number is a voicemail number.
   */
  public boolean isVoicemail() {
    // TODO(twyen): implement
    return false;
  }

  /**
   * Returns true if the {@link PhoneLookupInfo} passed to the constructor has incomplete CP2 local
   * info.
   */
  public boolean isCp2LocalInfoIncomplete() {
    return phoneLookupInfo.getCp2LocalInfo().getIsIncomplete();
  }

  /**
   * The {@link PhoneLookupInfo} passed to the constructor is associated with a number. This method
   * returns whether the number can be reported as invalid.
   *
   * <p>As we currently report invalid numbers via the People API, only numbers from the People API
   * can be reported as invalid.
   */
  public boolean canReportAsInvalidNumber() {
    switch (nameSource) {
      case NameSource.CP2_LOCAL:
      case NameSource.CP2_REMOTE:
        return false;
      case NameSource.PEOPLE_API:
        PeopleApiInfo peopleApiInfo = phoneLookupInfo.getPeopleApiInfo();
        return peopleApiInfo.getInfoType() != InfoType.UNKNOWN
            && !peopleApiInfo.getPersonId().isEmpty();
      case NameSource.NONE:
        return false;
      default:
        throw Assert.createUnsupportedOperationFailException(
            String.format("Unsupported name source: %s", nameSource));
    }
  }

  /**
   * Arbitrarily select the first local CP2 contact. In the future, it may make sense to display
   * contact information from all contacts with the same number (for example show the name as "Mom,
   * Dad" or show a synthesized photo containing photos of both "Mom" and "Dad").
   */
  @Nullable
  private Cp2ContactInfo getFirstLocalContact() {
    return phoneLookupInfo.getCp2LocalInfo().getCp2ContactInfoCount() > 0
        ? phoneLookupInfo.getCp2LocalInfo().getCp2ContactInfo(0)
        : null;
  }

  /**
   * Arbitrarily select the first remote CP2 contact. In the future, it may make sense to display
   * contact information from all contacts with the same number (for example show the name as "Mom,
   * Dad" or show a synthesized photo containing photos of both "Mom" and "Dad").
   */
  @Nullable
  private Cp2ContactInfo getFirstRemoteContact() {
    return phoneLookupInfo.getCp2RemoteInfo().getCp2ContactInfoCount() > 0
        ? phoneLookupInfo.getCp2RemoteInfo().getCp2ContactInfo(0)
        : null;
  }

  /** Select the {@link PhoneLookup} source providing a contact's name. */
  private @NameSource int selectNameSource() {
    for (int nameSource : NAME_SOURCES_IN_PRIORITY_ORDER) {
      switch (nameSource) {
        case NameSource.CP2_LOCAL:
          if (firstCp2LocalContact != null && !firstCp2LocalContact.getName().isEmpty()) {
            return NameSource.CP2_LOCAL;
          }
          break;
        case NameSource.CP2_REMOTE:
          if (firstCp2RemoteContact != null && !firstCp2RemoteContact.getName().isEmpty()) {
            return NameSource.CP2_REMOTE;
          }
          break;
        case NameSource.PEOPLE_API:
          if (phoneLookupInfo.hasPeopleApiInfo()
              && !phoneLookupInfo.getPeopleApiInfo().getDisplayName().isEmpty()) {
            return NameSource.PEOPLE_API;
          }
          break;
        default:
          throw Assert.createUnsupportedOperationFailException(
              String.format("Unsupported name source: %s", nameSource));
      }
    }

    return NameSource.NONE;
  }
}

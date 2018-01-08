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

package com.android.dialer.phonelookup.cp2;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DeletedContacts;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.ArraySet;
import android.telecom.Call;
import android.text.TextUtils;
import com.android.dialer.DialerPhoneNumber;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.Annotations.BackgroundExecutor;
import com.android.dialer.common.concurrent.Annotations.LightweightExecutor;
import com.android.dialer.inject.ApplicationContext;
import com.android.dialer.phonelookup.PhoneLookup;
import com.android.dialer.phonelookup.PhoneLookupInfo;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info;
import com.android.dialer.phonelookup.PhoneLookupInfo.Cp2Info.Cp2ContactInfo;
import com.android.dialer.phonelookup.database.contract.PhoneLookupHistoryContract.PhoneLookupHistory;
import com.android.dialer.phonenumberproto.PartitionedNumbers;
import com.android.dialer.storage.Unencrypted;
import com.android.dialer.telecom.TelecomCallUtil;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;

/** PhoneLookup implementation for local contacts. */
public final class Cp2PhoneLookup implements PhoneLookup<Cp2Info> {

  private static final String PREF_LAST_TIMESTAMP_PROCESSED =
      "cp2PhoneLookupLastTimestampProcessed";

  /** Projection for performing batch lookups based on E164 numbers using the PHONE table. */
  private static final String[] PHONE_PROJECTION =
      new String[] {
        Phone.DISPLAY_NAME_PRIMARY, // 0
        Phone.PHOTO_THUMBNAIL_URI, // 1
        Phone.PHOTO_ID, // 2
        Phone.TYPE, // 3
        Phone.LABEL, // 4
        Phone.NORMALIZED_NUMBER, // 5
        Phone.CONTACT_ID, // 6
        Phone.LOOKUP_KEY // 7
      };

  /**
   * Projection for performing individual lookups of non-E164 numbers using the PHONE_LOOKUP table.
   */
  private static final String[] PHONE_LOOKUP_PROJECTION =
      new String[] {
        ContactsContract.PhoneLookup.DISPLAY_NAME_PRIMARY, // 0
        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, // 1
        ContactsContract.PhoneLookup.PHOTO_ID, // 2
        ContactsContract.PhoneLookup.TYPE, // 3
        ContactsContract.PhoneLookup.LABEL, // 4
        ContactsContract.PhoneLookup.NORMALIZED_NUMBER, // 5
        ContactsContract.PhoneLookup.CONTACT_ID, // 6
        ContactsContract.PhoneLookup.LOOKUP_KEY // 7
      };

  // The following indexes should match both PHONE_PROJECTION and PHONE_LOOKUP_PROJECTION above.
  private static final int CP2_INFO_NAME_INDEX = 0;
  private static final int CP2_INFO_PHOTO_URI_INDEX = 1;
  private static final int CP2_INFO_PHOTO_ID_INDEX = 2;
  private static final int CP2_INFO_TYPE_INDEX = 3;
  private static final int CP2_INFO_LABEL_INDEX = 4;
  private static final int CP2_INFO_NORMALIZED_NUMBER_INDEX = 5;
  private static final int CP2_INFO_CONTACT_ID_INDEX = 6;
  private static final int CP2_INFO_LOOKUP_KEY_INDEX = 7;

  // We cannot efficiently process invalid numbers because batch queries cannot be constructed which
  // accomplish the necessary loose matching. We'll attempt to process a limited number of them,
  // but if there are too many we fall back to querying CP2 at render time.
  private static final int MAX_SUPPORTED_INVALID_NUMBERS = 5;

  private final Context appContext;
  private final SharedPreferences sharedPreferences;
  private final ListeningExecutorService backgroundExecutorService;
  private final ListeningExecutorService lightweightExecutorService;

  @Nullable private Long currentLastTimestampProcessed;

  @Inject
  Cp2PhoneLookup(
      @ApplicationContext Context appContext,
      @Unencrypted SharedPreferences sharedPreferences,
      @BackgroundExecutor ListeningExecutorService backgroundExecutorService,
      @LightweightExecutor ListeningExecutorService lightweightExecutorService) {
    this.appContext = appContext;
    this.sharedPreferences = sharedPreferences;
    this.backgroundExecutorService = backgroundExecutorService;
    this.lightweightExecutorService = lightweightExecutorService;
  }

  @Override
  public ListenableFuture<Cp2Info> lookup(Call call) {
    return backgroundExecutorService.submit(() -> lookupInternal(call));
  }

  private Cp2Info lookupInternal(Call call) {
    String rawNumber = TelecomCallUtil.getNumber(call);
    if (TextUtils.isEmpty(rawNumber)) {
      return Cp2Info.getDefaultInstance();
    }
    Optional<String> e164 = TelecomCallUtil.getE164Number(appContext, call);
    Set<Cp2ContactInfo> cp2ContactInfos = new ArraySet<>();
    // Note: It would make sense to use PHONE_LOOKUP for E164 numbers as well, but we use PHONE to
    // ensure consistency when the batch methods are used to update data.
    try (Cursor cursor =
        e164.isPresent()
            ? queryPhoneTableBasedOnE164(PHONE_PROJECTION, ImmutableSet.of(e164.get()))
            : queryPhoneLookup(PHONE_LOOKUP_PROJECTION, rawNumber)) {
      if (cursor == null) {
        LogUtil.w("Cp2PhoneLookup.lookupInternal", "null cursor");
        return Cp2Info.getDefaultInstance();
      }
      while (cursor.moveToNext()) {
        cp2ContactInfos.add(buildCp2ContactInfoFromPhoneCursor(appContext, cursor));
      }
    }
    return Cp2Info.newBuilder().addAllCp2ContactInfo(cp2ContactInfos).build();
  }

  @Override
  public ListenableFuture<Boolean> isDirty(ImmutableSet<DialerPhoneNumber> phoneNumbers) {
    PartitionedNumbers partitionedNumbers = new PartitionedNumbers(phoneNumbers);
    if (partitionedNumbers.unformattableNumbers().size() > MAX_SUPPORTED_INVALID_NUMBERS) {
      // If there are N invalid numbers, we can't determine determine dirtiness without running N
      // queries; since running this many queries is not feasible for the (lightweight) isDirty
      // check, simply return true. The expectation is that this should rarely be the case as the
      // vast majority of numbers in call logs should be valid.
      return Futures.immediateFuture(true);
    }

    ListenableFuture<Long> lastModifiedFuture =
        backgroundExecutorService.submit(
            () -> sharedPreferences.getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L));
    return Futures.transformAsync(
        lastModifiedFuture,
        lastModified -> {
          // We are always going to need to do this check and it is pretty cheap so do it first.
          ListenableFuture<Boolean> anyContactsDeletedFuture =
              anyContactsDeletedSince(lastModified);
          return Futures.transformAsync(
              anyContactsDeletedFuture,
              anyContactsDeleted -> {
                if (anyContactsDeleted) {
                  return Futures.immediateFuture(true);
                }
                // Hopefully the most common case is there are no contacts updated; we can detect
                // this cheaply.
                ListenableFuture<Boolean> noContactsModifiedSinceFuture =
                    noContactsModifiedSince(lastModified);
                return Futures.transformAsync(
                    noContactsModifiedSinceFuture,
                    noContactsModifiedSince -> {
                      if (noContactsModifiedSince) {
                        return Futures.immediateFuture(false);
                      }
                      // This method is more expensive but is probably the most likely scenario; we
                      // are looking for changes to contacts which have been called.
                      ListenableFuture<Set<Long>> contactIdsFuture =
                          queryPhoneTableForContactIds(phoneNumbers);
                      ListenableFuture<Boolean> contactsUpdatedFuture =
                          Futures.transformAsync(
                              contactIdsFuture,
                              contactIds -> contactsUpdated(contactIds, lastModified),
                              MoreExecutors.directExecutor());
                      return Futures.transformAsync(
                          contactsUpdatedFuture,
                          contactsUpdated -> {
                            if (contactsUpdated) {
                              return Futures.immediateFuture(true);
                            }
                            // This is the most expensive method so do it last; the scenario is that
                            // a contact which has been called got disassociated with a number and
                            // we need to clear their information.
                            ListenableFuture<Set<Long>> phoneLookupContactIdsFuture =
                                queryPhoneLookupHistoryForContactIds();
                            return Futures.transformAsync(
                                phoneLookupContactIdsFuture,
                                phoneLookupContactIds ->
                                    contactsUpdated(phoneLookupContactIds, lastModified),
                                MoreExecutors.directExecutor());
                          },
                          MoreExecutors.directExecutor());
                    },
                    MoreExecutors.directExecutor());
              },
              MoreExecutors.directExecutor());
        },
        MoreExecutors.directExecutor());
  }

  /**
   * Returns set of contact ids that correspond to {@code dialerPhoneNumbers} if the contact exists.
   */
  private ListenableFuture<Set<Long>> queryPhoneTableForContactIds(
      ImmutableSet<DialerPhoneNumber> dialerPhoneNumbers) {
    PartitionedNumbers partitionedNumbers = new PartitionedNumbers(dialerPhoneNumbers);

    List<ListenableFuture<Set<Long>>> queryFutures = new ArrayList<>();

    // First use the E164 numbers to query the NORMALIZED_NUMBER column.
    queryFutures.add(
        queryPhoneTableForContactIdsBasedOnE164(partitionedNumbers.validE164Numbers()));

    // Then run a separate query for each invalid number. Separate queries are done to accomplish
    // loose matching which couldn't be accomplished with a batch query.
    Assert.checkState(
        partitionedNumbers.unformattableNumbers().size() <= MAX_SUPPORTED_INVALID_NUMBERS);
    for (String invalidNumber : partitionedNumbers.unformattableNumbers()) {
      queryFutures.add(queryPhoneLookupTableForContactIdsBasedOnRawNumber(invalidNumber));
    }
    return Futures.transform(
        Futures.allAsList(queryFutures),
        listOfSets -> {
          Set<Long> contactIds = new ArraySet<>();
          for (Set<Long> ids : listOfSets) {
            contactIds.addAll(ids);
          }
          return contactIds;
        },
        lightweightExecutorService);
  }

  /** Gets all of the contact ids from PhoneLookupHistory. */
  private ListenableFuture<Set<Long>> queryPhoneLookupHistoryForContactIds() {
    return backgroundExecutorService.submit(
        () -> {
          Set<Long> contactIds = new ArraySet<>();
          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      PhoneLookupHistory.CONTENT_URI,
                      new String[] {
                        PhoneLookupHistory.PHONE_LOOKUP_INFO,
                      },
                      null,
                      null,
                      null)) {

            if (cursor == null) {
              LogUtil.w("Cp2PhoneLookup.queryPhoneLookupHistoryForContactIds", "null cursor");
              return contactIds;
            }

            if (cursor.moveToFirst()) {
              int phoneLookupInfoColumn =
                  cursor.getColumnIndexOrThrow(PhoneLookupHistory.PHONE_LOOKUP_INFO);
              do {
                PhoneLookupInfo phoneLookupInfo;
                try {
                  phoneLookupInfo =
                      PhoneLookupInfo.parseFrom(cursor.getBlob(phoneLookupInfoColumn));
                } catch (InvalidProtocolBufferException e) {
                  throw new IllegalStateException(e);
                }
                for (Cp2ContactInfo info : phoneLookupInfo.getCp2Info().getCp2ContactInfoList()) {
                  contactIds.add(info.getContactId());
                }
              } while (cursor.moveToNext());
            }
          }
          return contactIds;
        });
  }

  private ListenableFuture<Set<Long>> queryPhoneTableForContactIdsBasedOnE164(
      Set<String> validE164Numbers) {
    return backgroundExecutorService.submit(
        () -> {
          Set<Long> contactIds = new ArraySet<>();
          if (validE164Numbers.isEmpty()) {
            return contactIds;
          }
          try (Cursor cursor =
              queryPhoneTableBasedOnE164(new String[] {Phone.CONTACT_ID}, validE164Numbers)) {
            if (cursor == null) {
              LogUtil.w("Cp2PhoneLookup.queryPhoneTableForContactIdsBasedOnE164", "null cursor");
              return contactIds;
            }
            while (cursor.moveToNext()) {
              contactIds.add(cursor.getLong(0 /* columnIndex */));
            }
          }
          return contactIds;
        });
  }

  private ListenableFuture<Set<Long>> queryPhoneLookupTableForContactIdsBasedOnRawNumber(
      String rawNumber) {
    return backgroundExecutorService.submit(
        () -> {
          Set<Long> contactIds = new ArraySet<>();
          try (Cursor cursor =
              queryPhoneLookup(
                  new String[] {android.provider.ContactsContract.PhoneLookup.CONTACT_ID},
                  rawNumber)) {
            if (cursor == null) {
              LogUtil.w(
                  "Cp2PhoneLookup.queryPhoneLookupTableForContactIdsBasedOnRawNumber",
                  "null cursor");
              return contactIds;
            }
            while (cursor.moveToNext()) {
              contactIds.add(cursor.getLong(0 /* columnIndex */));
            }
          }
          return contactIds;
        });
  }

  /** Returns true if any contacts were modified after {@code lastModified}. */
  private ListenableFuture<Boolean> contactsUpdated(Set<Long> contactIds, long lastModified) {
    return backgroundExecutorService.submit(
        () -> {
          try (Cursor cursor = queryContactsTableForContacts(contactIds, lastModified)) {
            return cursor.getCount() > 0;
          }
        });
  }

  private Cursor queryContactsTableForContacts(Set<Long> contactIds, long lastModified) {
    // Filter to after last modified time based only on contacts we care about
    String where =
        Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
            + " > ?"
            + " AND "
            + Contacts._ID
            + " IN ("
            + questionMarks(contactIds.size())
            + ")";

    String[] args = new String[contactIds.size() + 1];
    args[0] = Long.toString(lastModified);
    int i = 1;
    for (Long contactId : contactIds) {
      args[i++] = Long.toString(contactId);
    }

    return appContext
        .getContentResolver()
        .query(
            Contacts.CONTENT_URI,
            new String[] {Contacts._ID, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP},
            where,
            args,
            null);
  }

  private ListenableFuture<Boolean> noContactsModifiedSince(long lastModified) {
    return backgroundExecutorService.submit(
        () -> {
          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      Contacts.CONTENT_URI,
                      new String[] {Contacts._ID},
                      Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + " > ?",
                      new String[] {Long.toString(lastModified)},
                      Contacts._ID + " limit 1")) {
            if (cursor == null) {
              LogUtil.w("Cp2PhoneLookup.noContactsModifiedSince", "null cursor");
              return false;
            }
            return cursor.getCount() == 0;
          }
        });
  }

  /** Returns true if any contacts were deleted after {@code lastModified}. */
  private ListenableFuture<Boolean> anyContactsDeletedSince(long lastModified) {
    return backgroundExecutorService.submit(
        () -> {
          try (Cursor cursor =
              appContext
                  .getContentResolver()
                  .query(
                      DeletedContacts.CONTENT_URI,
                      new String[] {DeletedContacts.CONTACT_DELETED_TIMESTAMP},
                      DeletedContacts.CONTACT_DELETED_TIMESTAMP + " > ?",
                      new String[] {Long.toString(lastModified)},
                      DeletedContacts.CONTACT_DELETED_TIMESTAMP + " limit 1")) {
            if (cursor == null) {
              LogUtil.w("Cp2PhoneLookup.anyContactsDeletedSince", "null cursor");
              return false;
            }
            return cursor.getCount() > 0;
          }
        });
  }

  @Override
  public void setSubMessage(PhoneLookupInfo.Builder destination, Cp2Info subMessage) {
    destination.setCp2Info(subMessage);
  }

  @Override
  public Cp2Info getSubMessage(PhoneLookupInfo phoneLookupInfo) {
    return phoneLookupInfo.getCp2Info();
  }

  @Override
  public ListenableFuture<ImmutableMap<DialerPhoneNumber, Cp2Info>> getMostRecentInfo(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap) {
    currentLastTimestampProcessed = null;

    ListenableFuture<Long> lastModifiedFuture =
        backgroundExecutorService.submit(
            () -> sharedPreferences.getLong(PREF_LAST_TIMESTAMP_PROCESSED, 0L));
    return Futures.transformAsync(
        lastModifiedFuture,
        lastModified -> {
          // Build a set of each DialerPhoneNumber that was associated with a contact, and is no
          // longer associated with that same contact.
          ListenableFuture<Set<DialerPhoneNumber>> deletedPhoneNumbersFuture =
              getDeletedPhoneNumbers(existingInfoMap, lastModified);

          return Futures.transformAsync(
              deletedPhoneNumbersFuture,
              deletedPhoneNumbers -> {

                // If there are too many invalid numbers, just defer the work to render time.
                ArraySet<DialerPhoneNumber> unprocessableNumbers =
                    findUnprocessableNumbers(existingInfoMap);
                Map<DialerPhoneNumber, Cp2Info> existingInfoMapToProcess = existingInfoMap;
                if (!unprocessableNumbers.isEmpty()) {
                  existingInfoMapToProcess =
                      Maps.filterKeys(
                          existingInfoMap, number -> !unprocessableNumbers.contains(number));
                }

                // For each DialerPhoneNumber that was associated with a contact or added to a
                // contact, build a map of those DialerPhoneNumbers to a set Cp2ContactInfos, where
                // each Cp2ContactInfo represents a contact.
                ListenableFuture<Map<DialerPhoneNumber, Set<Cp2ContactInfo>>>
                    updatedContactsFuture =
                        buildMapForUpdatedOrAddedContacts(
                            existingInfoMapToProcess, lastModified, deletedPhoneNumbers);

                return Futures.transform(
                    updatedContactsFuture,
                    updatedContacts -> {

                      // Start build a new map of updated info. This will replace existing info.
                      ImmutableMap.Builder<DialerPhoneNumber, Cp2Info> newInfoMapBuilder =
                          ImmutableMap.builder();

                      // For each DialerPhoneNumber in existing info...
                      for (Entry<DialerPhoneNumber, Cp2Info> entry : existingInfoMap.entrySet()) {
                        DialerPhoneNumber dialerPhoneNumber = entry.getKey();
                        Cp2Info existingInfo = entry.getValue();

                        // Build off the existing info
                        Cp2Info.Builder infoBuilder = Cp2Info.newBuilder(existingInfo);

                        // If the contact was updated, replace the Cp2ContactInfo list
                        if (updatedContacts.containsKey(dialerPhoneNumber)) {
                          infoBuilder
                              .clear()
                              .addAllCp2ContactInfo(updatedContacts.get(dialerPhoneNumber));
                          // If it was deleted and not added to a new contact, clear all the CP2
                          // information.
                        } else if (deletedPhoneNumbers.contains(dialerPhoneNumber)) {
                          infoBuilder.clear();
                        } else if (unprocessableNumbers.contains(dialerPhoneNumber)) {
                          infoBuilder.clear().setIsIncomplete(true);
                        }

                        // If the DialerPhoneNumber didn't change, add the unchanged existing info.
                        newInfoMapBuilder.put(dialerPhoneNumber, infoBuilder.build());
                      }
                      return newInfoMapBuilder.build();
                    },
                    lightweightExecutorService);
              },
              lightweightExecutorService);
        },
        lightweightExecutorService);
  }

  private ArraySet<DialerPhoneNumber> findUnprocessableNumbers(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap) {
    ArraySet<DialerPhoneNumber> unprocessableNumbers = new ArraySet<>();
    PartitionedNumbers partitionedNumbers = new PartitionedNumbers(existingInfoMap.keySet());
    if (partitionedNumbers.unformattableNumbers().size() > MAX_SUPPORTED_INVALID_NUMBERS) {
      for (String invalidNumber : partitionedNumbers.unformattableNumbers()) {
        unprocessableNumbers.addAll(
            partitionedNumbers.dialerPhoneNumbersForUnformattable(invalidNumber));
      }
    }
    return unprocessableNumbers;
  }

  @Override
  public ListenableFuture<Void> onSuccessfulBulkUpdate() {
    return backgroundExecutorService.submit(
        () -> {
          if (currentLastTimestampProcessed != null) {
            sharedPreferences
                .edit()
                .putLong(PREF_LAST_TIMESTAMP_PROCESSED, currentLastTimestampProcessed)
                .apply();
          }
          return null;
        });
  }

  private ListenableFuture<Set<DialerPhoneNumber>> findNumbersToUpdate(
      Map<DialerPhoneNumber, Cp2Info> existingInfoMap,
      long lastModified,
      Set<DialerPhoneNumber> deletedPhoneNumbers) {
    return backgroundExecutorService.submit(
        () -> {
          Set<DialerPhoneNumber> updatedNumbers = new ArraySet<>();
          Set<Long> contactIds = new ArraySet<>();
          for (Entry<DialerPhoneNumber, Cp2Info> entry : existingInfoMap.entrySet()) {
            DialerPhoneNumber dialerPhoneNumber = entry.getKey();
            Cp2Info existingInfo = entry.getValue();

            // If the number was deleted, we need to check if it was added to a new contact.
            if (deletedPhoneNumbers.contains(dialerPhoneNumber)) {
              updatedNumbers.add(dialerPhoneNumber);
              continue;
            }

            // When the PhoneLookupHistory contains no information for a number, because for
            // example the user just upgraded to the new UI, or cleared data, we need to check for
            // updated info.
            if (existingInfo.getCp2ContactInfoCount() == 0) {
              updatedNumbers.add(dialerPhoneNumber);
            } else {
              // For each Cp2ContactInfo for each existing DialerPhoneNumber...
              // Store the contact id if it exist, else automatically add the DialerPhoneNumber to
              // our set of DialerPhoneNumbers we want to update.
              for (Cp2ContactInfo cp2ContactInfo : existingInfo.getCp2ContactInfoList()) {
                long existingContactId = cp2ContactInfo.getContactId();
                if (existingContactId == 0) {
                  // If the number doesn't have a contact id, for various reasons, we need to look
                  // up the number to check if any exists. The various reasons this might happen
                  // are:
                  //  - An existing contact that wasn't in the call log is now in the call log.
                  //  - A number was in the call log before but has now been added to a contact.
                  //  - A number is in the call log, but isn't associated with any contact.
                  updatedNumbers.add(dialerPhoneNumber);
                } else {
                  contactIds.add(cp2ContactInfo.getContactId());
                }
              }
            }
          }

          // Query the contacts table and get those that whose
          // Contacts.CONTACT_LAST_UPDATED_TIMESTAMP is after lastModified, such that Contacts._ID
          // is in our set of contact IDs we build above.
          if (!contactIds.isEmpty()) {
            try (Cursor cursor = queryContactsTableForContacts(contactIds, lastModified)) {
              int contactIdIndex = cursor.getColumnIndex(Contacts._ID);
              int lastUpdatedIndex = cursor.getColumnIndex(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP);
              cursor.moveToPosition(-1);
              while (cursor.moveToNext()) {
                // Find the DialerPhoneNumber for each contact id and add it to our updated numbers
                // set. These, along with our number not associated with any Cp2ContactInfo need to
                // be updated.
                long contactId = cursor.getLong(contactIdIndex);
                updatedNumbers.addAll(
                    findDialerPhoneNumbersContainingContactId(existingInfoMap, contactId));
                long lastUpdatedTimestamp = cursor.getLong(lastUpdatedIndex);
                if (currentLastTimestampProcessed == null
                    || currentLastTimestampProcessed < lastUpdatedTimestamp) {
                  currentLastTimestampProcessed = lastUpdatedTimestamp;
                }
              }
            }
          }
          return updatedNumbers;
        });
  }

  @Override
  public void registerContentObservers(
      Context appContext, ContentObserverCallbacks contentObserverCallbacks) {
    // Do nothing since CP2 changes are too noisy.
  }

  /**
   * 1. get all contact ids. if the id is unset, add the number to the list of contacts to look up.
   * 2. reduce our list of contact ids to those that were updated after lastModified. 3. Now we have
   * the smallest set of dialer phone numbers to query cp2 against. 4. build and return the map of
   * dialerphonenumbers to their new Cp2ContactInfo
   *
   * @return Map of {@link DialerPhoneNumber} to {@link Cp2Info} with updated {@link
   *     Cp2ContactInfo}.
   */
  private ListenableFuture<Map<DialerPhoneNumber, Set<Cp2ContactInfo>>>
      buildMapForUpdatedOrAddedContacts(
          Map<DialerPhoneNumber, Cp2Info> existingInfoMap,
          long lastModified,
          Set<DialerPhoneNumber> deletedPhoneNumbers) {
    // Start by building a set of DialerPhoneNumbers that we want to update.
    ListenableFuture<Set<DialerPhoneNumber>> updatedNumbersFuture =
        findNumbersToUpdate(existingInfoMap, lastModified, deletedPhoneNumbers);

    return Futures.transformAsync(
        updatedNumbersFuture,
        updatedNumbers -> {
          if (updatedNumbers.isEmpty()) {
            return Futures.immediateFuture(new ArrayMap<>());
          }

          // Divide the numbers into those we can format to E164 and those we can't. Issue a single
          // batch query for the E164 numbers against the PHONE table, and in parallel issue
          // individual queries against PHONE_LOOKUP for each non-E164 number.
          // TODO(zachh): These queries are inefficient without a lastModified column to filter on.
          PartitionedNumbers partitionedNumbers =
              new PartitionedNumbers(ImmutableSet.copyOf(updatedNumbers));

          ListenableFuture<Map<String, Set<Cp2ContactInfo>>> e164Future =
              batchQueryForValidNumbers(partitionedNumbers.validE164Numbers());

          List<ListenableFuture<Set<Cp2ContactInfo>>> nonE164FuturesList = new ArrayList<>();
          for (String invalidNumber : partitionedNumbers.unformattableNumbers()) {
            nonE164FuturesList.add(individualQueryForInvalidNumber(invalidNumber));
          }

          ListenableFuture<List<Set<Cp2ContactInfo>>> nonE164Future =
              Futures.allAsList(nonE164FuturesList);

          Callable<Map<DialerPhoneNumber, Set<Cp2ContactInfo>>> computeMap =
              () -> {
                // These get() calls are safe because we are using whenAllSucceed below.
                Map<String, Set<Cp2ContactInfo>> e164Result = e164Future.get();
                List<Set<Cp2ContactInfo>> non164Results = nonE164Future.get();

                Map<DialerPhoneNumber, Set<Cp2ContactInfo>> map = new ArrayMap<>();

                // First update the map with the E164 results.
                for (Entry<String, Set<Cp2ContactInfo>> entry : e164Result.entrySet()) {
                  String e164Number = entry.getKey();
                  Set<Cp2ContactInfo> cp2ContactInfos = entry.getValue();

                  Set<DialerPhoneNumber> dialerPhoneNumbers =
                      partitionedNumbers.dialerPhoneNumbersForE164(e164Number);

                  addInfo(map, dialerPhoneNumbers, cp2ContactInfos);

                  // We are going to remove the numbers that we've handled so that we later can
                  // detect numbers that weren't handled and therefore need to have their contact
                  // information removed.
                  updatedNumbers.removeAll(dialerPhoneNumbers);
                }

                // Next update the map with the non-E164 results.
                int i = 0;
                for (String unformattableNumber : partitionedNumbers.unformattableNumbers()) {
                  Set<Cp2ContactInfo> cp2Infos = non164Results.get(i++);
                  Set<DialerPhoneNumber> dialerPhoneNumbers =
                      partitionedNumbers.dialerPhoneNumbersForUnformattable(unformattableNumber);

                  addInfo(map, dialerPhoneNumbers, cp2Infos);

                  // We are going to remove the numbers that we've handled so that we later can
                  // detect numbers that weren't handled and therefore need to have their contact
                  // information removed.
                  updatedNumbers.removeAll(dialerPhoneNumbers);
                }

                // The leftovers in updatedNumbers that weren't removed are numbers that were
                // previously associated with contacts, but are no longer. Remove the contact
                // information for them.
                for (DialerPhoneNumber dialerPhoneNumber : updatedNumbers) {
                  map.put(dialerPhoneNumber, ImmutableSet.of());
                }
                return map;
              };
          return Futures.whenAllSucceed(e164Future, nonE164Future)
              .call(computeMap, lightweightExecutorService);
        },
        lightweightExecutorService);
  }

  private ListenableFuture<Map<String, Set<Cp2ContactInfo>>> batchQueryForValidNumbers(
      Set<String> e164Numbers) {
    return backgroundExecutorService.submit(
        () -> {
          Map<String, Set<Cp2ContactInfo>> cp2ContactInfosByNumber = new ArrayMap<>();
          if (e164Numbers.isEmpty()) {
            return cp2ContactInfosByNumber;
          }
          try (Cursor cursor = queryPhoneTableBasedOnE164(PHONE_PROJECTION, e164Numbers)) {
            if (cursor == null) {
              LogUtil.w("Cp2PhoneLookup.batchQueryForValidNumbers", "null cursor");
            } else {
              while (cursor.moveToNext()) {
                String e164Number = cursor.getString(CP2_INFO_NORMALIZED_NUMBER_INDEX);
                Set<Cp2ContactInfo> cp2ContactInfos = cp2ContactInfosByNumber.get(e164Number);
                if (cp2ContactInfos == null) {
                  cp2ContactInfos = new ArraySet<>();
                  cp2ContactInfosByNumber.put(e164Number, cp2ContactInfos);
                }
                cp2ContactInfos.add(buildCp2ContactInfoFromPhoneCursor(appContext, cursor));
              }
            }
          }
          return cp2ContactInfosByNumber;
        });
  }

  private ListenableFuture<Set<Cp2ContactInfo>> individualQueryForInvalidNumber(
      String invalidNumber) {
    return backgroundExecutorService.submit(
        () -> {
          Set<Cp2ContactInfo> cp2ContactInfos = new ArraySet<>();
          if (invalidNumber.isEmpty()) {
            return cp2ContactInfos;
          }
          try (Cursor cursor = queryPhoneLookup(PHONE_LOOKUP_PROJECTION, invalidNumber)) {
            if (cursor == null) {
              LogUtil.w("Cp2PhoneLookup.individualQueryForInvalidNumber", "null cursor");
            } else {
              while (cursor.moveToNext()) {
                cp2ContactInfos.add(buildCp2ContactInfoFromPhoneCursor(appContext, cursor));
              }
            }
          }
          return cp2ContactInfos;
        });
  }

  /**
   * Adds the {@code cp2ContactInfo} to the entries for all specified {@code dialerPhoneNumbers} in
   * the {@code map}.
   */
  private static void addInfo(
      Map<DialerPhoneNumber, Set<Cp2ContactInfo>> map,
      Set<DialerPhoneNumber> dialerPhoneNumbers,
      Set<Cp2ContactInfo> cp2ContactInfos) {
    for (DialerPhoneNumber dialerPhoneNumber : dialerPhoneNumbers) {
      Set<Cp2ContactInfo> existingInfos = map.get(dialerPhoneNumber);
      if (existingInfos == null) {
        existingInfos = new ArraySet<>();
        map.put(dialerPhoneNumber, existingInfos);
      }
      existingInfos.addAll(cp2ContactInfos);
    }
  }

  private Cursor queryPhoneTableBasedOnE164(String[] projection, Set<String> validE164Numbers) {
    return appContext
        .getContentResolver()
        .query(
            Phone.CONTENT_URI,
            projection,
            Phone.NORMALIZED_NUMBER + " IN (" + questionMarks(validE164Numbers.size()) + ")",
            validE164Numbers.toArray(new String[validE164Numbers.size()]),
            null);
  }

  private Cursor queryPhoneLookup(String[] projection, String rawNumber) {
    Uri uri =
        Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(rawNumber));
    return appContext.getContentResolver().query(uri, projection, null, null, null);
  }

  /**
   * @param cursor with projection {@link #PHONE_PROJECTION}.
   * @return new {@link Cp2ContactInfo} based on current row of {@code cursor}.
   */
  private static Cp2ContactInfo buildCp2ContactInfoFromPhoneCursor(
      Context appContext, Cursor cursor) {
    String displayName = cursor.getString(CP2_INFO_NAME_INDEX);
    String photoUri = cursor.getString(CP2_INFO_PHOTO_URI_INDEX);
    int photoId = cursor.getInt(CP2_INFO_PHOTO_ID_INDEX);
    int type = cursor.getInt(CP2_INFO_TYPE_INDEX);
    String label = cursor.getString(CP2_INFO_LABEL_INDEX);
    int contactId = cursor.getInt(CP2_INFO_CONTACT_ID_INDEX);
    String lookupKey = cursor.getString(CP2_INFO_LOOKUP_KEY_INDEX);

    Cp2ContactInfo.Builder infoBuilder = Cp2ContactInfo.newBuilder();
    if (!TextUtils.isEmpty(displayName)) {
      infoBuilder.setName(displayName);
    }
    if (!TextUtils.isEmpty(photoUri)) {
      infoBuilder.setPhotoUri(photoUri);
    }
    if (photoId > 0) {
      infoBuilder.setPhotoId(photoId);
    }

    // Phone.getTypeLabel returns "Custom" if given (0, null) which is not of any use. Just
    // omit setting the label if there's no information for it.
    if (type != 0 || !TextUtils.isEmpty(label)) {
      infoBuilder.setLabel(Phone.getTypeLabel(appContext.getResources(), type, label).toString());
    }
    infoBuilder.setContactId(contactId);
    if (!TextUtils.isEmpty(lookupKey)) {
      infoBuilder.setLookupUri(Contacts.getLookupUri(contactId, lookupKey).toString());
    }
    return infoBuilder.build();
  }

  /** Returns set of DialerPhoneNumbers that were associated with now deleted contacts. */
  private ListenableFuture<Set<DialerPhoneNumber>> getDeletedPhoneNumbers(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap, long lastModified) {
    return backgroundExecutorService.submit(
        () -> {
          // Build set of all contact IDs from our existing data. We're going to use this set to
          // query against the DeletedContacts table and see if any of them were deleted.
          Set<Long> contactIds = findContactIdsIn(existingInfoMap);

          // Start building a set of DialerPhoneNumbers that were associated with now deleted
          // contacts.
          try (Cursor cursor = queryDeletedContacts(contactIds, lastModified)) {
            // We now have a cursor/list of contact IDs that were associated with deleted contacts.
            return findDeletedPhoneNumbersIn(existingInfoMap, cursor);
          }
        });
  }

  private Set<Long> findContactIdsIn(ImmutableMap<DialerPhoneNumber, Cp2Info> map) {
    Set<Long> contactIds = new ArraySet<>();
    for (Cp2Info info : map.values()) {
      for (Cp2ContactInfo cp2ContactInfo : info.getCp2ContactInfoList()) {
        contactIds.add(cp2ContactInfo.getContactId());
      }
    }
    return contactIds;
  }

  private Cursor queryDeletedContacts(Set<Long> contactIds, long lastModified) {
    String where =
        DeletedContacts.CONTACT_DELETED_TIMESTAMP
            + " > ?"
            + " AND "
            + DeletedContacts.CONTACT_ID
            + " IN ("
            + questionMarks(contactIds.size())
            + ")";
    String[] args = new String[contactIds.size() + 1];
    args[0] = Long.toString(lastModified);
    int i = 1;
    for (Long contactId : contactIds) {
      args[i++] = Long.toString(contactId);
    }

    return appContext
        .getContentResolver()
        .query(
            DeletedContacts.CONTENT_URI,
            new String[] {DeletedContacts.CONTACT_ID, DeletedContacts.CONTACT_DELETED_TIMESTAMP},
            where,
            args,
            null);
  }

  /** Returns set of DialerPhoneNumbers that are associated with deleted contact IDs. */
  private Set<DialerPhoneNumber> findDeletedPhoneNumbersIn(
      ImmutableMap<DialerPhoneNumber, Cp2Info> existingInfoMap, Cursor cursor) {
    int contactIdIndex = cursor.getColumnIndexOrThrow(DeletedContacts.CONTACT_ID);
    int deletedTimeIndex = cursor.getColumnIndexOrThrow(DeletedContacts.CONTACT_DELETED_TIMESTAMP);
    Set<DialerPhoneNumber> deletedPhoneNumbers = new ArraySet<>();
    cursor.moveToPosition(-1);
    while (cursor.moveToNext()) {
      long contactId = cursor.getLong(contactIdIndex);
      deletedPhoneNumbers.addAll(
          findDialerPhoneNumbersContainingContactId(existingInfoMap, contactId));
      long deletedTime = cursor.getLong(deletedTimeIndex);
      if (currentLastTimestampProcessed == null || currentLastTimestampProcessed < deletedTime) {
        // TODO(zachh): There's a problem here if a contact for a new row is deleted?
        currentLastTimestampProcessed = deletedTime;
      }
    }
    return deletedPhoneNumbers;
  }

  private static Set<DialerPhoneNumber> findDialerPhoneNumbersContainingContactId(
      Map<DialerPhoneNumber, Cp2Info> existingInfoMap, long contactId) {
    Set<DialerPhoneNumber> matches = new ArraySet<>();
    for (Entry<DialerPhoneNumber, Cp2Info> entry : existingInfoMap.entrySet()) {
      for (Cp2ContactInfo cp2ContactInfo : entry.getValue().getCp2ContactInfoList()) {
        if (cp2ContactInfo.getContactId() == contactId) {
          matches.add(entry.getKey());
        }
      }
    }
    Assert.checkArgument(
        matches.size() > 0, "Couldn't find DialerPhoneNumber for contact ID: " + contactId);
    return matches;
  }

  private static String questionMarks(int count) {
    StringBuilder where = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i != 0) {
        where.append(", ");
      }
      where.append("?");
    }
    return where.toString();
  }
}

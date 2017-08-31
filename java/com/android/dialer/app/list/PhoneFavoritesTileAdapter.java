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
package com.android.dialer.app.list;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PinnedPositions;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.list.ContactEntry;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.app.R;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lightbringer.Lightbringer;
import com.android.dialer.lightbringer.LightbringerComponent;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;
import com.android.dialer.shortcuts.ShortcutRefresher;
import com.google.common.collect.ComparisonChain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

/** Also allows for a configurable number of columns as well as a maximum row of tiled contacts. */
public class PhoneFavoritesTileAdapter extends BaseAdapter implements OnDragDropListener {

  // Pinned positions start from 1, so there are a total of 20 maximum pinned contacts
  private static final int PIN_LIMIT = 21;
  private static final String TAG = PhoneFavoritesTileAdapter.class.getSimpleName();
  private static final boolean DEBUG = false;
  /**
   * The soft limit on how many contact tiles to show. NOTE This soft limit would not restrict the
   * number of starred contacts to show, rather 1. If the count of starred contacts is less than
   * this limit, show 20 tiles total. 2. If the count of starred contacts is more than or equal to
   * this limit, show all starred tiles and no frequents.
   */
  private static final int TILES_SOFT_LIMIT = 20;
  /** Contact data stored in cache. This is used to populate the associated view. */
  private ArrayList<ContactEntry> mContactEntries = null;

  private int mNumFrequents;
  private int mNumStarred;

  private ContactTileView.Listener mListener;
  private OnDataSetChangedForAnimationListener mDataSetChangedListener;
  private Context mContext;
  private Resources mResources;
  private ContactsPreferences mContactsPreferences;
  private final Comparator<ContactEntry> mContactEntryComparator =
      new Comparator<ContactEntry>() {
        @Override
        public int compare(ContactEntry lhs, ContactEntry rhs) {
          return ComparisonChain.start()
              .compare(lhs.pinned, rhs.pinned)
              .compare(getPreferredSortName(lhs), getPreferredSortName(rhs))
              .result();
        }

        private String getPreferredSortName(ContactEntry contactEntry) {
          if (mContactsPreferences.getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY
              || TextUtils.isEmpty(contactEntry.nameAlternative)) {
            return contactEntry.namePrimary;
          }
          return contactEntry.nameAlternative;
        }
      };
  /** Back up of the temporarily removed Contact during dragging. */
  private ContactEntry mDraggedEntry = null;
  /** Position of the temporarily removed contact in the cache. */
  private int mDraggedEntryIndex = -1;
  /** New position of the temporarily removed contact in the cache. */
  private int mDropEntryIndex = -1;
  /** New position of the temporarily entered contact in the cache. */
  private int mDragEnteredEntryIndex = -1;

  private boolean mAwaitingRemove = false;
  private boolean mDelayCursorUpdates = false;
  private ContactPhotoManager mPhotoManager;

  /** Indicates whether a drag is in process. */
  private boolean mInDragging = false;

  public PhoneFavoritesTileAdapter(
      Context context,
      ContactTileView.Listener listener,
      OnDataSetChangedForAnimationListener dataSetChangedListener) {
    mDataSetChangedListener = dataSetChangedListener;
    mListener = listener;
    mContext = context;
    mResources = context.getResources();
    mContactsPreferences = new ContactsPreferences(mContext);
    mNumFrequents = 0;
    mContactEntries = new ArrayList<>();
  }

  void setPhotoLoader(ContactPhotoManager photoLoader) {
    mPhotoManager = photoLoader;
  }

  /**
   * Indicates whether a drag is in process.
   *
   * @param inDragging Boolean variable indicating whether there is a drag in process.
   */
  private void setInDragging(boolean inDragging) {
    mDelayCursorUpdates = inDragging;
    mInDragging = inDragging;
  }

  void refreshContactsPreferences() {
    mContactsPreferences.refreshValue(ContactsPreferences.DISPLAY_ORDER_KEY);
    mContactsPreferences.refreshValue(ContactsPreferences.SORT_ORDER_KEY);
  }

  /**
   * Gets the number of frequents from the passed in cursor.
   *
   * <p>This methods is needed so the GroupMemberTileAdapter can override this.
   *
   * @param cursor The cursor to get number of frequents from.
   */
  private void saveNumFrequentsFromCursor(Cursor cursor) {
    mNumFrequents = cursor.getCount() - mNumStarred;
  }

  /**
   * Creates {@link ContactTileView}s for each item in {@link Cursor}.
   *
   * <p>Else use {@link ContactTileLoaderFactory}
   */
  void setContactCursor(Cursor cursor) {
    if (!mDelayCursorUpdates && cursor != null && !cursor.isClosed()) {
      mNumStarred = getNumStarredContacts(cursor);
      if (mAwaitingRemove) {
        mDataSetChangedListener.cacheOffsetsForDatasetChange();
      }

      saveNumFrequentsFromCursor(cursor);
      saveCursorToCache(cursor);
      // cause a refresh of any views that rely on this data
      notifyDataSetChanged();
      // about to start redraw
      mDataSetChangedListener.onDataSetChangedForAnimation();
    }
  }

  /**
   * Saves the cursor data to the cache, to speed up UI changes.
   *
   * @param cursor Returned cursor from {@link ContactTileLoaderFactory} with data to populate the
   *     view.
   */
  private void saveCursorToCache(Cursor cursor) {
    mContactEntries.clear();

    if (cursor == null) {
      return;
    }

    final LongSparseArray<Object> duplicates = new LongSparseArray<>(cursor.getCount());

    // Track the length of {@link #mContactEntries} and compare to {@link #TILES_SOFT_LIMIT}.
    int counter = 0;

    // Data for logging
    int starredContactsCount = 0;
    int pinnedContactsCount = 0;
    int multipleNumbersContactsCount = 0;
    int contactsWithPhotoCount = 0;
    int contactsWithNameCount = 0;
    int lightbringerReachableContactsCount = 0;

    // The cursor should not be closed since this is invoked from a CursorLoader.
    if (cursor.moveToFirst()) {
      int starredColumn = cursor.getColumnIndexOrThrow(Contacts.STARRED);
      int contactIdColumn = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID);
      int photoUriColumn = cursor.getColumnIndexOrThrow(Contacts.PHOTO_URI);
      int lookupKeyColumn = cursor.getColumnIndexOrThrow(Contacts.LOOKUP_KEY);
      int pinnedColumn = cursor.getColumnIndexOrThrow(Contacts.PINNED);
      int nameColumn = cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME_PRIMARY);
      int nameAlternativeColumn = cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME_ALTERNATIVE);
      int isDefaultNumberColumn = cursor.getColumnIndexOrThrow(Phone.IS_SUPER_PRIMARY);
      int phoneTypeColumn = cursor.getColumnIndexOrThrow(Phone.TYPE);
      int phoneLabelColumn = cursor.getColumnIndexOrThrow(Phone.LABEL);
      int phoneNumberColumn = cursor.getColumnIndexOrThrow(Phone.NUMBER);
      do {
        final int starred = cursor.getInt(starredColumn);
        final long id;

        // We display a maximum of TILES_SOFT_LIMIT contacts, or the total number of starred
        // whichever is greater.
        if (starred < 1 && counter >= TILES_SOFT_LIMIT) {
          break;
        } else {
          id = cursor.getLong(contactIdColumn);
        }

        final ContactEntry existing = (ContactEntry) duplicates.get(id);
        if (existing != null) {
          // Check if the existing number is a default number. If not, clear the phone number
          // and label fields so that the disambiguation dialog will show up.
          if (!existing.isDefaultNumber) {
            existing.phoneLabel = null;
            existing.phoneNumber = null;
          }
          continue;
        }

        final String photoUri = cursor.getString(photoUriColumn);
        final String lookupKey = cursor.getString(lookupKeyColumn);
        final int pinned = cursor.getInt(pinnedColumn);
        final String name = cursor.getString(nameColumn);
        final String nameAlternative = cursor.getString(nameAlternativeColumn);
        final boolean isStarred = cursor.getInt(starredColumn) > 0;
        final boolean isDefaultNumber = cursor.getInt(isDefaultNumberColumn) > 0;

        final ContactEntry contact = new ContactEntry();

        contact.id = id;
        contact.namePrimary =
            (!TextUtils.isEmpty(name)) ? name : mResources.getString(R.string.missing_name);
        contact.nameAlternative =
            (!TextUtils.isEmpty(nameAlternative))
                ? nameAlternative
                : mResources.getString(R.string.missing_name);
        contact.nameDisplayOrder = mContactsPreferences.getDisplayOrder();
        contact.photoUri = (photoUri != null ? Uri.parse(photoUri) : null);
        contact.lookupKey = lookupKey;
        contact.lookupUri =
            ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey), id);
        contact.isFavorite = isStarred;
        contact.isDefaultNumber = isDefaultNumber;

        // Set phone number and label
        final int phoneNumberType = cursor.getInt(phoneTypeColumn);
        final String phoneNumberCustomLabel = cursor.getString(phoneLabelColumn);
        contact.phoneLabel =
            (String) Phone.getTypeLabel(mResources, phoneNumberType, phoneNumberCustomLabel);
        contact.phoneNumber = cursor.getString(phoneNumberColumn);

        contact.pinned = pinned;
        mContactEntries.add(contact);

        // Set counts for logging
        if (isStarred) {
          // mNumStarred might be larger than the number of visible starred contact,
          // since it includes invisible ones (starred contact with no phone number).
          starredContactsCount++;
        }
        if (pinned != PinnedPositions.UNPINNED) {
          pinnedContactsCount++;
        }
        if (!TextUtils.isEmpty(name)) {
          contactsWithNameCount++;
        }
        if (photoUri != null) {
          contactsWithPhotoCount++;
        }

        duplicates.put(id, contact);

        counter++;
      } while (cursor.moveToNext());
    }

    mAwaitingRemove = false;

    arrangeContactsByPinnedPosition(mContactEntries);

    ShortcutRefresher.refresh(mContext, mContactEntries);
    notifyDataSetChanged();

    Lightbringer lightbringer = LightbringerComponent.get(mContext).getLightbringer();
    for (ContactEntry contact : mContactEntries) {
      if (contact.phoneNumber == null) {
        multipleNumbersContactsCount++;
      } else if (lightbringer.isReachable(mContext, contact.phoneNumber)) {
        lightbringerReachableContactsCount++;
      }
    }

    Logger.get(mContext)
        .logSpeedDialContactComposition(
            counter,
            starredContactsCount,
            pinnedContactsCount,
            multipleNumbersContactsCount,
            contactsWithPhotoCount,
            contactsWithNameCount,
            lightbringerReachableContactsCount);
    // Logs for manual testing
    LogUtil.v("PhoneFavoritesTileAdapter.saveCursorToCache", "counter: %d", counter);
    LogUtil.v(
        "PhoneFavoritesTileAdapter.saveCursorToCache",
        "starredContactsCount: %d",
        starredContactsCount);
    LogUtil.v(
        "PhoneFavoritesTileAdapter.saveCursorToCache",
        "pinnedContactsCount: %d",
        pinnedContactsCount);
    LogUtil.v(
        "PhoneFavoritesTileAdapter.saveCursorToCache",
        "multipleNumbersContactsCount: %d",
        multipleNumbersContactsCount);
    LogUtil.v(
        "PhoneFavoritesTileAdapter.saveCursorToCache",
        "contactsWithPhotoCount: %d",
        contactsWithPhotoCount);
    LogUtil.v(
        "PhoneFavoritesTileAdapter.saveCursorToCache",
        "contactsWithNameCount: %d",
        contactsWithNameCount);
  }

  /** Iterates over the {@link Cursor} Returns position of the first NON Starred Contact */
  private int getNumStarredContacts(Cursor cursor) {
    if (cursor == null) {
      return 0;
    }

    if (cursor.moveToFirst()) {
      int starredColumn = cursor.getColumnIndex(Contacts.STARRED);
      do {
        if (cursor.getInt(starredColumn) == 0) {
          return cursor.getPosition();
        }
      } while (cursor.moveToNext());
    }
    // There are not NON Starred contacts in cursor
    // Set divider position to end
    return cursor.getCount();
  }

  /** Returns the number of frequents that will be displayed in the list. */
  int getNumFrequents() {
    return mNumFrequents;
  }

  @Override
  public int getCount() {
    if (mContactEntries == null) {
      return 0;
    }

    return mContactEntries.size();
  }

  /**
   * Returns an ArrayList of the {@link ContactEntry}s that are to appear on the row for the given
   * position.
   */
  @Override
  public ContactEntry getItem(int position) {
    return mContactEntries.get(position);
  }

  /**
   * For the top row of tiled contacts, the item id is the position of the row of contacts. For
   * frequent contacts, the item id is the maximum number of rows of tiled contacts + the actual
   * contact id. Since contact ids are always greater than 0, this guarantees that all items within
   * this adapter will always have unique ids.
   */
  @Override
  public long getItemId(int position) {
    return getItem(position).id;
  }

  @Override
  public boolean hasStableIds() {
    return true;
  }

  @Override
  public boolean areAllItemsEnabled() {
    return true;
  }

  @Override
  public boolean isEnabled(int position) {
    return getCount() > 0;
  }

  @Override
  public void notifyDataSetChanged() {
    if (DEBUG) {
      LogUtil.v(TAG, "notifyDataSetChanged");
    }
    super.notifyDataSetChanged();
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (DEBUG) {
      LogUtil.v(TAG, "get view for " + position);
    }

    PhoneFavoriteTileView tileView = null;

    if (convertView instanceof PhoneFavoriteTileView) {
      tileView = (PhoneFavoriteTileView) convertView;
    }

    if (tileView == null) {
      tileView =
          (PhoneFavoriteTileView) View.inflate(mContext, R.layout.phone_favorite_tile_view, null);
    }
    tileView.setPhotoManager(mPhotoManager);
    tileView.setListener(mListener);
    tileView.loadFromContact(getItem(position));
    tileView.setPosition(position);
    return tileView;
  }

  @Override
  public int getViewTypeCount() {
    return ViewTypes.COUNT;
  }

  @Override
  public int getItemViewType(int position) {
    return ViewTypes.TILE;
  }

  /**
   * Temporarily removes a contact from the list for UI refresh. Stores data for this contact in the
   * back-up variable.
   *
   * @param index Position of the contact to be removed.
   */
  private void popContactEntry(int index) {
    if (isIndexInBound(index)) {
      mDraggedEntry = mContactEntries.get(index);
      mDraggedEntryIndex = index;
      mDragEnteredEntryIndex = index;
      markDropArea(mDragEnteredEntryIndex);
    }
  }

  /**
   * @param itemIndex Position of the contact in {@link #mContactEntries}.
   * @return True if the given index is valid for {@link #mContactEntries}.
   */
  boolean isIndexInBound(int itemIndex) {
    return itemIndex >= 0 && itemIndex < mContactEntries.size();
  }

  /**
   * Mark the tile as drop area by given the item index in {@link #mContactEntries}.
   *
   * @param itemIndex Position of the contact in {@link #mContactEntries}.
   */
  private void markDropArea(int itemIndex) {
    if (mDraggedEntry != null
        && isIndexInBound(mDragEnteredEntryIndex)
        && isIndexInBound(itemIndex)) {
      mDataSetChangedListener.cacheOffsetsForDatasetChange();
      // Remove the old placeholder item and place the new placeholder item.
      mContactEntries.remove(mDragEnteredEntryIndex);
      mDragEnteredEntryIndex = itemIndex;
      mContactEntries.add(mDragEnteredEntryIndex, ContactEntry.BLANK_ENTRY);
      ContactEntry.BLANK_ENTRY.id = mDraggedEntry.id;
      mDataSetChangedListener.onDataSetChangedForAnimation();
      notifyDataSetChanged();
    }
  }

  /** Drops the temporarily removed contact to the desired location in the list. */
  private void handleDrop() {
    boolean changed = false;
    if (mDraggedEntry != null) {
      if (isIndexInBound(mDragEnteredEntryIndex) && mDragEnteredEntryIndex != mDraggedEntryIndex) {
        // Don't add the ContactEntry here (to prevent a double animation from occuring).
        // When we receive a new cursor the list of contact entries will automatically be
        // populated with the dragged ContactEntry at the correct spot.
        mDropEntryIndex = mDragEnteredEntryIndex;
        mContactEntries.set(mDropEntryIndex, mDraggedEntry);
        mDataSetChangedListener.cacheOffsetsForDatasetChange();
        changed = true;
      } else if (isIndexInBound(mDraggedEntryIndex)) {
        // If {@link #mDragEnteredEntryIndex} is invalid,
        // falls back to the original position of the contact.
        mContactEntries.remove(mDragEnteredEntryIndex);
        mContactEntries.add(mDraggedEntryIndex, mDraggedEntry);
        mDropEntryIndex = mDraggedEntryIndex;
        notifyDataSetChanged();
      }

      if (changed && mDropEntryIndex < PIN_LIMIT) {
        final ArrayList<ContentProviderOperation> operations =
            getReflowedPinningOperations(mContactEntries, mDraggedEntryIndex, mDropEntryIndex);
        if (!operations.isEmpty()) {
          // update the database here with the new pinned positions
          try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
            Logger.get(mContext).logInteraction(InteractionEvent.Type.SPEED_DIAL_PIN_CONTACT);
          } catch (RemoteException | OperationApplicationException e) {
            LogUtil.e(TAG, "Exception thrown when pinning contacts", e);
          }
        }
      }
      mDraggedEntry = null;
    }
  }

  /**
   * Used when a contact is removed from speeddial. This will both unstar and set pinned position of
   * the contact to PinnedPosition.DEMOTED so that it doesn't show up anymore in the favorites list.
   */
  private void unstarAndUnpinContact(Uri contactUri) {
    final ContentValues values = new ContentValues(2);
    values.put(Contacts.STARRED, false);
    values.put(Contacts.PINNED, PinnedPositions.DEMOTED);
    mContext.getContentResolver().update(contactUri, values, null, null);
  }

  /**
   * Given a list of contacts that each have pinned positions, rearrange the list (destructive) such
   * that all pinned contacts are in their defined pinned positions, and unpinned contacts take the
   * spaces between those pinned contacts. Demoted contacts should not appear in the resulting list.
   *
   * <p>This method also updates the pinned positions of pinned contacts so that they are all unique
   * positive integers within range from 0 to toArrange.size() - 1. This is because when the contact
   * entries are read from the database, it is possible for them to have overlapping pin positions
   * due to sync or modifications by third party apps.
   */
  @VisibleForTesting
  private void arrangeContactsByPinnedPosition(ArrayList<ContactEntry> toArrange) {
    final PriorityQueue<ContactEntry> pinnedQueue =
        new PriorityQueue<>(PIN_LIMIT, mContactEntryComparator);

    final List<ContactEntry> unpinnedContacts = new LinkedList<>();

    final int length = toArrange.size();
    for (int i = 0; i < length; i++) {
      final ContactEntry contact = toArrange.get(i);
      // Decide whether the contact is hidden(demoted), pinned, or unpinned
      if (contact.pinned > PIN_LIMIT || contact.pinned == PinnedPositions.UNPINNED) {
        unpinnedContacts.add(contact);
      } else if (contact.pinned > PinnedPositions.DEMOTED) {
        // Demoted or contacts with negative pinned positions are ignored.
        // Pinned contacts go into a priority queue where they are ranked by pinned
        // position. This is required because the contacts provider does not return
        // contacts ordered by pinned position.
        pinnedQueue.add(contact);
      }
    }

    final int maxToPin = Math.min(PIN_LIMIT, pinnedQueue.size() + unpinnedContacts.size());

    toArrange.clear();
    for (int i = 1; i < maxToPin + 1; i++) {
      if (!pinnedQueue.isEmpty() && pinnedQueue.peek().pinned <= i) {
        final ContactEntry toPin = pinnedQueue.poll();
        toPin.pinned = i;
        toArrange.add(toPin);
      } else if (!unpinnedContacts.isEmpty()) {
        toArrange.add(unpinnedContacts.remove(0));
      }
    }

    // If there are still contacts in pinnedContacts at this point, it means that the pinned
    // positions of these pinned contacts exceed the actual number of contacts in the list.
    // For example, the user had 10 frequents, starred and pinned one of them at the last spot,
    // and then cleared frequents. Contacts in this situation should become unpinned.
    while (!pinnedQueue.isEmpty()) {
      final ContactEntry entry = pinnedQueue.poll();
      entry.pinned = PinnedPositions.UNPINNED;
      toArrange.add(entry);
    }

    // Any remaining unpinned contacts that weren't in the gaps between the pinned contacts
    // now just get appended to the end of the list.
    toArrange.addAll(unpinnedContacts);
  }

  /**
   * Given an existing list of contact entries and a single entry that is to be pinned at a
   * particular position, return a list of {@link ContentProviderOperation}s that contains new
   * pinned positions for all contacts that are forced to be pinned at new positions, trying as much
   * as possible to keep pinned contacts at their original location.
   *
   * <p>At this point in time the pinned position of each contact in the list has already been
   * updated by {@link #arrangeContactsByPinnedPosition}, so we can assume that all pinned
   * positions(within {@link #PIN_LIMIT} are unique positive integers.
   */
  @VisibleForTesting
  private ArrayList<ContentProviderOperation> getReflowedPinningOperations(
      ArrayList<ContactEntry> list, int oldPos, int newPinPos) {
    final ArrayList<ContentProviderOperation> positions = new ArrayList<>();
    final int lowerBound = Math.min(oldPos, newPinPos);
    final int upperBound = Math.max(oldPos, newPinPos);
    for (int i = lowerBound; i <= upperBound; i++) {
      final ContactEntry entry = list.get(i);

      // Pinned positions in the database start from 1 instead of being zero-indexed like
      // arrays, so offset by 1.
      final int databasePinnedPosition = i + 1;
      if (entry.pinned == databasePinnedPosition) {
        continue;
      }

      final Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(entry.id));
      final ContentValues values = new ContentValues();
      values.put(Contacts.PINNED, databasePinnedPosition);
      positions.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());
    }
    return positions;
  }

  @Override
  public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view) {
    setInDragging(true);
    final int itemIndex = mContactEntries.indexOf(view.getContactEntry());
    popContactEntry(itemIndex);
  }

  @Override
  public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {
    if (view == null) {
      // The user is hovering over a view that is not a contact tile, no need to do
      // anything here.
      return;
    }
    final int itemIndex = mContactEntries.indexOf(view.getContactEntry());
    if (mInDragging
        && mDragEnteredEntryIndex != itemIndex
        && isIndexInBound(itemIndex)
        && itemIndex < PIN_LIMIT
        && itemIndex >= 0) {
      markDropArea(itemIndex);
    }
  }

  @Override
  public void onDragFinished(int x, int y) {
    setInDragging(false);
    // A contact has been dragged to the RemoveView in order to be unstarred,  so simply wait
    // for the new contact cursor which will cause the UI to be refreshed without the unstarred
    // contact.
    if (!mAwaitingRemove) {
      handleDrop();
    }
  }

  @Override
  public void onDroppedOnRemove() {
    if (mDraggedEntry != null) {
      unstarAndUnpinContact(mDraggedEntry.lookupUri);
      mAwaitingRemove = true;
      Logger.get(mContext).logInteraction(InteractionEvent.Type.SPEED_DIAL_REMOVE_CONTACT);
    }
  }

  interface OnDataSetChangedForAnimationListener {

    void onDataSetChangedForAnimation(long... idsInPlace);

    void cacheOffsetsForDatasetChange();
  }

  private static class ViewTypes {

    static final int TILE = 0;
    static final int COUNT = 1;
  }
}

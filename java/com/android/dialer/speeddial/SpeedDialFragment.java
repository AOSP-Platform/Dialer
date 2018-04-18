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

package com.android.dialer.speeddial;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.SupportUiListener;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.precall.PreCall;
import com.android.dialer.speeddial.ContextMenu.ContextMenuItemListener;
import com.android.dialer.speeddial.FavoritesViewHolder.FavoriteContactsListener;
import com.android.dialer.speeddial.HeaderViewHolder.SpeedDialHeaderListener;
import com.android.dialer.speeddial.SuggestionViewHolder.SuggestedContactsListener;
import com.android.dialer.speeddial.database.SpeedDialEntry.Channel;
import com.android.dialer.speeddial.draghelper.SpeedDialItemTouchHelperCallback;
import com.android.dialer.speeddial.draghelper.SpeedDialLayoutManager;
import com.android.dialer.speeddial.loader.SpeedDialUiItem;
import com.android.dialer.speeddial.loader.UiItemLoaderComponent;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Fragment for displaying:
 *
 * <ul>
 *   <li>Favorite/Starred contacts
 *   <li>Suggested contacts
 * </ul>
 *
 * <p>Suggested contacts built from {@link android.provider.ContactsContract#STREQUENT_PHONE_ONLY}.
 */
public class SpeedDialFragment extends Fragment {

  private final SpeedDialHeaderListener headerListener = new SpeedDialFragmentHeaderListener();
  private final FavoriteContactsListener favoritesListener = new SpeedDialFavoritesListener();
  private final SuggestedContactsListener suggestedListener = new SpeedDialSuggestedListener();

  private View rootLayout;
  private ContextMenu contextMenu;
  private FrameLayout contextMenuBackground;
  private ContextMenuItemListener contextMenuItemListener;

  private SpeedDialAdapter adapter;
  private SpeedDialLayoutManager layoutManager;
  private SupportUiListener<ImmutableList<SpeedDialUiItem>> speedDialLoaderListener;

  public static SpeedDialFragment newInstance() {
    return new SpeedDialFragment();
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.enterBlock("SpeedDialFragment.onCreateView");
    rootLayout = inflater.inflate(R.layout.fragment_speed_dial, container, false);

    // Setup our RecyclerView
    RecyclerView recyclerView = rootLayout.findViewById(R.id.speed_dial_recycler_view);
    adapter =
        new SpeedDialAdapter(getContext(), favoritesListener, suggestedListener, headerListener);
    layoutManager = new SpeedDialLayoutManager(getContext(), 3 /* spanCount */);
    layoutManager.setSpanSizeLookup(adapter.getSpanSizeLookup());
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(adapter);

    // Setup drag and drop touch helper
    ItemTouchHelper.Callback callback = new SpeedDialItemTouchHelperCallback(adapter);
    ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
    touchHelper.attachToRecyclerView(recyclerView);
    adapter.setItemTouchHelper(touchHelper);

    // Setup favorite contact context menu
    contextMenu = rootLayout.findViewById(R.id.favorite_contact_context_menu);
    contextMenuBackground = rootLayout.findViewById(R.id.context_menu_background);
    contextMenuBackground.setOnClickListener(
        v -> {
          contextMenu.hideMenu();
          contextMenuBackground.setVisibility(View.GONE);
        });
    contextMenuItemListener = new SpeedDialContextMenuItemListener();

    speedDialLoaderListener =
        DialerExecutorComponent.get(getContext())
            .createUiListener(getChildFragmentManager(), "speed_dial_loader_listener");
    return rootLayout;
  }

  public boolean hasFrequents() {
    // TODO(calderwoodra)
    return false;
  }

  @Override
  public void onResume() {
    super.onResume();
    speedDialLoaderListener.listen(
        getContext(),
        UiItemLoaderComponent.get(getContext()).speedDialUiItemLoader().loadSpeedDialUiItems(),
        speedDialUiItems -> {
          adapter.setSpeedDialUiItems(
              UiItemLoaderComponent.get(getContext())
                  .speedDialUiItemLoader()
                  .insertDuoChannels(getContext(), speedDialUiItems));
          adapter.notifyDataSetChanged();
        },
        throwable -> {
          throw new RuntimeException(throwable);
        });
  }

  private class SpeedDialFragmentHeaderListener implements SpeedDialHeaderListener {

    @Override
    public void onAddFavoriteClicked() {
      startActivity(new Intent(getContext(), AddFavoriteActivity.class));
    }
  }

  private final class SpeedDialFavoritesListener implements FavoriteContactsListener {

    @Override
    public void onAmbiguousContactClicked(List<Channel> channels) {
      DisambigDialog.show(channels, getChildFragmentManager());
    }

    @Override
    public void onClick(Channel channel) {
      if (channel.technology() == Channel.DUO) {
        Logger.get(getContext())
            .logImpression(DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FOR_FAVORITE_CONTACT);
        Intent intent =
            DuoComponent.get(getContext()).getDuo().getIntent(getContext(), channel.number());
        getActivity().startActivityForResult(intent, ActivityRequestCodes.DIALTACTS_DUO);
        return;
      }

      PreCall.start(
          getContext(),
          new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
              .setIsVideoCall(channel.isVideoTechnology()));
    }

    @Override
    public void showContextMenu(View view, SpeedDialUiItem speedDialUiItem) {
      layoutManager.setScrollEnabled(false);
      contextMenu.showMenu(rootLayout, view, speedDialUiItem, contextMenuItemListener);
    }

    @Override
    public void onTouchFinished(boolean closeContextMenu) {
      layoutManager.setScrollEnabled(true);

      if (closeContextMenu) {
        contextMenu.hideMenu();
      } else if (contextMenu.isVisible()) {
        // If we're showing the context menu, show this background surface so that we can intercept
        // touch events to close the menu
        // Note: We call this in onTouchFinished because if we show the background before the user
        // is done, they might try to drag the view and but won't be able to because this view would
        // intercept all of the touch events.
        contextMenuBackground.setVisibility(View.VISIBLE);
      }
    }
  }

  private final class SpeedDialSuggestedListener implements SuggestedContactsListener {

    @Override
    public void onOverFlowMenuClicked(SpeedDialUiItem speedDialUiItem) {
      // TODO(calderwoodra) show overflow menu for suggested contacts
    }

    @Override
    public void onRowClicked(Channel channel) {
      if (channel.technology() == Channel.DUO) {
        Logger.get(getContext())
            .logImpression(
                DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FOR_SUGGESTED_CONTACT);
        Intent intent =
            DuoComponent.get(getContext()).getDuo().getIntent(getContext(), channel.number());
        getActivity().startActivityForResult(intent, ActivityRequestCodes.DIALTACTS_DUO);
        return;
      }
      PreCall.start(
          getContext(),
          new CallIntentBuilder(channel.number(), CallInitiationType.Type.SPEED_DIAL)
              .setIsVideoCall(channel.isVideoTechnology()));
    }
  }

  private static final class SpeedDialContextMenuItemListener implements ContextMenuItemListener {

    @Override
    public void placeVoiceCall(SpeedDialUiItem speedDialUiItem) {
      // TODO(calderwoodra)
    }

    @Override
    public void placeVideoCall(SpeedDialUiItem speedDialUiItem) {
      // TODO(calderwoodra)
    }

    @Override
    public void openSmsConversation(SpeedDialUiItem speedDialUiItem) {
      // TODO(calderwoodra)
    }

    @Override
    public void removeFavoriteContact(SpeedDialUiItem speedDialUiItem) {
      // TODO(calderwoodra)
    }

    @Override
    public void openContactInfo(SpeedDialUiItem speedDialUiItem) {
      // TODO(calderwoodra)
    }
  }
}

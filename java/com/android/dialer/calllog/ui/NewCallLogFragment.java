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

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.calllog.CallLogComponent;
import com.android.dialer.calllog.CallLogFramework;
import com.android.dialer.calllog.CallLogFramework.CallLogUi;
import com.android.dialer.calllog.RefreshAnnotatedCallLogWorker;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.common.concurrent.UiListener;
import com.google.common.util.concurrent.ListenableScheduledFuture;

/** The "new" call log fragment implementation, which is built on top of the annotated call log. */
public final class NewCallLogFragment extends Fragment
    implements CallLogUi, LoaderCallbacks<Cursor> {

  private RefreshAnnotatedCallLogWorker refreshAnnotatedCallLogWorker;
  private UiListener<Void> refreshAnnotatedCallLogListener;
  private RecyclerView recyclerView;

  public NewCallLogFragment() {
    LogUtil.enterBlock("NewCallLogFragment.NewCallLogFragment");
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);

    LogUtil.enterBlock("NewCallLogFragment.onCreate");

    CallLogComponent component = CallLogComponent.get(getContext());
    CallLogFramework callLogFramework = component.callLogFramework();
    callLogFramework.attachUi(this);

    // TODO(zachh): Use support fragment manager and add support for them in executors library.
    refreshAnnotatedCallLogListener =
        DialerExecutorComponent.get(getContext())
            .createUiListener(
                getActivity().getFragmentManager(), "NewCallLogFragment.refreshAnnotatedCallLog");
    refreshAnnotatedCallLogWorker = component.getRefreshAnnotatedCallLogWorker();
  }

  @Override
  public void onStart() {
    super.onStart();

    LogUtil.enterBlock("NewCallLogFragment.onStart");
  }

  @Override
  public void onResume() {
    super.onResume();

    LogUtil.enterBlock("NewCallLogFragment.onResume");

    CallLogFramework callLogFramework = CallLogComponent.get(getContext()).callLogFramework();
    callLogFramework.attachUi(this);

    // TODO(zachh): Consider doing this when fragment becomes visible.
    checkAnnotatedCallLogDirtyAndRefreshIfNecessary();
  }

  @Override
  public void onPause() {
    super.onPause();

    LogUtil.enterBlock("NewCallLogFragment.onPause");

    CallLogFramework callLogFramework = CallLogComponent.get(getContext()).callLogFramework();
    callLogFramework.detachUi();
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    LogUtil.enterBlock("NewCallLogFragment.onCreateView");

    View view = inflater.inflate(R.layout.new_call_log_fragment, container, false);
    recyclerView = view.findViewById(R.id.new_call_log_recycler_view);

    getLoaderManager().restartLoader(0, null, this);

    return view;
  }

  private void checkAnnotatedCallLogDirtyAndRefreshIfNecessary() {
    LogUtil.enterBlock("NewCallLogFragment.checkAnnotatedCallLogDirtyAndRefreshIfNecessary");
    ListenableScheduledFuture<Void> future = refreshAnnotatedCallLogWorker.refreshWithDirtyCheck();
    refreshAnnotatedCallLogListener.listen(future, unused -> {}, RuntimeException::new);
  }

  @Override
  public void invalidateUi() {
    LogUtil.enterBlock("NewCallLogFragment.invalidateUi");
    ListenableScheduledFuture<Void> future =
        refreshAnnotatedCallLogWorker.refreshWithoutDirtyCheck();
    refreshAnnotatedCallLogListener.listen(future, unused -> {}, RuntimeException::new);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    LogUtil.enterBlock("NewCallLogFragment.onCreateLoader");
    return new CoalescedAnnotatedCallLogCursorLoader(getContext());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor newCursor) {
    LogUtil.enterBlock("NewCallLogFragment.onLoadFinished");

    if (newCursor == null) {
      // This might be possible when the annotated call log hasn't been created but we're trying
      // to show the call log.
      LogUtil.w("NewCallLogFragment.onLoadFinished", "null cursor");
      return;
    }
    // TODO(zachh): Handle empty cursor by showing empty view.
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    recyclerView.setAdapter(new NewCallLogAdapter(newCursor, System::currentTimeMillis));
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    LogUtil.enterBlock("NewCallLogFragment.onLoaderReset");
    recyclerView.setAdapter(null);
  }
}

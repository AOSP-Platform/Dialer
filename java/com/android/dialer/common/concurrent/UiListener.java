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

package com.android.dialer.common.concurrent;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.FailureListener;
import com.android.dialer.common.concurrent.DialerExecutor.SuccessListener;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Executor;

/**
 * A headless fragment for use in UI components that interact with ListenableFutures.
 *
 * <p>Callbacks are only executed if the UI component is still alive.
 *
 * <p>Example usage: <code><pre>
 * public class MyActivity extends Activity {
 *
 *   private UiListener&lt;MyOutputType&gt uiListener;
 *
 *   public void onCreate(Bundle bundle) {
 *     super.onCreate(bundle);
 *
 *     // Must be called in onCreate!
 *     uiListener = DialerExecutorComponent.get(context).createUiListener(fragmentManager, taskId);
 *   }
 *
 *   private void onSuccess(MyOutputType output) { ... }
 *   private void onFailure(Throwable throwable) { ... }
 *
 *   private void userDidSomething() {
 *     ListenableFuture&lt;MyOutputType&gt; future = callSomeMethodReturningListenableFuture(input);
 *     uiListener.listen(future, this::onSuccess, this::onFailure);
 *   }
 * }
 * </pre></code>
 */
public class UiListener<OutputT> extends Fragment {

  private Executor uiThreadExecutor;
  private CallbackWrapper<OutputT> callbackWrapper;

  @MainThread
  static <OutputT> UiListener<OutputT> create(
      Executor uiThreadExecutor, FragmentManager fragmentManager, String taskId) {
    @SuppressWarnings("unchecked")
    UiListener<OutputT> uiListener =
        (UiListener<OutputT>) fragmentManager.findFragmentByTag(taskId);

    if (uiListener == null) {
      LogUtil.i("UiListener.create", "creating new UiListener for " + taskId);
      uiListener = new UiListener<>();
      uiListener.uiThreadExecutor = uiThreadExecutor;
      fragmentManager.beginTransaction().add(uiListener, taskId).commit();
    }
    return uiListener;
  }

  /**
   * Adds the specified listeners to the provided future.
   *
   * <p>The listeners are not called if the UI component this {@link UiListener} is declared in is
   * dead.
   */
  @MainThread
  public void listen(
      @NonNull ListenableFuture<OutputT> future,
      @NonNull SuccessListener<OutputT> successListener,
      @NonNull FailureListener failureListener) {
    callbackWrapper =
        new CallbackWrapper<>(Assert.isNotNull(successListener), Assert.isNotNull(failureListener));
    Futures.addCallback(Assert.isNotNull(future), callbackWrapper, uiThreadExecutor);
  }

  private static class CallbackWrapper<OutputT> implements FutureCallback<OutputT> {
    private SuccessListener<OutputT> successListener;
    private FailureListener failureListener;

    private CallbackWrapper(
        SuccessListener<OutputT> successListener, FailureListener failureListener) {
      this.successListener = successListener;
      this.failureListener = failureListener;
    }

    @Override
    public void onSuccess(@Nullable OutputT output) {
      if (successListener == null) {
        LogUtil.i("UiListener.runTask", "task succeeded but UI is dead");
      } else {
        successListener.onSuccess(output);
      }
    }

    @Override
    public void onFailure(Throwable throwable) {
      LogUtil.e("UiListener.runTask", "task failed", throwable);
      if (failureListener == null) {
        LogUtil.i("UiListener.runTask", "task failed but UI is dead");
      } else {
        failureListener.onFailure(throwable);
      }
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRetainInstance(true);
  }

  @Override
  public void onDetach() {
    super.onDetach();
    LogUtil.enterBlock("UiListener.onDetach");
    if (callbackWrapper != null) {
      callbackWrapper.successListener = null;
      callbackWrapper.failureListener = null;
    }
  }
}

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

package com.android.newbubble.testing.shadows;

import android.animation.Animator.AnimatorListener;
import android.animation.TimeInterpolator;
import android.view.View;
import android.view.ViewPropertyAnimator;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;

/**
 * Shadows implementation of ViewPropertyAnimator because it does not work properly in Robolectric
 * tests. This will set properties immediately, and run {@code startAction} and {@code endAction}
 * when {@link ViewPropertyAnimator#start()} is called.
 *
 * <p>Not all methods are currently implemented.
 */
@Implements(ViewPropertyAnimator.class)
public class ShadowViewPropertyAnimator {
  @RealObject private ViewPropertyAnimator realViewPropertyAnimator;
  private View view;
  private AnimatorListener listener;
  private Runnable startAction;
  private static Runnable endAction;

  public static ShadowViewPropertyAnimator shadowOf(ViewPropertyAnimator animator) {
    return (ShadowViewPropertyAnimator) Shadow.extract(animator);
  }

  public void __constructor__(View view) {
    this.view = view;
  }

  @Implementation
  public void start() {
    if (listener != null) {
      listener.onAnimationStart(null);
    }
    if (startAction != null) {
      startAction.run();
    }
    if (endAction != null) {
      endAction.run();
    }
  }

  @Implementation
  public void cancel() {
    if (listener != null) {
      listener.onAnimationCancel(null);
    }
  }

  @Implementation
  public ViewPropertyAnimator setListener(AnimatorListener listener) {
    this.listener = listener;
    return realViewPropertyAnimator;
  }

  @Implementation
  public ViewPropertyAnimator alpha(float value) {
    view.setAlpha(value);
    return realViewPropertyAnimator;
  }

  @Implementation
  public ViewPropertyAnimator translationX(float value) {
    view.setTranslationX(value);
    return realViewPropertyAnimator;
  }

  @Implementation
  public ViewPropertyAnimator translationY(float value) {
    view.setTranslationY(value);
    return realViewPropertyAnimator;
  }

  @Implementation
  public ViewPropertyAnimator scaleX(float value) {
    view.setScaleX(value);
    return realViewPropertyAnimator;
  }

  @Implementation
  public ViewPropertyAnimator scaleY(float value) {
    view.setScaleY(value);
    return realViewPropertyAnimator;
  }

  @Implementation
  public ViewPropertyAnimator withStartAction(Runnable startAction) {
    this.startAction = startAction;
    return realViewPropertyAnimator;
  }

  @Implementation
  public ViewPropertyAnimator withEndAction(Runnable endAction) {
    ShadowViewPropertyAnimator.endAction = endAction;
    return realViewPropertyAnimator;
  }

  public static Runnable getEndAction() {
    return endAction;
  }

  public static void resetEndAction() {
    endAction = null;
  }

  @Implementation
  public ViewPropertyAnimator setInterpolator(TimeInterpolator interpolator) {
    return realViewPropertyAnimator;
  }
}

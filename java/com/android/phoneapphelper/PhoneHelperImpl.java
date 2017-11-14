package com.android.phoneapphelper;

import android.app.Instrumentation;
import android.platform.test.helpers.AbstractStandardAppHelper;
import android.platform.test.helpers.IPhoneHelper;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

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

/** Implementations of a phone helper to cover testing on basic functionality of Phone app. */
public class PhoneHelperImpl extends AbstractStandardAppHelper implements IPhoneHelper {
  private static final String DIALER_PACKAGE = "com.android.dialer";
  private static final String DIALER_LAUNCHER = "Phone";
  private static final long WAIT_TIMEOUT_MS = 3000;
  private static final String[] dialNumbers =
      new String[] {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

  public PhoneHelperImpl(Instrumentation instr) {
    super(instr);
  }

  @Override
  public void hangUp() {
    safeInteraction(By.res(DIALER_PACKAGE, "incall_end_call"), uiObject -> uiObject.click());
    mDevice.wait(Until.gone(By.res(DIALER_PACKAGE, "incall_end_call")), WAIT_TIMEOUT_MS);
  }

  @Override
  public void dismissInitialDialogs() {}

  @Override
  public String getPackage() {
    return DIALER_PACKAGE;
  }

  @Override
  public String getLauncherName() {
    return DIALER_LAUNCHER;
  }

  /**
   * A method to dial numbers through dial pad. The input number should be a legit phone number e.g.
   * "," ";" etc. are not supported.
   */
  @Override
  public void dialNumber(String number) {
    safeInteraction(By.res(DIALER_PACKAGE, "floating_action_button"), uiObject -> uiObject.click());
    for (int i = 0; i < number.length(); i++) {
      String cId = getDialNumberId(number.charAt(i));
      if (!cId.equals("+")) {
        safeInteraction(By.res(DIALER_PACKAGE, cId), uiObject -> uiObject.click());
      } else {
        safeInteraction(By.res(DIALER_PACKAGE, "zero"), uiObject -> uiObject.longClick());
      }
    }
    safeInteraction(
        By.res(DIALER_PACKAGE, "dialpad_floating_action_button"), uiObject -> uiObject.click());
  }

  /**
   * Perform safe interaction on an UiObject2 object.
   *
   * @param bySelector the BySelector to search ui element on screen.
   * @param callback the callback function to perform UiObject function on the oject if found.
   * @return true if object was found.
   */
  private boolean safeInteraction(BySelector bySelector, Callback callback) {
    UiObject2 uiObject;
    if ((uiObject = safeGetUiObject(bySelector)) == null) {
      return false;
    }
    callback.interaction(uiObject);
    return true;
  }

  private UiObject2 safeGetUiObject(BySelector bySelector) {
    if (!hasObject(bySelector)) {
      return null;
    }
    return mDevice.findObject(bySelector);
  }

  private boolean hasObject(BySelector bySelector) {
    return mDevice.wait(Until.hasObject(bySelector), WAIT_TIMEOUT_MS).booleanValue();
  }

  private String getDialNumberId(char n) {
    if (n == '*') {
      return "star";
    }
    if (n == '#') {
      return "pound";
    }
    if (!Character.isDigit(n)) {
      return "+";
    }
    return dialNumbers[n - '0'];
  }

  private interface Callback {
    void interaction(UiObject2 uiObject);
  }
}

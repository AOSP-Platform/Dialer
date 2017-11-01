/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.voicemail.impl.scheduling;

import android.os.Bundle;

/**
 * A set of listeners managed by {@link BaseTask} for common behaviors such as retrying. Call {@link
 * BaseTask#addPolicy(Policy)} to add a policy.
 */
public interface Policy {

  void onCreate(BaseTask task, Bundle extras);

  void onBeforeExecute();

  void onCompleted();

  void onFail();

  void onDuplicatedTaskAdded();
}

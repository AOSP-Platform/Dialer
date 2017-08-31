/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.contacts.common.extensions;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import com.android.contacts.common.list.DirectoryPartition;
import java.util.List;

/** An interface for adding extended phone directories. */
public interface PhoneDirectoryExtender {

  /**
   * Return a list of extended directories to add. May return null if no directories are to be
   * added.
   */
  List<DirectoryPartition> getExtendedDirectories(Context context);

  /** returns true if the nearby places directory is enabled. */
  boolean isEnabled(Context context);

  /** Returns the content uri for nearby places. */
  @Nullable
  Uri getContentUri();
}

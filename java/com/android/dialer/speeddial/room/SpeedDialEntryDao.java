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

package com.android.dialer.speeddial.room;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;
import java.util.List;

/** Data access object for {@link SpeedDialEntry}. */
@Dao
public interface SpeedDialEntryDao {

  @Query("SELECT * FROM speeddialentry")
  List<SpeedDialEntry> getAllEntries();

  @Query("DELETE FROM speeddialentry")
  void nukeTable();

  @Insert
  void insert(SpeedDialEntry... entries);

  @Update
  void update(SpeedDialEntry... entries);

  @Delete
  void delete(SpeedDialEntry... entries);
}

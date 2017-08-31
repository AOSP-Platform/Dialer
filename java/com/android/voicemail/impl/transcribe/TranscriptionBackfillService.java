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
package com.android.voicemail.impl.transcribe;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.WorkerThread;
import android.support.v4.app.JobIntentService;
import android.support.v4.os.BuildCompat;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.constants.ScheduledJobIds;
import java.util.List;

/**
 * JobScheduler service for transcribing old voicemails. This service does a database scan for
 * un-transcribed voicemails and schedules transcription tasks for them, once we have an un-metered
 * network connection.
 */
public class TranscriptionBackfillService extends JobIntentService {

  /** Schedule a task to scan the database for untranscribed voicemails */
  public static boolean scheduleTask(Context context) {
    if (BuildCompat.isAtLeastO()) {
      LogUtil.enterBlock("TranscriptionBackfillService.transcribeOldVoicemails");
      ComponentName componentName = new ComponentName(context, TranscriptionBackfillService.class);
      JobInfo.Builder builder =
          new JobInfo.Builder(ScheduledJobIds.VVM_TRANSCRIPTION_BACKFILL_JOB, componentName)
              .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
      JobScheduler scheduler = context.getSystemService(JobScheduler.class);
      return scheduler.enqueue(builder.build(), makeWorkItem()) == JobScheduler.RESULT_SUCCESS;
    } else {
      LogUtil.i("TranscriptionBackfillService.transcribeOldVoicemails", "not supported");
      return false;
    }
  }

  private static JobWorkItem makeWorkItem() {
    Intent intent = new Intent();
    return new JobWorkItem(intent);
  }

  @Override
  @WorkerThread
  protected void onHandleWork(Intent intent) {
    LogUtil.enterBlock("TranscriptionBackfillService.onHandleWork");

    TranscriptionDbHelper dbHelper = new TranscriptionDbHelper(this);
    List<Uri> untranscribed = dbHelper.getUntranscribedVoicemails();
    LogUtil.i(
        "TranscriptionBackfillService.onHandleWork",
        "found " + untranscribed.size() + " untranscribed voicemails");
    // TODO(mdooley): Consider doing the actual transcriptions here instead of scheduling jobs.
    for (Uri uri : untranscribed) {
      ThreadUtil.postOnUiThread(
          () -> {
            TranscriptionService.scheduleNewVoicemailTranscriptionJob(this, uri, false);
          });
    }
  }

  @Override
  public void onDestroy() {
    LogUtil.enterBlock("TranscriptionBackfillService.onDestroy");
    super.onDestroy();
  }
}

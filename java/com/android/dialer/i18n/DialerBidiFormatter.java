/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.i18n;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.text.BidiFormatter;
import android.text.TextUtils;
import android.util.Patterns;
import com.android.dialer.common.Assert;
import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An enhanced version of {@link BidiFormatter} that can recognize a formatted phone number
 * containing whitespaces.
 *
 * <p>Formatted phone numbers usually contain one or more whitespaces (e.g., "+1 650-253-0000",
 * "(650) 253-0000", etc). {@link BidiFormatter} mistakes such a number for tokens separated by
 * whitespaces. Therefore, these numbers can't be correctly shown in a RTL context (e.g., "+1
 * 650-253-0000" would be shown as "650-253-0000 1+".)
 */
public final class DialerBidiFormatter {

  private DialerBidiFormatter() {}

  // Regular expresssion that matches a single space in the beginning or end of a string.
  private static final String REGEXP_SURROUNDING_SPACE = "^[ ]|[ ]$";

  /**
   * Divides the given text into segments, applies {@link BidiFormatter#unicodeWrap(CharSequence)}
   * to each segment, and then reassembles the text.
   *
   * <p>A segment of the text is either a substring matching {@link Patterns#PHONE} or one that does
   * not.
   *
   * @see BidiFormatter#unicodeWrap(CharSequence)
   */
  public static CharSequence unicodeWrap(@Nullable CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return text;
    }

    List<CharSequence> segments = segmentText(text);

    StringBuilder formattedText = new StringBuilder();
    for (CharSequence segment : segments) {
      formattedText.append(BidiFormatter.getInstance().unicodeWrap(segment));
    }

    return formattedText.toString();
  }

  /**
   * Segments the given text using {@link Patterns#PHONE}. Single spaces before and after phone
   * number will be placed within its own segment to prevent misplaced space due to RLT-layout.
   *
   * <p>For example, "Mobile, +1 650-253-0000, 20 seconds" will be segmented into {"Mobile,", " ",
   * "+1 650-253-0000", ", 20 seconds"}.
   */
  @VisibleForTesting
  static List<CharSequence> segmentText(CharSequence text) {
    Assert.checkArgument(!TextUtils.isEmpty(text));

    // Segment the text to extract any phone numbers into its own segment
    List<CharSequence> segments = segmentText(text, Patterns.PHONE);

    // Segment the previous segments to extract starting and ending spaces into its own segment
    // to make sure spaces are placed correctly within the final string.
    List<CharSequence> resultingSegments = new ArrayList<>();
    Pattern patternSurroundingSpace = Pattern.compile(REGEXP_SURROUNDING_SPACE);
    for (CharSequence segment : segments) {
      List<CharSequence> allSegments = segmentText(segment, patternSurroundingSpace);
      resultingSegments.addAll(allSegments);
    }

    return resultingSegments;
  }

  private static List<CharSequence> segmentText(CharSequence text, Pattern pattern) {
    Assert.checkArgument(!TextUtils.isEmpty(text));

    List<CharSequence> segments = new ArrayList<>();

    // Find the start index and the end index of each segment matching the pattern.
    Matcher matcher = pattern.matcher(text.toString());
    List<Range> segmentRanges = new ArrayList<>();
    while (matcher.find()) {
      segmentRanges.add(Range.newBuilder().setStart(matcher.start()).setEnd(matcher.end()).build());
    }

    // Segment the text.
    int currIndex = 0;
    for (Range segmentRange : segmentRanges) {
      if (currIndex < segmentRange.getStart()) {
        segments.add(text.subSequence(currIndex, segmentRange.getStart()));
      }

      segments.add(text.subSequence(segmentRange.getStart(), segmentRange.getEnd()));
      currIndex = segmentRange.getEnd();
    }
    if (currIndex < text.length()) {
      segments.add(text.subSequence(currIndex, text.length()));
    }

    return segments;
  }

  /** Represents the start index (inclusive) and the end index (exclusive) of a text segment. */
  @AutoValue
  abstract static class Range {
    static Builder newBuilder() {
      return new AutoValue_DialerBidiFormatter_Range.Builder();
    }

    abstract int getStart();

    abstract int getEnd();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setStart(int start);

      abstract Builder setEnd(int end);

      abstract Range build();
    }
  }
}

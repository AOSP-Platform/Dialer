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

package com.android.dialer.searchfragment.common;

import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import java.util.regex.Pattern;

/** Utility class for filtering, comparing and handling strings and queries. */
public class QueryFilteringUtil {

  /** Matches strings with "-", "(", ")", 2-9 of at least length one. */
  private static final Pattern T9_PATTERN = Pattern.compile("[\\-()2-9]+");

  /**
   * Returns true if the query is of T9 format and the name's T9 representation belongs to the query
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>#nameMatchesT9Query("7", "John Smith") returns true, 7 -> 'S'
   *   <li>#nameMatchesT9Query("55", "Jessica Jones") returns true, 55 -> 'JJ'
   *   <li>#nameMatchesT9Query("56", "Jessica Jones") returns true, 56 -> 'Jo'
   *   <li>#nameMatchesT9Query("7", "Jessica Jones") returns false, no names start with P,Q,R or S
   * </ul>
   */
  public static boolean nameMatchesT9Query(String query, String name) {
    if (!T9_PATTERN.matcher(query).matches()) {
      return false;
    }

    query = digitsOnly(query);
    Pattern pattern = Pattern.compile("(^|\\s)" + Pattern.quote(query));
    if (pattern.matcher(getT9Representation(name)).find()) {
      // query matches the start of a T9 name (i.e. 75 -> "Jessica [Jo]nes")
      return true;
    }

    // Check matches initials
    // TODO(calderwoodra) investigate faster implementation
    int queryIndex = 0;

    String[] names = name.toLowerCase().split("\\s");
    for (int i = 0; i < names.length && queryIndex < query.length(); i++) {
      if (TextUtils.isEmpty(names[i])) {
        continue;
      }

      if (getDigit(names[i].charAt(0)) == query.charAt(queryIndex)) {
        queryIndex++;
      }
    }

    return queryIndex == query.length();
  }

  /**
   * Returns true if the subparts of the name (split by white space) begin with the query.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>#nameContainsQuery("b", "Brandon") returns true
   *   <li>#nameContainsQuery("o", "Bob") returns false
   *   <li>#nameContainsQuery("o", "Bob Olive") returns true
   * </ul>
   */
  public static boolean nameContainsQuery(String query, String name) {
    if (TextUtils.isEmpty(name)) {
      return false;
    }

    return Pattern.compile("(^|\\s)" + Pattern.quote(query.toLowerCase()))
        .matcher(name.toLowerCase())
        .find();
  }

  /** @return true if the number belongs to the query. */
  public static boolean numberMatchesNumberQuery(String query, String number) {
    return PhoneNumberUtils.isGlobalPhoneNumber(query)
        && indexOfQueryNonDigitsIgnored(query, number) != -1;
  }

  /**
   * Checks if query is contained in number while ignoring all characters in both that are not
   * digits (i.e. {@link Character#isDigit(char)} returns false).
   *
   * @return index where query is found with all non-digits removed, -1 if it's not found.
   */
  static int indexOfQueryNonDigitsIgnored(@NonNull String query, @NonNull String number) {
    return digitsOnly(number).indexOf(digitsOnly(query));
  }

  // Returns string with letters replaced with their T9 representation.
  static String getT9Representation(String s) {
    StringBuilder builder = new StringBuilder(s.length());
    for (char c : s.toLowerCase().toCharArray()) {
      builder.append(getDigit(c));
    }
    return builder.toString();
  }

  /** @return String s with only digits recognized by Character#isDigit() remaining */
  public static String digitsOnly(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isDigit(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  // Returns the T9 representation of a lower case character, otherwise returns the character.
  static char getDigit(char c) {
    switch (c) {
      case 'a':
      case 'b':
      case 'c':
        return '2';
      case 'd':
      case 'e':
      case 'f':
        return '3';
      case 'g':
      case 'h':
      case 'i':
        return '4';
      case 'j':
      case 'k':
      case 'l':
        return '5';
      case 'm':
      case 'n':
      case 'o':
        return '6';
      case 'p':
      case 'q':
      case 'r':
      case 's':
        return '7';
      case 't':
      case 'u':
      case 'v':
        return '8';
      case 'w':
      case 'x':
      case 'y':
      case 'z':
        return '9';
      default:
        return c;
    }
  }
}

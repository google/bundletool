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

package com.android.tools.build.bundletool.device.activitymanager;

import com.android.tools.build.bundletool.device.activitymanager.ResourceConfigParser.ConfigQualifierParser;
import com.android.tools.build.bundletool.device.activitymanager.ResourceConfigParser.ResourceConfigHandler;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A common parsing logic for resource qualifier parsers expecting a specific regex pattern. */
abstract class PatternConfigQualifierParser<T> implements ConfigQualifierParser<T> {

  /** Provider of the expected pattern. */
  abstract Pattern getExpectedPattern();

  /**
   * Provides a function called when the current qualifier matches the expected pattern.
   *
   * <p>Typically this issues some calls to the {@link ResourceConfigHandler}. The function should
   * return if the identified string matched the expectations.
   */
  abstract Function<Matcher, Boolean> getValueProcessor(ResourceConfigHandler<T> handler);

  @Override
  public boolean parse(ConfigStringIterator iterator, ResourceConfigHandler<T> handler) {
    String nextPart = iterator.getValue();
    if (nextPart.equals("any")) {
      return true;
    }

    Matcher matcher = getExpectedPattern().matcher(nextPart);
    if (!matcher.matches()) {
      return false;
    }

    return getValueProcessor(handler).apply(matcher);
  }

  static class MccParser<T> extends PatternConfigQualifierParser<T> {

    @Override
    Pattern getExpectedPattern() {
      return Pattern.compile("mcc(?<code>\\d\\d\\d?)");
    }

    @Override
    Function<Matcher, Boolean> getValueProcessor(ResourceConfigHandler<T> handler) {
      return matcher -> {
        String mccCode = matcher.group("code");

        if (mccCode == null) {
          return false;
        }

        handler.onMccCode(Integer.parseInt(mccCode));
        return true;
      };
    }
  }

  static class MncParser<T> extends PatternConfigQualifierParser<T> {

    @Override
    Pattern getExpectedPattern() {
      return Pattern.compile("mnc(?<code>\\d{1,3}?)");
    }

    @Override
    Function<Matcher, Boolean> getValueProcessor(ResourceConfigHandler<T> handler) {
      return matcher -> {
        String mncCode = matcher.group("code");

        if (mncCode == null) {
          return false;
        }

        handler.onMncCode(Integer.parseInt(mncCode));
        return true;
      };
    }
  }
}

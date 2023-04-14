/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.build.bundletool.model.targeting;

import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.auto.value.AutoValue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents dimension value used in targeted directory path. For a dimension targeting like
 * 'tcf_astc', DimensionKeyValue parses 'tcf' as dimension key and 'astc' as dimension value.
 */
@AutoValue
public abstract class DimensionKeyValue {
  private static final Pattern DIMENSION_KEY_VALUE_PATTERN =
      Pattern.compile("(?<key>[a-z]+)_(?<value>.+)");

  public abstract String getDimensionKey();

  public abstract String getDimensionValue();

  public static DimensionKeyValue parse(String dimensionValue) {
    Matcher matcher = DIMENSION_KEY_VALUE_PATTERN.matcher(dimensionValue);
    if (matcher.matches()) {
      String key = matcher.group("key");
      return new AutoValue_DimensionKeyValue(key, matcher.group("value"));
    } else {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Cannot tokenize targeted directory segment '%s'."
                  + " Expecting targeting dimension value in the '<key>_<value>' format.",
              dimensionValue)
          .build();
    }
  }
}

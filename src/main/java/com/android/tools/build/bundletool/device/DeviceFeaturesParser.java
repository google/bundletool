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

package com.android.tools.build.bundletool.device;

import com.android.tools.build.bundletool.model.exceptions.AdbOutputParseException;
import com.google.common.collect.ImmutableList;

/** Parses the output of the "pm list features" ADB shell command. */
public class DeviceFeaturesParser {

  private static final String EXPECTED_LINE_PREFIX = "feature:";
  private static final String WARNING_LINE_PREFIX = "WARNING:";

  /** Parses the "pm list features" command output. */
  public ImmutableList<String> parse(ImmutableList<String> packageManagerListOutput) {
    ImmutableList.Builder<String> features = ImmutableList.builder();

    for (String line : packageManagerListOutput) {
      if (line.isEmpty()) {
        continue;
      }
      if (line.startsWith(WARNING_LINE_PREFIX)) {
        continue;
      }
      if (!line.startsWith(EXPECTED_LINE_PREFIX)) {
        throw AdbOutputParseException.builder()
            .withInternalMessage("Unexpected output of 'pm list features' command: '%s'", line)
            .build();
      }
      // Skip "feature:" in the output.
      features.add(line.substring(EXPECTED_LINE_PREFIX.length()));
    }
    return features.build();
  }
}

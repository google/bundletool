/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.google.common.primitives.Ints;
import java.util.Optional;

/** Parses the output of the "pm install-create" ADB shell command. */
class SessionIdParser {

  /** Parses sessionId from "pm install-create" command output */
  int parse(ImmutableList<String> installCreateOutput) {
    Optional<String> successLine =
        installCreateOutput.stream().filter(s -> s.startsWith("Success")).findFirst();
    if (!successLine.isPresent()) {
      throw AdbOutputParseException.builder()
          .withInternalMessage(
              "adb: failed to parse session id from output\nDetails:%s", installCreateOutput)
          .build();
    }
    return parseSessionIdFromOutput(successLine.get());
  }

  private static int parseSessionIdFromOutput(String output) {
    int startIndex = output.indexOf("[");
    int endIndex = output.indexOf("]", startIndex);
    if (startIndex == -1 || endIndex == -1) {
      throw AdbOutputParseException.builder()
          .withInternalMessage("adb: failed to parse session id from output\nDetails:%s", output)
          .build();
    }
    Integer sessionId = Ints.tryParse(output.substring(startIndex + 1, endIndex));
    if (sessionId == null) {
      throw AdbOutputParseException.builder()
          .withInternalMessage("adb: session id should be integer\nDetails:%s", output)
          .build();
    }
    return sessionId;
  }
}

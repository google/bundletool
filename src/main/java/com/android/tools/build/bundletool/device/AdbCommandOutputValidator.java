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

import com.android.tools.build.bundletool.model.exceptions.ParseException;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/** Validators for adb shell command outputs */
class AdbCommandOutputValidator {

  /** Validates that ADB shell command finished successfully */
  public static void validateSuccess(ImmutableList<String> output, String command) {
    Optional<String> successLine = output.stream().filter(s -> s.startsWith("Success")).findFirst();
    if (!successLine.isPresent()) {
      throw new ParseException(String.format("adb failed: %s\nDetails:%s", command, output));
    }
  }

  private AdbCommandOutputValidator() {}
}

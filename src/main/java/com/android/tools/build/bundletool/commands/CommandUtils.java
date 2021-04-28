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

package com.android.tools.build.bundletool.commands;

import com.android.tools.build.bundletool.androidtools.Aapt2Command;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.SdkToolsLocator;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import java.nio.file.Path;
import java.util.Optional;

final class CommandUtils {
  static final String ANDROID_SERIAL_VARIABLE = "ANDROID_SERIAL";

  private CommandUtils() {}

  static Path getAdbPath(
      ParsedFlags flags, Flag<Path> adbFlag, SystemEnvironmentProvider systemEnvironmentProvider) {
    return adbFlag
        .getValue(flags)
        .orElseGet(
            () ->
                new SdkToolsLocator()
                    .locateAdb(systemEnvironmentProvider)
                    .orElseThrow(
                        () ->
                            CommandExecutionException.builder()
                                .withInternalMessage(
                                    "Unable to determine the location of ADB. Please set the --adb "
                                        + "flag or define ANDROID_HOME or PATH environment "
                                        + "variable.")
                                .build()));
  }

  static Optional<String> getDeviceSerialName(
      ParsedFlags flags,
      Flag<String> deviceIdFlag,
      SystemEnvironmentProvider systemEnvironmentProvider) {
    Optional<String> deviceSerialName = deviceIdFlag.getValue(flags);
    if (!deviceSerialName.isPresent()) {
      deviceSerialName = systemEnvironmentProvider.getVariable(ANDROID_SERIAL_VARIABLE);
    }
    return deviceSerialName;
  }

  static Aapt2Command extractAapt2FromJar(Path tempDir) {
    return new SdkToolsLocator()
        .extractAapt2(tempDir)
        .map(Aapt2Command::createFromExecutablePath)
        .orElseThrow(
            () ->
                CommandExecutionException.builder()
                    .withInternalMessage(
                        "Could not extract aapt2: consider updating bundletool to a more recent "
                            + "version or providing the path to aapt2 using the flag --aapt2.")
                    .build());
  }
}

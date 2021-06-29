/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.build.bundletool.transparency;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.commands.CheckTransparencyCommand;
import com.android.tools.build.bundletool.device.AdbRunner;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.AdbShellCommandTask;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.device.Device.FilePullParams;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/** Executes {@link CheckTransparencyCommand} in CONNECTED_DEVICE mode. */
public final class ConnectedDeviceModeTransparencyChecker {

  private static final String APK_PATH_ON_DEVICE_PREFIX = "package:/";

  public static TransparencyCheckResult checkTransparency(CheckTransparencyCommand command) {
    command.getAdbServer().get().init(command.getAdbPath().get());
    AdbRunner adbRunner = new AdbRunner(command.getAdbServer().get());
    Device adbDevice = getDevice(command.getAdbServer().get(), command.getDeviceId());

    // Execute a shell command to retrieve paths to all APKs for the given package name.
    AdbShellCommandTask adbShellCommandTask =
        new AdbShellCommandTask(adbDevice, "pm path " + command.getPackageName().get());
    ImmutableList<String> pathsToApksOnDevice =
        adbShellCommandTask.execute().stream()
            .filter(path -> path.startsWith(APK_PATH_ON_DEVICE_PREFIX))
            .map(path -> path.substring(APK_PATH_ON_DEVICE_PREFIX.length()))
            .collect(toImmutableList());
    if (pathsToApksOnDevice.isEmpty()) {
      throw InvalidCommandException.builder()
          .withInternalMessage("No files found for package " + command.getPackageName().get())
          .build();
    }

    // Pull APKs to a temporary directory and verify code transparency.
    try (TempDirectory tempDir = new TempDirectory("connected-device-transparency-check")) {
      Path apksExtractedSubDirectory = tempDir.getPath().resolve("extracted");
      Files.createDirectory(apksExtractedSubDirectory);
      ImmutableList<FilePullParams> pullParams =
          createPullParams(pathsToApksOnDevice, apksExtractedSubDirectory);

      if (command.getDeviceId().isPresent()) {
        adbRunner.run(device -> device.pull(pullParams), command.getDeviceId().get());
      } else {
        adbRunner.run(device -> device.pull(pullParams));
      }

      return ApkTransparencyCheckUtils.checkTransparency(
          pullParams.stream().map(FilePullParams::getDestinationPath).collect(toImmutableList()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Device getDevice(AdbServer adbServer, Optional<String> deviceId) {
    DeviceAnalyzer deviceAnalyzer = new DeviceAnalyzer(adbServer);
    Device device;
    try {
      device = deviceAnalyzer.getAndValidateDevice(deviceId);
    } catch (TimeoutException e) {
      throw new UncheckedTimeoutException(e);
    }
    return device;
  }

  private static ImmutableList<FilePullParams> createPullParams(
      ImmutableList<String> pathsToApksOnDevice, Path apksExtractedSubDirectory) {
    return pathsToApksOnDevice.stream()
        .map(
            pathOnDevice ->
                FilePullParams.builder()
                    .setPathOnDevice(pathOnDevice)
                    .setDestinationPath(
                        apksExtractedSubDirectory.resolve(Paths.get(pathOnDevice).getFileName()))
                    .build())
        .collect(toImmutableList());
  }

  private ConnectedDeviceModeTransparencyChecker() {}
}

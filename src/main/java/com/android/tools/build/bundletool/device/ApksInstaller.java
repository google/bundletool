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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.device.Device.InstallOptions;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.DeviceNotFoundException;
import com.android.tools.build.bundletool.model.exceptions.DeviceNotFoundException.TooManyDevicesMatchedException;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/** Responsible for installing the APKs. */
public class ApksInstaller {

  private final AdbServer adbServer;

  /** Initializes the instance. Expects the {@link AdbServer} to be initialized. */
  public ApksInstaller(AdbServer adbServer) {
    this.adbServer = adbServer;
  }

  /** Attempts to install the given APKs to the only connected device. */
  public void installApks(ImmutableList<Path> apkPaths, InstallOptions installOptions) {
    try {
      installApks(apkPaths, installOptions, Predicates.alwaysTrue());
    } catch (TooManyDevicesMatchedException e) {
      throw CommandExecutionException.builder()
          .withMessage("Expected to find one connected device, but found %d.", e.getMatchedNumber())
          .withCause(e)
          .build();
    } catch (DeviceNotFoundException e) {
      throw CommandExecutionException.builder()
          .withMessage("Expected to find one connected device, but found none.")
          .withCause(e)
          .build();
    }
  }

  /** Attempts to install the given APKs to a device with a given serial number. */
  public void installApks(
      ImmutableList<Path> apkPaths, InstallOptions installOptions, String deviceId) {
    try {
      installApks(apkPaths, installOptions, device -> device.getSerialNumber().equals(deviceId));
    } catch (DeviceNotFoundException e) {
      throw CommandExecutionException.builder()
          .withMessage("Expected to find one connected device with serial number '%s'.", deviceId)
          .withCause(e)
          .build();
    }
  }

  private void installApks(
      ImmutableList<Path> apkPaths, InstallOptions installOptions, Predicate<Device> deviceFilter) {
    try {
      ImmutableList<Device> matchedDevices =
          adbServer.getDevices().stream().filter(deviceFilter).collect(toImmutableList());
      if (matchedDevices.isEmpty()) {
        throw new DeviceNotFoundException("Unable to find a device matching the criteria.");
      } else if (matchedDevices.size() > 1) {
        throw new TooManyDevicesMatchedException(matchedDevices.size());
      }

      installOnDevice(apkPaths, installOptions, matchedDevices.get(0));

    } catch (TimeoutException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage("Timed out while waiting for ADB.")
          .build();
    }
  }

  private void installOnDevice(
      ImmutableList<Path> apkPaths, InstallOptions installOptions, Device device) {
    device.installApks(apkPaths, installOptions);
  }
}

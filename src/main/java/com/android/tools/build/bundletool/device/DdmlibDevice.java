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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.build.bundletool.model.exceptions.InstallationException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Ddmlib-backed implementation of the {@link Device}. */
public class DdmlibDevice extends Device {

  private final IDevice device;
  private static final int ADB_TIMEOUT_MS = 60000;
  private static final String DEVICE_FEATURES_COMMAND = "pm list features";

  private final DeviceFeaturesParser deviceFeaturesParser = new DeviceFeaturesParser();

  public DdmlibDevice(IDevice device) {
    this.device = device;
  }

  @Override
  public DeviceState getState() {
    return device.getState();
  }

  @Override
  public AndroidVersion getVersion() {
    return device.getVersion();
  }

  @Override
  public ImmutableList<String> getAbis() {
    return ImmutableList.copyOf(device.getAbis());
  }

  @Override
  public int getDensity() {
    return device.getDensity();
  }

  @Override
  public String getSerialNumber() {
    return device.getSerialNumber();
  }

  @Override
  public Optional<String> getProperty(String propertyName) {
    return Optional.ofNullable(device.getProperty(propertyName));
  }

  @Override
  public ImmutableList<String> getDeviceFeatures() {
    return deviceFeaturesParser.parse(
        new AdbShellCommandTask(this, DEVICE_FEATURES_COMMAND)
            .execute(ADB_TIMEOUT_MS, TimeUnit.MILLISECONDS));
  }

  @Override
  public void executeShellCommand(
      String command,
      IShellOutputReceiver receiver,
      long maxTimeToOutputResponse,
      TimeUnit maxTimeUnits)
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
          IOException {
    device.executeShellCommand(command, receiver, maxTimeToOutputResponse, maxTimeUnits);
  }

  @Override
  public void installApks(ImmutableList<Path> apks, InstallOptions installOptions) {
    ImmutableList<File> apkFiles = apks.stream().map(Path::toFile).collect(toImmutableList());

    try {
      if (getVersion()
          .isGreaterOrEqualThan(AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel())) {
        device.installPackages(
            apkFiles,
            installOptions.getAllowReinstall(),
            installOptions.getAllowDowngrade() ? ImmutableList.of("-d") : ImmutableList.of(),
            installOptions.getTimeout().toMillis(),
            TimeUnit.MILLISECONDS);
      } else {
        device.installPackage(
            Iterables.getOnlyElement(apkFiles).toString(),
            installOptions.getAllowReinstall(),
            installOptions.getAllowDowngrade() ? "-d" : null);
      }
    } catch (InstallException e) {
      throw InstallationException.builder()
          .withCause(e)
          .withMessage("Installation of the app failed.")
          .build();
    }
  }
}

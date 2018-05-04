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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Interface for BundleTool - Ddmlib interactions. Represents a connected device. */
public interface Device {

  DeviceState getState();

  AndroidVersion getVersion();

  ImmutableList<String> getAbis();

  /** Returns device density or -1 if error occurred. */
  int getDensity();

  String getSerialNumber();

  Optional<String> getProperty(String propertyName);

  void executeShellCommand(
      String command,
      IShellOutputReceiver receiver,
      long maxTimeToOutputResponse,
      TimeUnit maxTimeUnits)
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
          IOException;

  void installApks(ImmutableList<Path> apks, boolean reinstall, long timeout, TimeUnit timeoutUnit);
}

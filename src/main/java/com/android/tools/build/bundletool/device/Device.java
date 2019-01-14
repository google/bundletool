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
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Interface for BundleTool - Ddmlib interactions. Represents a connected device. */
public abstract class Device {

  private static final Duration DEFAULT_ADB_TIMEOUT = Duration.ofMinutes(1);

  public abstract DeviceState getState();

  public abstract AndroidVersion getVersion();

  public abstract ImmutableList<String> getAbis();

  /** Returns device density or -1 if error occurred. */
  public abstract int getDensity();

  public abstract String getSerialNumber();

  public abstract Optional<String> getProperty(String propertyName);

  public abstract ImmutableList<String> getDeviceFeatures();

  public abstract void executeShellCommand(
      String command,
      IShellOutputReceiver receiver,
      long maxTimeToOutputResponse,
      TimeUnit maxTimeUnits)
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
          IOException;

  public abstract void installApks(ImmutableList<Path> apks, InstallOptions installOptions);

  /** Options related to APK installation. */
  @Immutable
  @AutoValue
  @AutoValue.CopyAnnotations
  public abstract static class InstallOptions {

    public abstract boolean getAllowDowngrade();

    public abstract boolean getAllowReinstall();

    public abstract Duration getTimeout();

    public static Builder builder() {
      return new AutoValue_Device_InstallOptions.Builder()
          .setTimeout(DEFAULT_ADB_TIMEOUT)
          .setAllowReinstall(true);
    }

    /** Builder for {@link InstallOptions}. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setAllowDowngrade(boolean allowDowngrade);

      public abstract Builder setAllowReinstall(boolean allowReinstall);

      public abstract Builder setTimeout(Duration timeout);

      public abstract InstallOptions build();
    }
  }
}

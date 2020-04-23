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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.InstallationException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.FormatMethod;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Ddmlib-backed implementation of the {@link Device}. */
public class DdmlibDevice extends Device {

  private final IDevice device;
  private static final int ADB_TIMEOUT_MS = 60000;
  private static final String DEVICE_FEATURES_COMMAND = "pm list features";
  private static final String GL_EXTENSIONS_COMMAND = "dumpsys SurfaceFlinger";

  private final DeviceFeaturesParser deviceFeaturesParser = new DeviceFeaturesParser();
  private final GlExtensionsParser glExtensionsParser = new GlExtensionsParser();

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
  public ImmutableList<String> getGlExtensions() {
    return glExtensionsParser.parse(
        new AdbShellCommandTask(this, GL_EXTENSIONS_COMMAND)
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
    ImmutableList.Builder<String> extraArgs = ImmutableList.builder();
    if (installOptions.getAllowDowngrade()) {
      extraArgs.add("-d");
    }
    if (installOptions.getAllowTestOnly()) {
      extraArgs.add("-t");
    }

    try {
      if (getVersion()
          .isGreaterOrEqualThan(AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel())) {
        device.installPackages(
            apkFiles,
            installOptions.getAllowReinstall(),
            extraArgs.build(),
            installOptions.getTimeout().toMillis(),
            TimeUnit.MILLISECONDS);
      } else {
        device.installPackage(
            Iterables.getOnlyElement(apkFiles).toString(),
            installOptions.getAllowReinstall(),
            extraArgs.build().toArray(new String[0]));
      }
    } catch (InstallException e) {
      throw InstallationException.builder()
          .withCause(e)
          .withMessage("Installation of the app failed.")
          .build();
    }
  }

  @Override
  public void pushApks(ImmutableList<Path> apks, PushOptions pushOptions) {
    String splitsPath = pushOptions.getDestinationPath();
    checkArgument(!splitsPath.isEmpty(), "Splits path cannot be empty.");

    RemoteCommandExecutor commandExecutor =
        new RemoteCommandExecutor(this, pushOptions.getTimeout().toMillis(), System.err);

    try {
      // There are two different flows, depending on if the path is absolute or not...
      if (!splitsPath.startsWith("/")) {
        // Path is relative, so we're going to try to push it to the app's external dir
        String packageName =
            pushOptions
                .getPackageName()
                .orElseThrow(
                    () ->
                        new CommandExecutionException(
                            "PushOptions.packageName must be set for relative paths."));

        splitsPath = joinUnixPaths("/sdcard/Android/data/", packageName, "files", splitsPath);
      }
      // Now the path is absolute. We assume it's pointing to a location writeable by ADB shell.
      // It shouldn't point to app's private directory.

      // Some clean up first. Remove the destination dir if flag is set...
      if (pushOptions.getClearDestinationPath()) {
        commandExecutor.executeAndPrint("rm -rf %s", splitsPath);
      }

      // ... and recreate it, making sure the destination dir is empty.
      // We don't want splits from previous runs in the directory.
      // There isn't a nice way to test if dir is empty in shell, but rmdir will return error
      commandExecutor.executeAndPrint(
          "mkdir -p %s && rmdir %s && mkdir -p %s", splitsPath, splitsPath, splitsPath);

      // Try to push files normally. Will fail if ADB shell doesn't have permission to write.
      for (Path path : apks) {
        device.pushFile(
            path.toFile().getAbsolutePath(),
            joinUnixPaths(splitsPath, path.getFileName().toString()));
        System.err.printf(
            "Pushed \"%s\"%n", joinUnixPaths(splitsPath, path.getFileName().toString()));
      }
    } catch (IOException
        | TimeoutException
        | SyncException
        | AdbCommandRejectedException
        | ShellCommandUnresponsiveException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withMessage(
              "Pushing additional splits for local testing failed. Your app might still have been"
                  + " installed correctly, but you won't be able to test dynamic modules.")
          .build();
    }
  }

  @Override
  public Path syncPackageToDevice(Path localFilePath)
      throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
    return Paths.get(device.syncPackageToDevice(localFilePath.toFile().getAbsolutePath()));
  }

  @Override
  public void removeRemotePackage(Path remoteFilePath) throws InstallException {
    device.removeRemotePackage(remoteFilePath.toString());
  }

  static class RemoteCommandExecutor {
    private final Device device;
    private final MultiLineReceiver receiver;
    private final long timeout;
    private final PrintStream out;
    private String lastOutputLine;

    RemoteCommandExecutor(Device device, long timeout, PrintStream out) {
      this.device = device;
      this.timeout = timeout;
      this.out = out;
      this.receiver =
          new MultiLineReceiver() {
            @Override
            public void processNewLines(String[] lines) {
              for (String line : lines) {
                if (!line.isEmpty()) {
                  out.println("ADB >> " + line);
                  lastOutputLine = line;
                }
              }
            }

            @Override
            public boolean isCancelled() {
              return false;
            }
          };
    }

    @FormatMethod
    private void executeAndPrint(String commandFormat, String... args)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
      String command = formatCommandWithArgs(commandFormat, args);
      lastOutputLine = null;
      out.println("ADB << " + command);
      // ExecuteShellCommand would only tell us about ADB errors, and NOT the actual shell commands
      // We need another way to check exit values of the commands we run.
      // By adding " && echo OK" we can make sure "OK" is printed if the cmd executed successfully.
      device.executeShellCommand(command + " && echo OK", receiver, timeout, TimeUnit.MILLISECONDS);
      if (!"OK".equals(lastOutputLine)) {
        throw new IOException("ADB command failed.");
      }
    }

    /** Returns the string in single quotes, with any single quotes in the string escaped. */
    static String escapeAndSingleQuote(String string) {
      return "'" + string.replace("'", "'\\''") + "'";
    }

    /**
     * Formats the command string, replacing %s argument placeholders with escaped and single quoted
     * args. This was made to work with Strings only, so format your args beforehand.
     */
    @FormatMethod
    static String formatCommandWithArgs(String command, String... args) {
      return String.format(
          command, Arrays.stream(args).map(RemoteCommandExecutor::escapeAndSingleQuote).toArray());
    }
  }

  // We can't rely on Path and friends if running on Windows, emulator always needs UNIX paths
  static String joinUnixPaths(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') {
        sb.append('/');
      }
      sb.append(part);
    }
    return sb.toString();
  }
}

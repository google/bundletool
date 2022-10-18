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

import static com.android.tools.build.bundletool.device.LocalTestingPathResolver.resolveLocalTestingPath;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.DdmPreferences;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.FormatMethod;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Ddmlib-backed implementation of the {@link Device}. */
public class DdmlibDevice extends Device {
  private static final String DENSITY_OUTPUT_PREFIX = "Physical density:";

  private final IDevice device;
  private final Clock clock;
  private static final int ADB_TIMEOUT_MS = 60000;
  private static final String DEVICE_FEATURES_COMMAND = "pm list features";
  private static final String GL_EXTENSIONS_COMMAND = "dumpsys SurfaceFlinger";

  private final DeviceFeaturesParser deviceFeaturesParser = new DeviceFeaturesParser();
  private final GlExtensionsParser glExtensionsParser = new GlExtensionsParser();

  public DdmlibDevice(IDevice device, Clock clock) {
    this.device = device;
    this.clock = clock;
  }

  @SuppressWarnings("JavaTimeDefaultTimeZone")
  public DdmlibDevice(IDevice device) {
    this(device, Clock.systemDefaultZone());
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
    int density = device.getDensity();
    if (density != -1) {
      return density;
    }
    // This might be a case when ddmlib is unable to retrieve density via reading properties.
    // For example this happens on Android S emulator.
    try {
      int[] parsedDensityFromShell = new int[] {-1};
      device.executeShellCommand(
          "wm density",
          new MultiLineReceiver() {
            @Override
            public void processNewLines(String[] lines) {
              stream(lines)
                  .filter(string -> string.startsWith(DENSITY_OUTPUT_PREFIX))
                  .map(string -> string.substring(DENSITY_OUTPUT_PREFIX.length()).trim())
                  .map(Ints::tryParse)
                  .forEach(density -> parsedDensityFromShell[0] = density != null ? density : -1);
            }

            @Override
            public boolean isCancelled() {
              return false;
            }
          },
          /* maxTimeToOutputResponse= */ 1,
          MINUTES);
      return parsedDensityFromShell[0];
    } catch (TimeoutException
        | AdbCommandRejectedException
        | ShellCommandUnresponsiveException
        | IOException e) {
      return -1;
    }
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
            .execute(ADB_TIMEOUT_MS, MILLISECONDS));
  }

  @Override
  public ImmutableList<String> getGlExtensions() {
    return glExtensionsParser.parse(
        new AdbShellCommandTask(this, GL_EXTENSIONS_COMMAND).execute(ADB_TIMEOUT_MS, MILLISECONDS));
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
            MILLISECONDS);
      } else {
        device.installPackage(
            Iterables.getOnlyElement(apkFiles).toString(),
            installOptions.getAllowReinstall(),
            extraArgs.build().toArray(new String[0]));
      }
    } catch (InstallException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage("Installation of the app failed.")
          .build();
    }
  }

  @Override
  public void push(ImmutableList<Path> files, PushOptions pushOptions) {
    String splitsPath = pushOptions.getDestinationPath();
    checkArgument(!splitsPath.isEmpty(), "Splits path cannot be empty.");

    RemoteCommandExecutor commandExecutor =
        new RemoteCommandExecutor(this, pushOptions.getTimeout().toMillis(), System.err);
    DdmPreferences.setTimeOut((int) pushOptions.getTimeout().toMillis());
    try {
      splitsPath = resolveLocalTestingPath(splitsPath, pushOptions.getPackageName());
      // Now the path is absolute. We assume it's pointing to a location writeable by ADB shell.
      // It shouldn't point to app's private directory.

      // Some clean up first. Remove the destination dir if flag is set...
      if (pushOptions.getClearDestinationPath()) {
        commandExecutor.executeAndPrint("rm -rf %s", splitsPath);
      }

      // ... and recreate it, making sure the destination dir is empty.
      // We don't want splits from previous runs in the directory.
      // There isn't a nice way to test if dir is empty in shell, but rmdir will return error
      commandExecutor.executeAndPrint("mkdir -p %s && rmdir %1$s && mkdir -p %1$s", splitsPath);

      pushFiles(commandExecutor, splitsPath, files);

      // Fix permission issue for devices on Android S.
      if (device.getVersion().getApiLevel() >= 31 || device.getVersion().isPreview()) {
        commandExecutor.executeAndPrint("chmod 775 %s", splitsPath);
      }
    } catch (IOException
        | TimeoutException
        | SyncException
        | AdbCommandRejectedException
        | ShellCommandUnresponsiveException e) {
      throw CommandExecutionException.builder()
          .withCause(e)
          .withInternalMessage(
              "Pushing additional splits for local testing failed. Your app might still have been"
                  + " installed correctly, but you won't be able to test dynamic modules.")
          .build();
    }
  }

  private void pushFiles(
      RemoteCommandExecutor executor, String splitsPath, ImmutableList<Path> files)
      throws IOException, SyncException, TimeoutException, AdbCommandRejectedException,
          ShellCommandUnresponsiveException {
    try {
      // Try to push files normally. Will fail if ADB shell doesn't have permission to write.
      pushFilesToLocation(splitsPath, files);
    } catch (SyncException e) {
      // On some Android 11 devices push to target location may fail with 'fchown failed:
      // Operation not permitted'. Workaround is to push files to temporary location and next
      // move files.
      if (!e.getMessage().contains("fchown failed: Operation not permitted")) {
        throw e;
      }
      String tempPath = String.format("/data/local/tmp/splits-%d", clock.millis());
      pushFilesToLocation(tempPath, files);
      executor.executeAndPrint("rm -rf %s && mv %s %s", splitsPath, tempPath, splitsPath);
    }
  }

  private void pushFilesToLocation(String splitsPath, ImmutableList<Path> files)
      throws IOException, SyncException, TimeoutException, AdbCommandRejectedException {
    for (Path path : files) {
      device.pushFile(
          path.toFile().getAbsolutePath(),
          joinUnixPaths(splitsPath, path.getFileName().toString()));
      System.err.printf(
          "Pushed \"%s\"%n", joinUnixPaths(splitsPath, path.getFileName().toString()));
    }
  }

  @Override
  public Path syncPackageToDevice(Path localFilePath)
      throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
    return Paths.get(device.syncPackageToDevice(localFilePath.toFile().getAbsolutePath()));
  }

  @Override
  public void removeRemotePath(
      String remoteFilePath, Optional<String> runAsPackageName, Duration timeout)
      throws IOException {
    RemoteCommandExecutor executor =
        new RemoteCommandExecutor(this, timeout.toMillis(), System.err);
    try {
      if (runAsPackageName.isPresent()) {
        executor.executeAndPrint("run-as %s rm -rf %s", runAsPackageName.get(), remoteFilePath);
      } else {
        executor.executeAndPrint("rm -rf %s", remoteFilePath);
      }
    } catch (TimeoutException
        | AdbCommandRejectedException
        | ShellCommandUnresponsiveException
        | IOException e) {
      throw new IOException(String.format("Failed to remove '%s'", remoteFilePath), e);
    }
  }

  @Override
  public void pull(ImmutableList<FilePullParams> files) {
    files.forEach(file -> pullFile(file.getPathOnDevice(), file.getDestinationPath().toString()));
  }

  private void pullFile(String pathOnDevice, String destinationPath) {
    try {
      device.pullFile(pathOnDevice, destinationPath);
    } catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
      throw new CommandExecutionException(
          "Exception while pulling file from the device.", e.getMessage(), e.getCause());
    }
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
      device.executeShellCommand(command + " && echo OK", receiver, timeout, MILLISECONDS);
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
          command, stream(args).map(RemoteCommandExecutor::escapeAndSingleQuote).toArray());
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

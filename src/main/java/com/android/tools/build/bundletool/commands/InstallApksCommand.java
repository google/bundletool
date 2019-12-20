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

package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.ANDROID_HOME_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.SYSTEM_PATH_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkDirectoryExists;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndExecutable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbRunner;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.device.Device.InstallOptions;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.ResultUtils;
import com.android.tools.build.bundletool.model.utils.SdkToolsLocator;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Installs APKs on a connected device. */
@AutoValue
public abstract class InstallApksCommand {

  public static final String COMMAND_NAME = "install-apks";

  private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");
  private static final Flag<Path> APKS_ARCHIVE_FILE_FLAG = Flag.path("apks");
  private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");
  private static final Flag<ImmutableSet<String>> MODULES_FLAG = Flag.stringSet("modules");
  private static final Flag<Boolean> ALLOW_DOWNGRADE_FLAG = Flag.booleanFlag("allow-downgrade");
  private static final Flag<String> PUSH_SPLITS_FLAG = Flag.string("push-splits-to");
  private static final Flag<Boolean> CLEAR_PUSH_PATH_FLAG = Flag.booleanFlag("clear-push-path");
  private static final Flag<Boolean> ALLOW_TEST_ONLY_FLAG = Flag.booleanFlag("allow-test-only");

  private static final String ANDROID_SERIAL_VARIABLE = "ANDROID_SERIAL";

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  public abstract Path getAdbPath();

  public abstract Path getApksArchivePath();

  public abstract Optional<String> getDeviceId();

  public abstract Optional<ImmutableSet<String>> getModules();

  public abstract boolean getAllowDowngrade();

  public abstract Optional<String> getPushSplitsPath();

  public abstract boolean getClearPushPath();

  public abstract boolean getAllowTestOnly();

  abstract AdbServer getAdbServer();

  public static Builder builder() {
    return new AutoValue_InstallApksCommand.Builder()
        .setAllowDowngrade(false)
        .setClearPushPath(false)
        .setAllowTestOnly(false);
  }

  /** Builder for the {@link InstallApksCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setAdbPath(Path adbPath);

    public abstract Builder setApksArchivePath(Path apksArchivePath);

    public abstract Builder setDeviceId(String deviceId);

    public abstract Builder setModules(ImmutableSet<String> modules);

    public abstract Builder setAllowDowngrade(boolean allowDowngrade);

    /** The caller is responsible for the lifecycle of the {@link AdbServer}. */
    public abstract Builder setAdbServer(AdbServer adbServer);

    public abstract Builder setPushSplitsPath(String pushSplitsPath);

    public abstract Builder setClearPushPath(boolean clearPushPath);

    public abstract Builder setAllowTestOnly(boolean allowTestOnly);

    public abstract InstallApksCommand build();
  }

  public static InstallApksCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
    return fromFlags(flags, DEFAULT_PROVIDER, adbServer);
  }

  public static InstallApksCommand fromFlags(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
    Path apksArchivePath = APKS_ARCHIVE_FILE_FLAG.getRequiredValue(flags);
    Path adbPath =
        ADB_PATH_FLAG
            .getValue(flags)
            .orElseGet(
                () ->
                    new SdkToolsLocator()
                        .locateAdb(systemEnvironmentProvider)
                        .orElseThrow(
                            () ->
                                new CommandExecutionException(
                                    "Unable to determine the location of ADB. Please set the --adb "
                                        + "flag or define ANDROID_HOME or PATH environment "
                                        + "variable.")));

    Optional<String> deviceSerialName = DEVICE_ID_FLAG.getValue(flags);
    if (!deviceSerialName.isPresent()) {
      deviceSerialName = systemEnvironmentProvider.getVariable(ANDROID_SERIAL_VARIABLE);
    }

    Optional<ImmutableSet<String>> modules = MODULES_FLAG.getValue(flags);
    Optional<Boolean> allowDowngrade = ALLOW_DOWNGRADE_FLAG.getValue(flags);
    Optional<String> pushSplits = PUSH_SPLITS_FLAG.getValue(flags);
    Optional<Boolean> clearPushPath = CLEAR_PUSH_PATH_FLAG.getValue(flags);
    Optional<Boolean> allowTestOnly = ALLOW_TEST_ONLY_FLAG.getValue(flags);

    flags.checkNoUnknownFlags();

    InstallApksCommand.Builder command =
        builder().setAdbPath(adbPath).setAdbServer(adbServer).setApksArchivePath(apksArchivePath);
    deviceSerialName.ifPresent(command::setDeviceId);
    modules.ifPresent(command::setModules);
    allowDowngrade.ifPresent(command::setAllowDowngrade);
    allowTestOnly.ifPresent(command::setAllowTestOnly);

    return command.build();
  }

  public void execute() {
    validateInput();

    AdbServer adbServer = getAdbServer();
    adbServer.init(getAdbPath());

    try (TempDirectory tempDirectory = new TempDirectory()) {
      DeviceSpec deviceSpec = new DeviceAnalyzer(adbServer).getDeviceSpec(getDeviceId());

      ExtractApksCommand.Builder extractApksCommand =
          ExtractApksCommand.builder()
              .setApksArchivePath(getApksArchivePath())
              .setDeviceSpec(deviceSpec);

      if (!Files.isDirectory(getApksArchivePath())) {
        extractApksCommand.setOutputDirectory(tempDirectory.getPath());
      }
      getModules().ifPresent(extractApksCommand::setModules);
      final ImmutableList<Path> extractedApks = extractApksCommand.build().execute();

      AdbRunner adbRunner = new AdbRunner(adbServer);
      InstallOptions installOptions =
          InstallOptions.builder()
              .setAllowDowngrade(getAllowDowngrade())
              .setAllowTestOnly(getAllowTestOnly())
              .build();

      if (getDeviceId().isPresent()) {
        adbRunner.run(
            device -> device.installApks(extractedApks, installOptions), getDeviceId().get());
      } else {
        adbRunner.run(device -> device.installApks(extractedApks, installOptions));
      }

      pushSplits(deviceSpec, extractApksCommand.build(), adbRunner);
    }
  }

  private void pushSplits(
      DeviceSpec baseSpec, ExtractApksCommand baseExtractCommand, AdbRunner adbRunner) {
    if (!getPushSplitsPath().isPresent()) {
      return;
    }

    ExtractApksCommand.Builder extractApksCommand = ExtractApksCommand.builder();
    extractApksCommand.setApksArchivePath(baseExtractCommand.getApksArchivePath());
    baseExtractCommand.getOutputDirectory().ifPresent(extractApksCommand::setOutputDirectory);

    // We want to push all modules...
    extractApksCommand.setModules(ImmutableSet.of(ExtractApksCommand.ALL_MODULES_SHORTCUT));
    // ... and all languages
    BuildApksResult toc = ResultUtils.readTableOfContents(getApksArchivePath());
    ImmutableSet<String> targetedLanguages = ResultUtils.getAllTargetedLanguages(toc);
    final ImmutableList<Path> extractedApksForPush =
        extractApksCommand
            .setDeviceSpec(baseSpec.toBuilder().addAllSupportedLocales(targetedLanguages).build())
            .build()
            .execute();

    Device.PushOptions.Builder pushOptions =
        Device.PushOptions.builder()
            .setDestinationPath(getPushSplitsPath().get())
            .setClearDestinationPath(getClearPushPath());

    // We're going to need the package name later for pushing to relative paths
    // (i.e. inside the app's private directory)
    if (!getPushSplitsPath().get().startsWith("/")) {
      String packageName = toc.getPackageName();
      if (packageName.isEmpty()) {
        throw new CommandExecutionException(
            "Unable to determine the package name of the base APK. If your APK "
                + "set was produced using an older version of bundletool, please "
                + "regenerate it. Alternatively, you can try again with an "
                + "absolute path for --push-splits-to, pointing to a location "
                + "that is writeable by the shell user, e.g. /sdcard/...");
      }
      pushOptions.setPackageName(packageName);
    }

    if (getDeviceId().isPresent()) {
      adbRunner.run(
          device -> device.pushApks(extractedApksForPush, pushOptions.build()),
          getDeviceId().get());
    } else {
      adbRunner.run(device -> device.pushApks(extractedApksForPush, pushOptions.build()));
    }
  }

  private void validateInput() {
    if (Files.isDirectory(getApksArchivePath())) {
      checkDirectoryExists(getApksArchivePath());
    } else {
      checkFileExistsAndReadable(getApksArchivePath());
    }
    checkFileExistsAndExecutable(getAdbPath());
    if (getClearPushPath()) {
      checkArgument(
          getPushSplitsPath().isPresent(),
          "--%s only applies when --%s is set.",
          CLEAR_PUSH_PATH_FLAG.getName(),
          PUSH_SPLITS_FLAG.getName());
    }
    getPushSplitsPath()
        .ifPresent(
            path ->
                checkArgument(
                    !path.isEmpty(),
                    "The value of the flag --%s cannot be empty.",
                    PUSH_SPLITS_FLAG.getName()));
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Installs APKs extracted from an APK Set to a connected device. "
                        + "Replaces already installed package.")
                .addAdditionalParagraph(
                    "This will extract from the APK Set archive and install only the APKs that "
                        + "would be served to that device. If the app is not compatible with the "
                        + "device or if the APK Set archive was generated for a different type of "
                        + "device, this command will fail.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ADB_PATH_FLAG.getName())
                .setExampleValue("path/to/adb")
                .setOptional(true)
                .setDescription(
                    "Path to the adb utility. If absent, an attempt will be made to locate it if "
                        + "the %s or %s environment variable is set.",
                    ANDROID_HOME_VARIABLE, SYSTEM_PATH_VARIABLE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APKS_ARCHIVE_FILE_FLAG.getName())
                .setExampleValue("archive.apks")
                .setDescription(
                    "Path to the archive file generated by the '%s' command.",
                    BuildApksCommand.COMMAND_NAME)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_ID_FLAG.getName())
                .setExampleValue("device-serial-name")
                .setOptional(true)
                .setDescription(
                    "Device serial name. If absent, this uses the %s environment variable. Either "
                        + "this flag or the environment variable is required when more than one "
                        + "device or emulator is connected.",
                    ANDROID_SERIAL_VARIABLE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ALLOW_DOWNGRADE_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "If set, allows APKs to be installed on the device even if the app is already "
                        + "installed with a lower version code.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODULES_FLAG.getName())
                .setExampleValue("base,module1,module2")
                .setOptional(true)
                .setDescription(
                    "List of modules to be installed, or \"%s\" for all modules. "
                        + "Defaults to modules installed during the first install, i.e. not "
                        + "on-demand. Note that the dependent modules will also be extracted. The "
                        + "value of this flag is ignored if the device receives a standalone APK.",
                    ExtractApksCommand.ALL_MODULES_SHORTCUT)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ALLOW_TEST_ONLY_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "If set, apps with 'android:testOnly=true' set in their manifest can also be"
                        + " deployed")
                .build())
        .build();
  }
}

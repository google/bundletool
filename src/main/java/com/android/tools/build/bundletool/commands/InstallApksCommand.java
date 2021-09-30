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

import static com.android.tools.build.bundletool.commands.CommandUtils.ANDROID_SERIAL_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.ANDROID_HOME_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.SYSTEM_PATH_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkDirectoryExists;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndExecutable;
import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.AssetModuleMetadata;
import com.android.bundle.Commands.AssetSliceSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.DeliveryType;
import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbRunner;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.device.Device.InstallOptions;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.device.LocalTestingPathResolver;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.ResultUtils;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.protobuf.Int32Value;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
  private static final Flag<Boolean> ALLOW_TEST_ONLY_FLAG = Flag.booleanFlag("allow-test-only");
  private static final Flag<Integer> DEVICE_TIER_FLAG = Flag.nonNegativeInteger("device-tier");
  private static final Flag<ImmutableSet<String>> DEVICE_GROUPS_FLAG =
      Flag.stringSet("device-groups");
  private static final Flag<ImmutableList<Path>> ADDITIONAL_LOCAL_TESTING_FILES_FLAG =
      Flag.pathList("additional-local-testing-files");
  private static final Flag<Integer> TIMEOUT_MILLIS_FLAG = Flag.positiveInteger("timeout-millis");

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  public abstract Path getAdbPath();

  public abstract Path getApksArchivePath();

  public abstract Optional<String> getDeviceId();

  public abstract Optional<ImmutableSet<String>> getModules();

  public abstract boolean getAllowDowngrade();

  public abstract boolean getAllowTestOnly();

  public abstract Optional<Integer> getDeviceTier();

  public abstract Optional<ImmutableSet<String>> getDeviceGroups();

  public abstract Optional<ImmutableList<Path>> getAdditionalLocalTestingFiles();

  abstract AdbServer getAdbServer();

  public abstract Duration getTimeout();

  public static Builder builder() {
    return new AutoValue_InstallApksCommand.Builder()
        .setAllowDowngrade(false)
        .setAllowTestOnly(false)
        .setTimeout(Device.DEFAULT_ADB_TIMEOUT);
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

    public abstract Builder setAllowTestOnly(boolean allowTestOnly);

    public abstract Builder setDeviceTier(Integer deviceTier);

    public abstract Builder setDeviceGroups(ImmutableSet<String> deviceGroups);

    public abstract Builder setAdditionalLocalTestingFiles(ImmutableList<Path> additionalFiles);

    public abstract Builder setTimeout(Duration timeout);

    public abstract InstallApksCommand build();
  }

  public static InstallApksCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
    return fromFlags(flags, DEFAULT_PROVIDER, adbServer);
  }

  public static InstallApksCommand fromFlags(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
    Path apksArchivePath = APKS_ARCHIVE_FILE_FLAG.getRequiredValue(flags);
    Path adbPath = CommandUtils.getAdbPath(flags, ADB_PATH_FLAG, systemEnvironmentProvider);

    Optional<String> deviceSerialName =
        CommandUtils.getDeviceSerialName(flags, DEVICE_ID_FLAG, systemEnvironmentProvider);

    Optional<ImmutableSet<String>> modules = MODULES_FLAG.getValue(flags);
    Optional<Boolean> allowDowngrade = ALLOW_DOWNGRADE_FLAG.getValue(flags);
    Optional<Boolean> allowTestOnly = ALLOW_TEST_ONLY_FLAG.getValue(flags);
    Optional<Integer> deviceTier = DEVICE_TIER_FLAG.getValue(flags);
    Optional<ImmutableSet<String>> deviceGroups = DEVICE_GROUPS_FLAG.getValue(flags);
    Optional<ImmutableList<Path>> additionalLocalTestingFiles =
        ADDITIONAL_LOCAL_TESTING_FILES_FLAG.getValue(flags);
    Optional<Integer> timeoutMillis = TIMEOUT_MILLIS_FLAG.getValue(flags);

    flags.checkNoUnknownFlags();

    InstallApksCommand.Builder command =
        builder().setAdbPath(adbPath).setAdbServer(adbServer).setApksArchivePath(apksArchivePath);
    deviceSerialName.ifPresent(command::setDeviceId);
    modules.ifPresent(command::setModules);
    allowDowngrade.ifPresent(command::setAllowDowngrade);
    allowTestOnly.ifPresent(command::setAllowTestOnly);
    deviceTier.ifPresent(command::setDeviceTier);
    deviceGroups.ifPresent(command::setDeviceGroups);
    additionalLocalTestingFiles.ifPresent(command::setAdditionalLocalTestingFiles);
    timeoutMillis.ifPresent(timeout -> command.setTimeout(Duration.ofMillis(timeout)));

    return command.build();
  }

  public void execute() {
    BuildApksResult toc = readBuildApksResult();
    validateInput(toc);

    AdbServer adbServer = getAdbServer();
    adbServer.init(getAdbPath());

    try (TempDirectory tempDirectory = new TempDirectory()) {
      DeviceSpec deviceSpec = new DeviceAnalyzer(adbServer).getDeviceSpec(getDeviceId());
      if (getDeviceTier().isPresent()) {
        deviceSpec =
            deviceSpec.toBuilder().setDeviceTier(Int32Value.of(getDeviceTier().get())).build();
      }
      if (getDeviceGroups().isPresent()) {
        deviceSpec = deviceSpec.toBuilder().addAllDeviceGroups(getDeviceGroups().get()).build();
      }

      final ImmutableList<Path> apksToInstall =
          getApksToInstall(toc, deviceSpec, tempDirectory.getPath());
      final ImmutableList<Path> filesToPush =
          ImmutableList.<Path>builder()
              .addAll(getApksToPushToStorage(toc, deviceSpec, tempDirectory.getPath()))
              .addAll(getAdditionalLocalTestingFiles().orElse(ImmutableList.of()))
              .build();

      AdbRunner adbRunner = new AdbRunner(adbServer);
      InstallOptions installOptions =
          InstallOptions.builder()
              .setAllowDowngrade(getAllowDowngrade())
              .setAllowTestOnly(getAllowTestOnly())
              .setTimeout(getTimeout())
              .build();

      if (getDeviceId().isPresent()) {
        adbRunner.run(
            device -> device.installApks(apksToInstall, installOptions), getDeviceId().get());
      } else {
        adbRunner.run(device -> device.installApks(apksToInstall, installOptions));
      }

      if (!filesToPush.isEmpty()) {
        pushFiles(filesToPush, toc, adbRunner);
      }
      if (toc.getLocalTestingInfo().getEnabled()) {
        cleanUpEmulatedSplits(adbRunner, toc);
      }
    }
  }

  private void cleanUpEmulatedSplits(AdbRunner adbRunner, BuildApksResult toc) {
    adbRunner.run(
        device -> {
          try {
            device.removeRemotePath(
                LocalTestingPathResolver.getLocalTestingWorkingDir(toc.getPackageName()),
                Optional.of(toc.getPackageName()),
                getTimeout());
          } catch (IOException e) {
            System.err.println(
                "Failed to remove working directory with local testing splits. Your app might"
                    + " still have been installed correctly but have previous version of"
                    + " dynamic feature modules. If you see legacy versions of dynamic feature"
                    + " modules installed try to uninstall and install the app again.");
          }
        });
  }

  /** Extracts the apks that will be installed. */
  private ImmutableList<Path> getApksToInstall(
      BuildApksResult toc, DeviceSpec deviceSpec, Path output) {
    ExtractApksCommand.Builder extractApksCommand =
        ExtractApksCommand.builder()
            .setApksArchivePath(getApksArchivePath())
            .setDeviceSpec(deviceSpec);
    if (!Files.isDirectory(getApksArchivePath())) {
      extractApksCommand.setOutputDirectory(output);
    }
    ImmutableSet<String> dynamicAssetModules =
        toc.getAssetSliceSetList().stream()
            .map(AssetSliceSet::getAssetModuleMetadata)
            .filter(metadata -> metadata.getDeliveryType() != DeliveryType.INSTALL_TIME)
            .map(AssetModuleMetadata::getName)
            .collect(toImmutableSet());
    getModules()
        .map(modules -> ExtractApksCommand.resolveRequestedModules(modules, toc))
        .map(modules -> Sets.difference(modules, dynamicAssetModules).immutableCopy())
        .ifPresent(extractApksCommand::setModules);
    return extractApksCommand.build().execute();
  }

  /**
   * Extracts the apks that will be pushed to storage in local testing mode.
   *
   * <p>This includes:
   *
   * <ul>
   *   <li>Non-master splits of the base module that match the device targeting.
   *   <li>All feature modules splits that match the device targeting.
   *   <li>All non install-time asset modules splits that match the device targeting.
   *   <li>All language splits.
   * </ul>
   */
  private ImmutableList<Path> getApksToPushToStorage(
      BuildApksResult toc, DeviceSpec deviceSpec, Path output) {
    if (!toc.getLocalTestingInfo().getEnabled()) {
      return ImmutableList.of();
    }
    ExtractApksCommand.Builder extractApksCommand =
        ExtractApksCommand.builder()
            .setApksArchivePath(getApksArchivePath())
            .setDeviceSpec(addAllSupportedLanguages(deviceSpec, toc));
    if (!Files.isDirectory(getApksArchivePath())) {
      extractApksCommand.setOutputDirectory(output);
    }
    ImmutableSet<String> installTimeAssetModules =
        toc.getAssetSliceSetList().stream()
            .map(AssetSliceSet::getAssetModuleMetadata)
            .filter(metadata -> metadata.getDeliveryType() == DeliveryType.INSTALL_TIME)
            .map(AssetModuleMetadata::getName)
            .collect(toImmutableSet());
    ImmutableSet<String> allModules =
        ExtractApksCommand.resolveRequestedModules(
            ImmutableSet.of(ExtractApksCommand.ALL_MODULES_SHORTCUT), toc);
    extractApksCommand.setModules(
        Sets.difference(allModules, installTimeAssetModules).immutableCopy());
    return extractApksCommand.build().execute().stream()
        .filter(
            apk ->
                !ResultUtils.getAllBaseMasterSplitPaths(toc).contains(apk.getFileName().toString()))
        .collect(toImmutableList());
  }

  private void pushFiles(ImmutableList<Path> files, BuildApksResult toc, AdbRunner adbRunner) {
    String packageName = toc.getPackageName();
    if (packageName.isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Unable to determine the package name of the base APK. If your APK set was produced"
                  + " using an older version of bundletool, please regenerate it.")
          .build();
    }
    Device.PushOptions.Builder pushOptions =
        Device.PushOptions.builder()
            .setDestinationPath(toc.getLocalTestingInfo().getLocalTestingPath())
            .setClearDestinationPath(true)
            .setPackageName(packageName)
            .setTimeout(getTimeout());

    if (getDeviceId().isPresent()) {
      adbRunner.run(device -> device.push(files, pushOptions.build()), getDeviceId().get());
    } else {
      adbRunner.run(device -> device.push(files, pushOptions.build()));
    }
  }

  /** Adds all supported languages in the given {@link BuildApksResult} to a {@link DeviceSpec}. */
  private static DeviceSpec addAllSupportedLanguages(DeviceSpec deviceSpec, BuildApksResult toc) {
    ImmutableSet<String> targetedLanguages = ResultUtils.getAllTargetedLanguages(toc);
    return deviceSpec.toBuilder().addAllSupportedLocales(targetedLanguages).build();
  }

  private void validateInput(BuildApksResult toc) {
    checkFileExistsAndExecutable(getAdbPath());
    if (getAdditionalLocalTestingFiles().isPresent()) {
      checkArgument(
          toc.getLocalTestingInfo().getEnabled(),
          "'%s' flag is only supported for APKs built in local testing mode.",
          ADDITIONAL_LOCAL_TESTING_FILES_FLAG.getName());
    }
  }

  private BuildApksResult readBuildApksResult() {
    if (Files.isDirectory(getApksArchivePath())) {
      checkDirectoryExists(getApksArchivePath());
    } else {
      checkFileExistsAndReadable(getApksArchivePath());
    }
    return ResultUtils.readTableOfContents(getApksArchivePath());
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
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_TIER_FLAG.getName())
                .setExampleValue("high")
                .setOptional(true)
                .setDescription(
                    "Device tier of the given device. This value will be used to match the correct"
                        + " device tier targeted APKs to this device."
                        + " This flag is only relevant if the bundle uses device tier targeting,"
                        + " and should be set in that case.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_GROUPS_FLAG.getName())
                .setExampleValue("highRam,googlePixel")
                .setOptional(true)
                .setDescription(
                    "Device groups the given device belongs to. This value will be used to match"
                        + " the correct device group conditional modules to this device."
                        + " This flag is only relevant if the bundle uses device group targeting"
                        + " in conditional modules and should be set in that case.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ADDITIONAL_LOCAL_TESTING_FILES_FLAG.getName())
                .setOptional(true)
                .setDescription(
                    "List of files which will be additionally pushed to local testing folder. This"
                        + " flag is only supported for APKs built in local testing mode.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(TIMEOUT_MILLIS_FLAG.getName())
                .setExampleValue("60000")
                .setOptional(true)
                .setDescription(
                    "Timeout in milliseconds which is passed to adb commands. Default is 10"
                        + " minutes.")
                .build())
        .build();
  }
}

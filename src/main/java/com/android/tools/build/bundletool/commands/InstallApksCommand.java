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

import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.ApksInstaller;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.device.Device.InstallOptions;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.io.TempDirectory;
import com.android.tools.build.bundletool.model.Aapt2Command;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
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
  private static final Flag<Path> PUSH_SPLITS_FLAG = Flag.path("push-splits-to");

  private static final String ANDROID_SERIAL_VARIABLE = "ANDROID_SERIAL";

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  public abstract Path getAdbPath();

  public abstract Path getApksArchivePath();

  public abstract Optional<String> getDeviceId();

  public abstract Optional<ImmutableSet<String>> getModules();

  public abstract boolean getAllowDowngrade();

  public abstract Optional<Path> getPushSplitsPath();

  abstract AdbServer getAdbServer();

  public static Builder builder() {
    return new AutoValue_InstallApksCommand.Builder().setAllowDowngrade(false);
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

    public abstract Builder setPushSplitsPath(Path pushSplitsPath);

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
    Optional<Path> pushSplits = PUSH_SPLITS_FLAG.getValue(flags);

    flags.checkNoUnknownFlags();

    InstallApksCommand.Builder command =
        builder().setAdbPath(adbPath).setAdbServer(adbServer).setApksArchivePath(apksArchivePath);
    deviceSerialName.ifPresent(command::setDeviceId);
    modules.ifPresent(command::setModules);
    allowDowngrade.ifPresent(command::setAllowDowngrade);
    pushSplits.ifPresent(command::setPushSplitsPath);

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
      ImmutableList<Path> extractedApks = extractApksCommand.build().execute();

      ApksInstaller installer = new ApksInstaller(adbServer);
      InstallOptions installOptions =
          InstallOptions.builder().setAllowDowngrade(getAllowDowngrade()).build();

      if (getDeviceId().isPresent()) {
        ImmutableList<Path> finalExtractedApks = extractedApks;
        installer.run(device -> device.installApks(finalExtractedApks, installOptions), getDeviceId().get());
      } else {
        ImmutableList<Path> finalExtractedApks = extractedApks;
        installer.run(device -> device.installApks(finalExtractedApks, installOptions));
      }

      if (getPushSplitsPath().isPresent()) {
        extractApksCommand.setModules(ImmutableSet.of(ExtractApksCommand.ALL_MODULES_SHORTCUT));
        extractedApks = extractApksCommand.build().execute(); //TODO add device spec for languages

        Device.PushOptions.Builder pushOptions = Device.PushOptions.builder()
                .setDestinationPath(getPushSplitsPath().get());

        if (!getPushSplitsPath().get().isAbsolute()) {
          Aapt2Command aapt2Command = BuildApksCommand.extractAapt2FromJar(tempDirectory.getPath());
          Optional<String> packageName = extractedApks.stream()
                  .filter(path -> "base-master.apk".equals(path.getFileName().toString()))
                  .findFirst()
                  .map(aapt2Command::getPackageNameFromApk);
          if (packageName.isPresent()) {
            pushOptions.setPackageName(packageName.get());
          } else {
            throw new CommandExecutionException("Unable to determine the package name of the base APK." +
                    "You can try again with an absolute path for --push-splits-to, pointing" +
                    "to a location that is writeable by the shell user, e.g. /sdcard/...");
          }
        }

        if (getDeviceId().isPresent()) {
          ImmutableList<Path> finalExtractedApks = extractedApks;
          installer.run(device -> device.pushApks(finalExtractedApks, pushOptions.build()), getDeviceId().get());
        } else {
          ImmutableList<Path> finalExtractedApks = extractedApks;
          installer.run(device -> device.pushApks(finalExtractedApks, pushOptions.build()));
        }
      }
    }
  }

  private void validateInput() {
    if (Files.isDirectory(getApksArchivePath())) {
      checkDirectoryExists(getApksArchivePath());
    } else {
      checkFileExistsAndReadable(getApksArchivePath());
    }
    checkFileExistsAndExecutable(getAdbPath());
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
                        .setFlagName(PUSH_SPLITS_FLAG.getName())
                        .setExampleValue("mysplits")
                        .setOptional(true)
                        .setDescription(
                                "If set, bundletool will also extract and push all splits (including "
                                    + "on-demand) to the chosen location on the device. This is useful "
                                    + "if you want to test split installation with FakeSplitInstallManager. "
                                    + "For debuggable apps, you can use a relative path, which will resolve "
                                    + "into the private application directory. If you use an absolute path, "
                                    + "it must point to a location writable by the ADB user.")
                        .build())
        .build();
  }
}

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
package com.android.tools.build.bundletool.commands;

import static com.android.tools.build.bundletool.commands.CommandUtils.ANDROID_SERIAL_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.ANDROID_HOME_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.SYSTEM_PATH_VARIABLE;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

/** Command to verify code transparency. */
@AutoValue
public abstract class CheckTransparencyCommand {

  public static final String COMMAND_NAME = "check-transparency";

  enum Mode {
    CONNECTED_DEVICE,
    BUNDLE,
    APK;

    final String getLowerCaseName() {
      return Ascii.toLowerCase(name());
    }
  }

  private static final Flag<Mode> MODE_FLAG = Flag.enumFlag("mode", Mode.class);

  private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");

  private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");

  private static final Flag<Path> BUNDLE_LOCATION_FLAG = Flag.path("bundle");

  private static final Flag<Path> APK_ZIP_LOCATION_FLAG = Flag.path("apk-zip");

  private static final String MODE_FLAG_OPTIONS =
      stream(Mode.values()).map(Mode::getLowerCaseName).collect(joining("|"));

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  public abstract Mode getMode();

  public abstract Optional<Path> getAdbPath();

  public abstract Optional<String> getDeviceId();

  public abstract Optional<AdbServer> getAdbServer();

  public abstract Optional<Path> getBundlePath();

  public abstract Optional<Path> getApkZipPath();

  public static CheckTransparencyCommand.Builder builder() {
    return new AutoValue_CheckTransparencyCommand.Builder();
  }

  /** Builder for the {@link CheckTransparencyCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setMode(Mode mode);

    public abstract Builder setAdbPath(Path adbPath);

    public abstract Builder setDeviceId(String deviceId);

    public abstract Builder setAdbServer(AdbServer adbServer);

    /** Sets the path to the input bundle. Must have the extension ".aab". */
    public abstract Builder setBundlePath(Path bundlePath);

    /** Sets the path to the .zip archive of device-specific APK files. */
    public abstract Builder setApkZipPath(Path apkZipPath);

    public abstract CheckTransparencyCommand build();
  }

  public static CheckTransparencyCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
    return fromFlags(flags, DEFAULT_PROVIDER, adbServer);
  }

  public static CheckTransparencyCommand fromFlags(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
    Mode mode = MODE_FLAG.getRequiredValue(flags);
    switch (mode) {
      case CONNECTED_DEVICE:
        return fromFlagsInConnectedDeviceMode(flags, systemEnvironmentProvider, adbServer);
      case BUNDLE:
        return fromFlagsInBundleMode(flags);
      case APK:
        return fromFlagsInApkMode(flags);
    }
    throw new IllegalStateException("Unrecognized value of --mode flag.");
  }

  private static CheckTransparencyCommand fromFlagsInBundleMode(ParsedFlags flags) {
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder()
            .setMode(Mode.BUNDLE)
            .setBundlePath(BUNDLE_LOCATION_FLAG.getRequiredValue(flags))
            .build();
    flags.checkNoUnknownFlags();
    return checkTransparencyCommand;
  }

  private static CheckTransparencyCommand fromFlagsInApkMode(ParsedFlags flags) {
    CheckTransparencyCommand checkTransparencyCommand =
        CheckTransparencyCommand.builder()
            .setMode(Mode.APK)
            .setApkZipPath(APK_ZIP_LOCATION_FLAG.getRequiredValue(flags))
            .build();
    flags.checkNoUnknownFlags();
    return checkTransparencyCommand;
  }

  private static CheckTransparencyCommand fromFlagsInConnectedDeviceMode(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
    CheckTransparencyCommand.Builder checkTransparencyCommand =
        CheckTransparencyCommand.builder().setMode(Mode.CONNECTED_DEVICE);
    Optional<String> deviceSerialName = DEVICE_ID_FLAG.getValue(flags);
    if (!deviceSerialName.isPresent()) {
      deviceSerialName = systemEnvironmentProvider.getVariable(ANDROID_SERIAL_VARIABLE);
    }
    deviceSerialName.ifPresent(checkTransparencyCommand::setDeviceId);

    Path adbPath = CommandUtils.getAdbPath(flags, ADB_PATH_FLAG, systemEnvironmentProvider);
    checkTransparencyCommand.setAdbPath(adbPath).setAdbServer(adbServer);
    flags.checkNoUnknownFlags();
    return checkTransparencyCommand.build();
  }

  public void execute() {
    validateInput();
    checkTransparency(System.out);
  }

  public void checkTransparency(PrintStream outputStream) {
    switch (getMode()) {
      case CONNECTED_DEVICE:
        ConnectedDeviceTransparencyChecker.checkTransparency(this, outputStream);
        break;
      case BUNDLE:
        BundleTransparencyChecker.checkTransparency(this, outputStream);
        break;
      case APK:
        ApkTransparencyChecker.checkTransparency(this, outputStream);
        break;
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder().setShortDescription("Verifies code transparency.").build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(MODE_FLAG.getName())
                .setExampleValue(MODE_FLAG_OPTIONS)
                .setOptional(true)
                .setDescription(
                    "Specifies which mode to run '%s' command against. Acceptable values are '%s'."
                        + " If set to '%s' we verify code transparency for a given app installed"
                        + " on a connected device. If set to '%s', we verify code transparency for"
                        + " a given Android App Bundle file. If set to '%s', we verify code"
                        + " transparency for a given set of device-specific APK files.",
                    CheckTransparencyCommand.COMMAND_NAME,
                    MODE_FLAG_OPTIONS,
                    Mode.CONNECTED_DEVICE.getLowerCaseName(),
                    Mode.BUNDLE.getLowerCaseName(),
                    Mode.APK.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ADB_PATH_FLAG.getName())
                .setExampleValue("path/to/adb")
                .setOptional(true)
                .setDescription(
                    "Path to the adb utility. Used only in '%s' mode. If absent, an attempt will"
                        + " be made to locate it if the %s or %s environment variable is set.",
                    Mode.CONNECTED_DEVICE.getLowerCaseName(),
                    ANDROID_HOME_VARIABLE,
                    SYSTEM_PATH_VARIABLE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_ID_FLAG.getName())
                .setExampleValue("device-serial-name")
                .setOptional(true)
                .setDescription(
                    "Device serial name. Used only in '%s' mode. If absent, this uses"
                        + " the %s environment variable. Either this flag or the environment"
                        + " variable is required when more than one device or emulator is"
                        + " connected.",
                    Mode.CONNECTED_DEVICE.getLowerCaseName(), ANDROID_SERIAL_VARIABLE)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(BUNDLE_LOCATION_FLAG.getName())
                .setExampleValue("path/to/bundle.aab")
                .setOptional(true)
                .setDescription(
                    "Path to the Android App Bundle that we want to verify code transparency for."
                        + " Used only in '%s' mode. Must have extension .aab.",
                    Mode.BUNDLE.getLowerCaseName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(APK_ZIP_LOCATION_FLAG.getName())
                .setExampleValue("path/to/apks.zip")
                .setOptional(true)
                .setDescription(
                    "Path to the zip archive of device specific APKs that we want to verify code"
                        + " transparency for. Used only in '%s' mode. Must have extension .zip.",
                    Mode.APK.getLowerCaseName())
                .build())
        .build();
  }

  private void validateInput() {
    if (getBundlePath().isPresent()) {
      FilePreconditions.checkFileHasExtension("AAB file", getBundlePath().get(), ".aab");
      FilePreconditions.checkFileExistsAndReadable(getBundlePath().get());
    }
    if (getApkZipPath().isPresent()) {
      FilePreconditions.checkFileHasExtension("Zip file", getApkZipPath().get(), ".zip");
      FilePreconditions.checkFileExistsAndReadable(getApkZipPath().get());
    }
  }
}

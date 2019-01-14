/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.Devices.DeviceSpec;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SdkToolsLocator;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.google.auto.value.AutoValue;
import com.google.common.io.MoreFiles;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/** Command to fetch the configuration of the connected device. */
@AutoValue
public abstract class GetDeviceSpecCommand {

  public static final String COMMAND_NAME = "get-device-spec";

  private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");
  private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");
  private static final Flag<Path> OUTPUT_FLAG = Flag.path("output");
  private static final Flag<Boolean> OVERWRITE_OUTPUT_FLAG = Flag.booleanFlag("overwrite");

  private static final String ANDROID_HOME_VARIABLE = "ANDROID_HOME";
  private static final String ANDROID_SERIAL_VARIABLE = "ANDROID_SERIAL";

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  private static final String JSON_EXTENSION = "json";

  public abstract Path getAdbPath();

  public abstract Optional<String> getDeviceId();

  public abstract Path getOutputPath();

  public abstract boolean getOverwriteOutput();

  abstract AdbServer getAdbServer();

  public static Builder builder() {
    return new AutoValue_GetDeviceSpecCommand.Builder().setOverwriteOutput(false);
  }

  /** Builder for the {@link GetDeviceSpecCommand}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setOutputPath(Path outputPath);

    /**
     * Sets whether to overwrite the contents of the output file.
     *
     * <p>The default is {@code false}. If set to {@code false} and the output file is present,
     * exception is thrown.
     */
    public abstract Builder setOverwriteOutput(boolean overwriteOutput);

    public abstract Builder setAdbPath(Path adbPath);

    public abstract Builder setDeviceId(String deviceId);

    /** The caller is responsible for the lifecycle of the {@link AdbServer}. */
    public abstract Builder setAdbServer(AdbServer adbServer);

    abstract GetDeviceSpecCommand autoBuild();

    public GetDeviceSpecCommand build() {
      GetDeviceSpecCommand command = autoBuild();

      if (!JSON_EXTENSION.equals(MoreFiles.getFileExtension(command.getOutputPath()))) {
        throw ValidationException.builder()
            .withMessage(
                "Flag --output should be the path where to generate the device spec file. "
                    + "Its extension must be '.json'.")
            .build();
      }

      return command;
    }
  }

  public static GetDeviceSpecCommand fromFlags(ParsedFlags flags, AdbServer adbServer) {
    return fromFlags(flags, DEFAULT_PROVIDER, adbServer);
  }

  public static GetDeviceSpecCommand fromFlags(
      ParsedFlags flags, SystemEnvironmentProvider systemEnvironmentProvider, AdbServer adbServer) {
    GetDeviceSpecCommand.Builder builder =
        builder().setAdbServer(adbServer).setOutputPath(OUTPUT_FLAG.getRequiredValue(flags));

    Optional<String> deviceSerialName = DEVICE_ID_FLAG.getValue(flags);
    if (!deviceSerialName.isPresent()) {
      deviceSerialName = systemEnvironmentProvider.getVariable(ANDROID_SERIAL_VARIABLE);
    }
    deviceSerialName.ifPresent(builder::setDeviceId);

    Path adbPath =
        ADB_PATH_FLAG
            .getValue(flags)
            .orElseGet(
                () ->
                    systemEnvironmentProvider
                        .getVariable(ANDROID_HOME_VARIABLE)
                        .flatMap(path -> new SdkToolsLocator().locateAdb(Paths.get(path)))
                        .orElseThrow(
                            () ->
                                new CommandExecutionException(
                                    "Unable to determine the location of ADB. Please set the --adb "
                                        + "flag or define ANDROID_HOME environment variable.")));
    builder.setAdbPath(adbPath);

    OVERWRITE_OUTPUT_FLAG.getValue(flags).ifPresent(builder::setOverwriteOutput);
    flags.checkNoUnknownFlags();

    return builder.build();
  }

  public DeviceSpec execute() {
    if (!getOverwriteOutput()) {
      FilePreconditions.checkFileDoesNotExist(getOutputPath());
    }

    Path pathToAdb = getAdbPath();
    FilePreconditions.checkFileExistsAndExecutable(pathToAdb);

    AdbServer adb = getAdbServer();
    adb.init(getAdbPath());
    DeviceSpec deviceSpec = new DeviceAnalyzer(adb).getDeviceSpec(getDeviceId());
    writeDeviceSpecToFile(deviceSpec, getOutputPath());
    return deviceSpec;
  }

  private void writeDeviceSpecToFile(DeviceSpec deviceSpec, Path outputFile) {
    try {
      if (getOverwriteOutput()) {
        Files.deleteIfExists(getOutputPath());
      }
      Files.write(outputFile, JsonFormat.printer().print(deviceSpec).getBytes(UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while writing the output file '%s'.", outputFile), e);
    }
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Writes out a JSON file containing the device specifications (i.e. features "
                        + "and properties) of the connected Android device.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(OUTPUT_FLAG.getName())
                .setExampleValue("device-spec.json")
                .setDescription(
                    "Path to the output device spec file. Must have the .json extension.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ADB_PATH_FLAG.getName())
                .setExampleValue("path/to/adb")
                .setOptional(true)
                .setDescription(
                    "Path to the adb utility. If absent, an attempt will be made to locate it if "
                        + "the %s environment variable is set.",
                    ANDROID_HOME_VARIABLE)
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
                .setFlagName(OVERWRITE_OUTPUT_FLAG.getName())
                .setOptional(true)
                .setDescription("If set, any previous existing output will be overwritten.")
                .build())
        .build();
  }
}

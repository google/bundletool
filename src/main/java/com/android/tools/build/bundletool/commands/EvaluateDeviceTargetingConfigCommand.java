/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.tools.build.bundletool.device.DeviceTargetingConfigEvaluator.getMatchingCountrySet;
import static com.android.tools.build.bundletool.device.DeviceTargetingConfigEvaluator.getMatchingDeviceGroups;
import static com.android.tools.build.bundletool.device.DeviceTargetingConfigEvaluator.getSelectedDeviceTier;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.ANDROID_HOME_VARIABLE;
import static com.android.tools.build.bundletool.model.utils.SdkToolsLocator.SYSTEM_PATH_VARIABLE;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceId;
import com.android.bundle.DeviceProperties;
import com.android.bundle.DeviceTier;
import com.android.bundle.DeviceTierConfig;
import com.android.bundle.SystemFeature;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.device.AdbServer;
import com.android.tools.build.bundletool.device.AdbShellCommandTask;
import com.android.tools.build.bundletool.device.Device;
import com.android.tools.build.bundletool.device.DeviceAnalyzer;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.exceptions.InvalidCommandException;
import com.android.tools.build.bundletool.model.utils.DefaultSystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.model.utils.files.FilePreconditions;
import com.android.tools.build.bundletool.validation.DeviceTierConfigValidator;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Command to evaluate which groups and tier a specific device would fall into, in a provided device
 * targeting config.
 */
@AutoValue
public abstract class EvaluateDeviceTargetingConfigCommand {

  public static final String COMMAND_NAME = "evaluate-device-targeting-config";

  private static final Flag<Path> DEVICE_TARGETING_CONFIGURATION_LOCATION_FLAG =
      Flag.path("config");

  private static final Flag<Path> DEVICE_PROPERTIES_LOCATION_FLAG = Flag.path("device-properties");

  private static final Flag<Path> ADB_PATH_FLAG = Flag.path("adb");

  private static final Flag<Boolean> CONNECTED_DEVICE_FLAG = Flag.booleanFlag("connected-device");

  private static final Flag<String> DEVICE_ID_FLAG = Flag.string("device-id");

  private static final Flag<String> COUNTRY_CODE_FLAG = Flag.string("country-code");

  private static final SystemEnvironmentProvider DEFAULT_PROVIDER =
      new DefaultSystemEnvironmentProvider();

  private static final String BRAND_NAME = "ro.product.brand";

  private static final String DEVICE_NAME = "ro.product.device";

  private static final String GET_MEMORY_SHELL_COMMAND =
      "cat /proc/meminfo | grep -i 'MemTotal' | grep -oE '[0-9]+'";

  private static final int FROM_KIB_TO_BYTES = 1024;

  abstract Optional<AdbServer> getAdbServer();

  abstract Path getDeviceTargetingConfigurationPath();

  abstract Optional<Path> getDevicePropertiesPath();

  abstract Optional<Boolean> getConnectedDeviceMode();

  public abstract Optional<String> getDeviceId();

  public abstract Optional<String> getCountryCode();

  abstract Optional<Path> getAdbPath();

  static Builder builder() {
    return new AutoValue_EvaluateDeviceTargetingConfigCommand.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setDeviceTargetingConfigurationPath(Path deviceTargetingConfigurationPath);

    abstract Builder setDevicePropertiesPath(Path devicePropertiesPath);

    abstract Builder setConnectedDeviceMode(boolean enabled);

    abstract Builder setAdbServer(AdbServer adbServer);

    abstract Builder setDeviceId(Optional<String> id);

    abstract Builder setCountryCode(String countryCode);

    abstract EvaluateDeviceTargetingConfigCommand build();

    abstract Builder setAdbPath(Path adbPath);
  }

  private static void validateFlags(ParsedFlags flags) {
    boolean hasConnectedDevice = CONNECTED_DEVICE_FLAG.getValue(flags).orElse(false);
    boolean hasDeviceProperties = DEVICE_PROPERTIES_LOCATION_FLAG.getValue(flags).isPresent();
    boolean hasAdbPath = ADB_PATH_FLAG.getValue(flags).isPresent();
    boolean hasDeviceId = DEVICE_ID_FLAG.getValue(flags).isPresent();

    if (hasDeviceProperties && hasConnectedDevice) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "Conflicting options: '--%s' and '--%s' cannot be present together.",
              CONNECTED_DEVICE_FLAG.getName(), DEVICE_PROPERTIES_LOCATION_FLAG.getName())
          .build();
    }

    if (!hasConnectedDevice && !hasDeviceProperties) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "Missing required flag: Either '--%s' or '--%s' must be specified.",
              CONNECTED_DEVICE_FLAG.getName(), DEVICE_PROPERTIES_LOCATION_FLAG.getName())
          .build();
    }

    if (hasAdbPath && !hasConnectedDevice) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "Adb path can only be used with '--%s'", CONNECTED_DEVICE_FLAG.getName())
          .build();
    }

    if (hasDeviceId && !hasConnectedDevice) {
      throw InvalidCommandException.builder()
          .withInternalMessage(
              "Device id can only be used with '--%s'", CONNECTED_DEVICE_FLAG.getName())
          .build();
    }
  }

  public static EvaluateDeviceTargetingConfigCommand fromFlags(
      ParsedFlags flags, AdbServer adbServer) {
    validateFlags(flags);

    Builder evaluateDeviceTargetingConfigCommandBuilder =
        builder()
            .setDeviceTargetingConfigurationPath(
                DEVICE_TARGETING_CONFIGURATION_LOCATION_FLAG.getRequiredValue(flags));

    DEVICE_PROPERTIES_LOCATION_FLAG
        .getValue(flags)
        .ifPresent(evaluateDeviceTargetingConfigCommandBuilder::setDevicePropertiesPath);

    if (CONNECTED_DEVICE_FLAG.getValue(flags).isPresent()) {
      Path adbPath = CommandUtils.getAdbPath(flags, ADB_PATH_FLAG, DEFAULT_PROVIDER);
      evaluateDeviceTargetingConfigCommandBuilder
          .setAdbPath(adbPath)
          .setAdbServer(adbServer)
          .setConnectedDeviceMode(true)
          .setDeviceId(DEVICE_ID_FLAG.getValue(flags));
    }
    COUNTRY_CODE_FLAG
        .getValue(flags)
        .ifPresent(evaluateDeviceTargetingConfigCommandBuilder::setCountryCode);

    return evaluateDeviceTargetingConfigCommandBuilder.build();
  }

  public void execute(PrintStream out) throws IOException, TimeoutException {
    try (Reader configReader = BufferedIo.reader(getDeviceTargetingConfigurationPath())) {
      DeviceTierConfig.Builder configBuilder = DeviceTierConfig.newBuilder();
      JsonFormat.parser().merge(configReader, configBuilder);
      DeviceTierConfig config = configBuilder.build();

      DeviceTierConfigValidator.validateDeviceTierConfig(config);
      if (getCountryCode().isPresent()) {
        DeviceTierConfigValidator.validateCountryCode(getCountryCode().get());
      }

      DeviceProperties.Builder devicePropertiesBuilder = DeviceProperties.newBuilder();
      if (this.getDevicePropertiesPath().isPresent()) {
        try (Reader devicePropertiesReader = BufferedIo.reader(getDevicePropertiesPath().get())) {
          JsonFormat.parser().merge(devicePropertiesReader, devicePropertiesBuilder);
        }
      } else {
        devicePropertiesBuilder = getDevicePropertiesFromConnectedDevice();
      }

      DeviceProperties deviceProperties = devicePropertiesBuilder.build();

      printTier(getSelectedDeviceTier(config, deviceProperties), out);
      printGroups(getMatchingDeviceGroups(config, deviceProperties), out);
      if (getCountryCode().isPresent()) {
        printCountrySet(getMatchingCountrySet(config, getCountryCode().get()), out);
      }
    }
  }

  private DeviceProperties.Builder getDevicePropertiesFromConnectedDevice()
      throws TimeoutException {
    Path pathToAdb = getAdbPath().get();
    FilePreconditions.checkFileExistsAndExecutable(pathToAdb);

    AdbServer adb = getAdbServer().get();
    adb.init(pathToAdb);
    Device device = new DeviceAnalyzer(adb).getAndValidateDevice(getDeviceId());
    return DeviceProperties.newBuilder()
        .setDeviceId(
            DeviceId.newBuilder()
                .setBuildBrand(device.getProperty(BRAND_NAME).orElse(""))
                .setBuildDevice(device.getProperty(DEVICE_NAME).orElse(""))
                .build())
        .addAllSystemFeatures(
            device.getDeviceFeatures().stream()
                .map(feature -> SystemFeature.newBuilder().setName(feature).build())
                .collect(toImmutableList()))
        .setRam(
            Long.parseLong(
                    new AdbShellCommandTask(device, GET_MEMORY_SHELL_COMMAND).execute().get(0))
                * FROM_KIB_TO_BYTES);
  }

  private void printTier(Optional<DeviceTier> selectedTier, PrintStream out) {
    if (selectedTier.isPresent()) {
      out.println("Tier: " + selectedTier.get().getLevel());
    } else {
      out.println("Tier: 0 (default)");
    }
  }

  private void printGroups(ImmutableSet<DeviceGroup> deviceGroups, PrintStream out) {
    if (deviceGroups.isEmpty()) {
      out.println("Groups:");
    } else {
      out.println(
          "Groups: '"
              + deviceGroups.stream().map(DeviceGroup::getName).collect(joining("', '"))
              + "'");
    }
  }

  private void printCountrySet(String countrySet, PrintStream out) {
    out.println("Country Set: '" + countrySet + "'");
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Evaluates which groups and tier a specific device would fall into, in a"
                        + " provided device targeting config or a connected device.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_TARGETING_CONFIGURATION_LOCATION_FLAG.getName())
                .setExampleValue("path/to/targeting/config.json")
                .setDescription("Path to device targeting configuration JSON file.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_PROPERTIES_LOCATION_FLAG.getName())
                .setExampleValue("path/to/device_properties.json")
                .setDescription(
                    "Path to a JSON representation of a specific device.  Cannot coexist with '%s'",
                    CONNECTED_DEVICE_FLAG.getName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(CONNECTED_DEVICE_FLAG.getName())
                .setDescription(
                    "If set, group and tier evaluation will be done for the connected device."
                        + " Cannot coexist with '%s'.",
                    DEVICE_PROPERTIES_LOCATION_FLAG.getName())
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(ADB_PATH_FLAG.getName())
                .setExampleValue("path/to/adb")
                .setOptional(true)
                .setDescription(
                    "Path to the adb utility. If absent, an attempt will be made to locate it if "
                        + "the %s or %s environment variable is set. Used only if %s flag is set.",
                    ANDROID_HOME_VARIABLE, SYSTEM_PATH_VARIABLE, CONNECTED_DEVICE_FLAG)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_ID_FLAG.getName())
                .setExampleValue("device-serial-name")
                .setOptional(true)
                .setDescription(
                    "Device serial name. If absent, this uses the %s environment variable. Either "
                        + "this flag or the environment variable is required when more than one "
                        + "device or emulator is connected. Used only if %s flag is set.",
                    ANDROID_SERIAL_VARIABLE, CONNECTED_DEVICE_FLAG)
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(COUNTRY_CODE_FLAG.getName())
                .setExampleValue("VN")
                .setOptional(true)
                .setDescription(
                    "An ISO 3166 alpha-2 format country code for the country of user account on the"
                        + " device. This will be used to derive corresponding country set from"
                        + " device targeting configuration.")
                .build())
        .build();
  }
}

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

import static com.android.tools.build.bundletool.device.DeviceTargetingConfigEvaluator.getMatchingDeviceGroups;
import static com.android.tools.build.bundletool.device.DeviceTargetingConfigEvaluator.getSelectedDeviceTier;
import static java.util.stream.Collectors.joining;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceProperties;
import com.android.bundle.DeviceTier;
import com.android.bundle.DeviceTierConfig;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.validation.DeviceTierConfigValidator;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Optional;

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

  abstract Path getDeviceTargetingConfigurationPath();

  abstract Path getDevicePropertiesPath();

  static Builder builder() {
    return new AutoValue_EvaluateDeviceTargetingConfigCommand.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setDeviceTargetingConfigurationPath(Path deviceTargetingConfigurationPath);

    abstract Builder setDevicePropertiesPath(Path devicePropertiesPath);

    abstract EvaluateDeviceTargetingConfigCommand build();
  }

  public static EvaluateDeviceTargetingConfigCommand fromFlags(ParsedFlags flags) {
    return builder()
        .setDeviceTargetingConfigurationPath(
            DEVICE_TARGETING_CONFIGURATION_LOCATION_FLAG.getRequiredValue(flags))
        .setDevicePropertiesPath(DEVICE_PROPERTIES_LOCATION_FLAG.getRequiredValue(flags))
        .build();
  }

  public void execute(PrintStream out) throws IOException {
    try (Reader configReader = BufferedIo.reader(getDeviceTargetingConfigurationPath());
        Reader devicePropertiesReader = BufferedIo.reader(getDevicePropertiesPath())) {
      DeviceTierConfig.Builder configBuilder = DeviceTierConfig.newBuilder();
      JsonFormat.parser().merge(configReader, configBuilder);
      DeviceTierConfig config = configBuilder.build();

      DeviceTierConfigValidator.validateDeviceTierConfig(config);

      DeviceProperties.Builder devicePropertiesBuilder = DeviceProperties.newBuilder();
      JsonFormat.parser().merge(devicePropertiesReader, devicePropertiesBuilder);
      DeviceProperties deviceProperties = devicePropertiesBuilder.build();

      printTier(getSelectedDeviceTier(config, deviceProperties), out);
      printGroups(getMatchingDeviceGroups(config, deviceProperties), out);
    }
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

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Evaluates which groups and tier a specific device would fall into, in a"
                        + " provided device targeting config.")
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
                .setDescription("Path to a JSON representation of a specific device.")
                .build())
        .build();
  }
}

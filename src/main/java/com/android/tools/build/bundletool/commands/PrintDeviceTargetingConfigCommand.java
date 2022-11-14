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

import static com.android.tools.build.bundletool.device.DeviceTargetingConfigEvaluator.getDeviceGroupNameToDeviceGroupMap;
import static com.android.tools.build.bundletool.device.DeviceTargetingConfigEvaluator.getDeviceSelectorsInTier;
import static com.android.tools.build.bundletool.device.DeviceTargetingConfigEvaluator.getSortedDeviceTiers;
import static java.lang.Math.pow;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceSelector;
import com.android.bundle.DeviceTier;
import com.android.bundle.DeviceTierConfig;
import com.android.bundle.SystemFeature;
import com.android.bundle.UserCountrySet;
import com.android.tools.build.bundletool.commands.CommandHelp.CommandDescription;
import com.android.tools.build.bundletool.commands.CommandHelp.FlagDescription;
import com.android.tools.build.bundletool.flags.Flag;
import com.android.tools.build.bundletool.flags.ParsedFlags;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.android.tools.build.bundletool.validation.DeviceTierConfigValidator;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/** Command to "pretty-print" a device targeting configuration JSON file. */
@AutoValue
public abstract class PrintDeviceTargetingConfigCommand {

  public static final String COMMAND_NAME = "print-device-targeting-config";

  private static final String INDENT = "  ";

  private static final Flag<Path> DEVICE_TARGETING_CONFIGURATION_LOCATION_FLAG =
      Flag.path("config");

  private static final int MAX_COUNTRY_CODES_ON_A_LINE = 10;

  abstract Path getDeviceTargetingConfigurationPath();

  abstract PrintStream getOutputStream();

  static Builder builder() {
    return new AutoValue_PrintDeviceTargetingConfigCommand.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setDeviceTargetingConfigurationPath(Path deviceTargetingConfigurationPath);

    abstract Builder setOutputStream(PrintStream out);

    abstract PrintDeviceTargetingConfigCommand build();
  }

  public static PrintDeviceTargetingConfigCommand fromFlags(ParsedFlags flags) {
    return fromFlags(flags, System.out);
  }

  public static PrintDeviceTargetingConfigCommand fromFlags(ParsedFlags flags, PrintStream out) {
    return builder()
        .setDeviceTargetingConfigurationPath(
            DEVICE_TARGETING_CONFIGURATION_LOCATION_FLAG.getRequiredValue(flags))
        .setOutputStream(out)
        .build();
  }

  public void execute() throws IOException {
    try (Reader configReader = BufferedIo.reader(getDeviceTargetingConfigurationPath())) {
      DeviceTierConfig.Builder configBuilder = DeviceTierConfig.newBuilder();
      JsonFormat.parser().merge(configReader, configBuilder);
      DeviceTierConfig config = configBuilder.build();

      DeviceTierConfigValidator.validateDeviceTierConfig(config);

      for (DeviceGroup deviceGroup : config.getDeviceGroupsList()) {
        printDeviceGroup(deviceGroup, INDENT);
      }

      printDeviceTierSet(config);
      config.getUserCountrySetsList().forEach(countrySet -> printCountrySet(countrySet, ""));
    }
  }

  private void printDeviceGroup(DeviceGroup deviceGroup, String indent) {
    getOutputStream().println("Group '" + deviceGroup.getName() + "':");
    printSetOfDeviceSelectors(ImmutableSet.copyOf(deviceGroup.getDeviceSelectorsList()), indent);
    getOutputStream().println();
  }

  private void printDeviceTierSet(DeviceTierConfig config) {
    ImmutableMap<String, DeviceGroup> deviceGroupNameToDeviceGroup =
        getDeviceGroupNameToDeviceGroupMap(config);
    ImmutableList<DeviceTier> deviceTiers = getSortedDeviceTiers(config);
    ImmutableSet<DeviceSelector> excludedSelectorsFromHigherTiers = ImmutableSet.of();

    for (DeviceTier deviceTier : deviceTiers) {
      getOutputStream().println("Tier " + deviceTier.getLevel() + ":");
      ImmutableSet<DeviceSelector> selectorsFromGroupsInTier =
          getDeviceSelectorsInTier(deviceTier, deviceGroupNameToDeviceGroup);
      printDeviceTier(selectorsFromGroupsInTier, excludedSelectorsFromHigherTiers, INDENT);
      excludedSelectorsFromHigherTiers =
          ImmutableSet.<DeviceSelector>builder()
              .addAll(selectorsFromGroupsInTier)
              .addAll(excludedSelectorsFromHigherTiers)
              .build();
    }
    if (!excludedSelectorsFromHigherTiers.isEmpty()) {
      getOutputStream().println("Tier 0 (default):");
      printDeviceTier(
          /* selectorsFromGroupsInTier= */ ImmutableSet.of(),
          excludedSelectorsFromHigherTiers,
          INDENT);
    }
  }

  private void printCountrySet(UserCountrySet countrySet, String indent) {
    getOutputStream().println(indent + "Country set '" + countrySet.getName() + "':");
    getOutputStream().println(indent + INDENT + "(");
    getOutputStream().println(indent + INDENT + INDENT + "Country Codes: [");
    printCountryCodes(countrySet.getCountryCodesList(), indent + INDENT + INDENT + INDENT);
    getOutputStream().println(indent + INDENT + INDENT + "]");
    getOutputStream().println(indent + INDENT + ")");
    getOutputStream().println();
  }

  private void printCountryCodes(List<String> countryCodes, String indent) {
    List<List<String>> partitionedCountryCodes =
        Lists.partition(countryCodes, MAX_COUNTRY_CODES_ON_A_LINE);
    partitionedCountryCodes.forEach(
        countryCodesOnThisLine ->
            getOutputStream().println(indent + String.join(", ", countryCodesOnThisLine)));
  }

  private void printDeviceTier(
      ImmutableSet<DeviceSelector> selectorsFromGroupsInTier,
      ImmutableSet<DeviceSelector> excludedSelectorsFromHigherTiers,
      String indent) {
    if (selectorsFromGroupsInTier.isEmpty()) {
      getOutputStream().println(indent + "NOT (");
      printSetOfDeviceSelectors(excludedSelectorsFromHigherTiers, indent + INDENT);
    } else {
      getOutputStream().println(indent + "(");
      printSetOfDeviceSelectors(selectorsFromGroupsInTier, indent + INDENT);
      if (!excludedSelectorsFromHigherTiers.isEmpty()) {
        getOutputStream().println(indent + ") AND NOT (");
        printSetOfDeviceSelectors(excludedSelectorsFromHigherTiers, indent + INDENT);
      }
    }
    getOutputStream().println(indent + ")");
    getOutputStream().println();
  }

  private void printSetOfDeviceSelectors(
      ImmutableSet<DeviceSelector> deviceSelectors, String indent) {
    getOutputStream().println(indent + "(");
    boolean isFirstSelector = true;
    for (DeviceSelector deviceSelector : deviceSelectors) {
      if (!isFirstSelector) {
        getOutputStream().println(indent + ") OR (");
      }
      isFirstSelector = false;
      printDeviceSelector(deviceSelector, indent + INDENT);
    }
    getOutputStream().println(indent + ")");
  }

  private void printDeviceSelector(DeviceSelector deviceSelector, String indent) {
    boolean isFirstSelectorRule = true;
    isFirstSelectorRule = printRamRule(deviceSelector, isFirstSelectorRule, indent);
    isFirstSelectorRule =
        printListRule(
            /* operator= */ "device IN",
            deviceSelector.getIncludedDeviceIdsList(),
            deviceId -> deviceId.getBuildBrand() + " " + deviceId.getBuildDevice(),
            isFirstSelectorRule,
            indent);
    isFirstSelectorRule =
        printListRule(
            /* operator= */ "device NOT IN",
            deviceSelector.getExcludedDeviceIdsList(),
            deviceId -> deviceId.getBuildBrand() + " " + deviceId.getBuildDevice(),
            isFirstSelectorRule,
            indent);
    isFirstSelectorRule =
        printListRule(
            /* operator= */ "device HAS ALL FEATURES IN",
            deviceSelector.getRequiredSystemFeaturesList(),
            SystemFeature::getName,
            isFirstSelectorRule,
            indent);
    printListRule(
        /* operator= */ "device HAS NO FEATURES IN",
        deviceSelector.getForbiddenSystemFeaturesList(),
        SystemFeature::getName,
        isFirstSelectorRule,
        indent);
  }

  /**
   * Prints the conditions on the device's RAM, if specified.
   *
   * @return whether any selector rules have been printed for the given {@link DeviceSelector} once
   *     this method has executed.
   */
  private boolean printRamRule(
      DeviceSelector deviceSelector, boolean isFirstSelectorRule, String indent) {
    long minBytes = deviceSelector.getDeviceRam().getMinBytes();
    long maxBytes = deviceSelector.getDeviceRam().getMaxBytes();
    boolean isRuleEmpty = minBytes == 0 && maxBytes == 0;
    if (isRuleEmpty) {
      return isFirstSelectorRule;
    }

    printAnd(indent + INDENT, isFirstSelectorRule);

    if (minBytes != 0 && maxBytes != 0) {
      getOutputStream()
          .println(
              indent
                  + bytesToReadableString(minBytes)
                  + " <= RAM < "
                  + bytesToReadableString(maxBytes));
    } else if (minBytes != 0) {
      getOutputStream().println(indent + "RAM >= " + bytesToReadableString(minBytes));
    } else {
      getOutputStream().println(indent + "RAM < " + bytesToReadableString(maxBytes));
    }
    return false;
  }

  private String bytesToReadableString(long bytes) {
    double bytesDouble = (double) bytes;
    double gigaByte = pow(1024, 3);
    double megaByte = pow(1024, 2);
    double kiloByte = 1024;
    if (bytesDouble >= gigaByte) {
      return String.format("%.2f GB", bytesDouble / gigaByte);
    }
    if (bytesDouble >= megaByte) {
      return String.format("%.2f MB", bytesDouble / megaByte);
    }
    if (bytesDouble >= kiloByte) {
      return String.format("%.2f KB", bytesDouble / kiloByte);
    }
    return bytes + " B";
  }

  /**
   * Prints a rule that is defined by a list.
   *
   * @return whether any selector rules have been printed for the given {@link DeviceSelector} once
   *     this method has executed.
   */
  private <T> boolean printListRule(
      String operator,
      List<T> operandList,
      Function<T, String> operandToString,
      boolean isFirstSelectorRule,
      String indent) {
    if (operandList.isEmpty()) {
      return isFirstSelectorRule;
    }
    printAnd(indent + INDENT, isFirstSelectorRule);
    getOutputStream()
        .println(indent + operator + " (" + formatOperandList(operandList, operandToString) + ")");
    return false;
  }

  private void printAnd(String indent, boolean isFirstSelectorRule) {
    if (!isFirstSelectorRule) {
      getOutputStream().println(indent + "AND");
    }
  }

  private <T> String formatOperandList(List<T> operandList, Function<T, String> operandToString) {
    if (operandList.size() > 3) {
      return formatOperandListWithEllipsis(operandList, operandToString);
    }
    StringBuilder formattedOperandList = new StringBuilder();
    for (T operand : operandList) {
      if (formattedOperandList.length() > 0) {
        formattedOperandList.append(", ");
      }
      formattedOperandList.append("'").append(operandToString.apply(operand)).append("'");
    }
    return formattedOperandList.toString();
  }

  private <T> String formatOperandListWithEllipsis(
      List<T> operandList, Function<T, String> operandToString) {
    return operandToString.apply(operandList.get(0))
        + ", "
        + operandToString.apply(operandList.get(1))
        + ", ..., "
        + operandToString.apply(Iterables.getLast(operandList));
  }

  public static CommandHelp help() {
    return CommandHelp.builder()
        .setCommandName(COMMAND_NAME)
        .setCommandDescription(
            CommandDescription.builder()
                .setShortDescription(
                    "Prints a device targeting configuration JSON file in a human-readable format.")
                .build())
        .addFlag(
            FlagDescription.builder()
                .setFlagName(DEVICE_TARGETING_CONFIGURATION_LOCATION_FLAG.getName())
                .setExampleValue("path/to/targeting/config.json")
                .setDescription("Path to device targeting configuration JSON file.")
                .build())
        .build();
  }
}

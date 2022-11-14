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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingByDeterministic;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Collections.max;
import static java.util.Collections.min;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceSelector;
import com.android.bundle.DeviceTier;
import com.android.bundle.DeviceTierConfig;
import com.android.bundle.DeviceTierSet;
import com.android.bundle.UserCountrySet;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Validates the contents of a DeviceTierConfig. */
public class DeviceTierConfigValidator {

  private static final String COUNTRY_SET_NAME_REGEX_STRING = "^[a-zA-Z][a-zA-Z0-9_]*$";
  private static final String COUNTRY_CODE_REGEX_STRING = "^[A-Z]{2}$";
  private static final Pattern COUNTRY_SET_NAME_REGEX =
      Pattern.compile(COUNTRY_SET_NAME_REGEX_STRING);
  private static final Pattern COUNTRY_CODE_REGEX = Pattern.compile(COUNTRY_CODE_REGEX_STRING);

  private DeviceTierConfigValidator() {}

  /** Validates the DeviceGroups, DeviceTierSet and UserCountrySets of a DeviceTierConfig. */
  public static void validateDeviceTierConfig(DeviceTierConfig deviceTierConfig) {
    if (deviceTierConfig.getUserCountrySetsList().isEmpty()
        && deviceTierConfig.getDeviceGroupsList().isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "The device tier config must contain at least one group or user country set.")
          .build();
    }

    validateUserCountrySets(deviceTierConfig.getUserCountrySetsList());
    validateGroups(deviceTierConfig.getDeviceGroupsList());
    validateTiers(deviceTierConfig.getDeviceTierSet(), deviceTierConfig.getDeviceGroupsList());
  }

  /**
   * Validates the given country code. Checks that the given country code matches the regex
   * '^[A-Z]{2}$'.
   */
  public static void validateCountryCode(String countryCode) {
    if (!COUNTRY_CODE_REGEX.matcher(countryCode).matches()) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Country code should match the regex '%s', but found '%s'.",
              COUNTRY_CODE_REGEX, countryCode)
          .build();
    }
  }

  private static void validateUserCountrySets(List<UserCountrySet> userCountrySets) {
    userCountrySets.forEach(DeviceTierConfigValidator::validateUserCountrySet);
    validateCountrySetNamesAreUnique(userCountrySets);
    validateCountryCodesAreUnique(userCountrySets);
  }

  private static void validateCountrySetNamesAreUnique(List<UserCountrySet> userCountrySets) {
    ImmutableSet<String> duplicateNames =
        userCountrySets.stream()
            .map(UserCountrySet::getName)
            .collect(groupingByDeterministic(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(e -> e.getKey())
            .collect(toImmutableSet());

    if (!duplicateNames.isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Country set names should be unique. Found multiple country sets with these names:"
                  + " %s.",
              duplicateNames)
          .build();
    }
  }

  private static void validateCountryCodesAreUnique(List<UserCountrySet> userCountrySets) {
    ImmutableSet<String> duplicateCountryCodes =
        userCountrySets.stream()
            .flatMap(userCountrySet -> userCountrySet.getCountryCodesList().stream())
            .collect(groupingByDeterministic(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(e -> e.getKey())
            .collect(toImmutableSet());

    if (!duplicateCountryCodes.isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "A country code can belong to only one country set. Found multiple occurrences of"
                  + " these country codes: %s.",
              duplicateCountryCodes.toString())
          .build();
    }
  }

  private static void validateUserCountrySet(UserCountrySet userCountrySet) {
    validateCountrySetName(userCountrySet.getName());

    if (userCountrySet.getCountryCodesList().isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Country set '%s' must specify at least one country code.", userCountrySet.getName())
          .build();
    }

    userCountrySet.getCountryCodesList().forEach(DeviceTierConfigValidator::validateCountryCode);
  }

  private static void validateCountrySetName(String countrySetName) {
    if (countrySetName.isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Country Sets must specify a name.")
          .build();
    }

    if (!COUNTRY_SET_NAME_REGEX.matcher(countrySetName).matches()) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Country set name should match the regex '%s', but found '%s'.",
              COUNTRY_SET_NAME_REGEX_STRING, countrySetName)
          .build();
    }
  }

  private static void validateGroups(List<DeviceGroup> deviceGroups) {
    for (DeviceGroup deviceGroup : deviceGroups) {
      if (deviceGroup.getName().isEmpty()) {
        throw CommandExecutionException.builder()
            .withInternalMessage("Device groups must specify a name.")
            .build();
      }

      validateDeviceSelectors(deviceGroup.getDeviceSelectorsList(), deviceGroup.getName());
    }
  }

  private static void validateDeviceSelectors(
      List<DeviceSelector> deviceSelectors, String groupName) {
    if (deviceSelectors.isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage("Device group '%s' must specify at least one selector.", groupName)
          .build();
    }
  }

  private static void validateTiers(DeviceTierSet deviceTierSet, List<DeviceGroup> deviceGroups) {
    if (deviceTierSet.getDeviceTiersCount() == 0) {
      return;
    }

    ImmutableSet<String> deviceGroupNames =
        deviceGroups.stream().map(DeviceGroup::getName).collect(toImmutableSet());

    validateTierLevels(deviceTierSet);

    for (DeviceTier deviceTier : deviceTierSet.getDeviceTiersList()) {
      if (deviceTier.getDeviceGroupNamesList().isEmpty()) {
        throw CommandExecutionException.builder()
            .withInternalMessage("Tier %d must specify at least one group", deviceTier.getLevel())
            .build();
      }

      for (String deviceGroupName : deviceTier.getDeviceGroupNamesList()) {
        if (!deviceGroupNames.contains(deviceGroupName)) {
          throw CommandExecutionException.builder()
              .withInternalMessage(
                  "Tier %d must specify existing groups, but found undefined group '%s'.",
                  deviceTier.getLevel(), deviceGroupName)
              .build();
        }
      }
    }
  }

  private static void validateTierLevels(DeviceTierSet deviceTierSet) {
    ImmutableListMultimap<Integer, DeviceTier> tiersByLevel =
        deviceTierSet.getDeviceTiersList().stream()
            .collect(toImmutableListMultimap(DeviceTier::getLevel, x -> x));
    int minTierLevel = min(tiersByLevel.keySet());
    int maxTierLevel = max(tiersByLevel.keySet());

    if (minTierLevel <= 0) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Each tier must specify a positive level, but found %d.", minTierLevel)
          .build();
    }

    for (int level = 1; level <= maxTierLevel; ++level) {
      if (!tiersByLevel.containsKey(level)) {
        throw CommandExecutionException.builder()
            .withInternalMessage(
                "Tier %d is undefined. You should define all tiers between 1 and your total number"
                    + " of tiers.",
                level)
            .build();
      }

      if (tiersByLevel.get(level).size() > 1) {
        throw CommandExecutionException.builder()
            .withInternalMessage(
                "Tier %d should be uniquely defined, but it was defined %d times.",
                level, tiersByLevel.get(level).size())
            .build();
      }
    }
  }
}

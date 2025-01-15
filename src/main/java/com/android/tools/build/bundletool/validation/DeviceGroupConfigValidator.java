/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceGroupConfig;
import com.android.bundle.DeviceSelector;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Validates a DeviceGroupConfig supplied as bundle metadata. */
public final class DeviceGroupConfigValidator extends SubValidator {

  private static final Pattern GROUP_NAME_REGEX = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

  @Override
  public void validateBundle(AppBundle bundle) {
    bundle.getDeviceGroupConfig().ifPresent(DeviceGroupConfigValidator::validate);
  }

  private static void validate(DeviceGroupConfig deviceGroupConfig) {
    if (deviceGroupConfig.getDeviceGroupsList().isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage("The device group config must contain at least one device group.")
          .build();
    }

    validateDeviceGroups(deviceGroupConfig.getDeviceGroupsList());
  }

  private static void validateDeviceGroups(List<DeviceGroup> deviceGroups) {
    deviceGroups.forEach(DeviceGroupConfigValidator::validateDeviceGroup);
    validateDeviceGroupNamesAreUnique(deviceGroups);
  }

  private static void validateDeviceGroupNamesAreUnique(List<DeviceGroup> deviceGroups) {
    ImmutableSet<String> duplicateNames =
        deviceGroups.stream()
            .map(DeviceGroup::getName)
            .collect(groupingByDeterministic(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(e -> e.getKey())
            .collect(toImmutableSet());

    if (!duplicateNames.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage("Found duplicated device group names %s.", duplicateNames)
          .build();
    }
  }

  private static void validateDeviceGroup(DeviceGroup deviceGroup) {
    validateDeviceGroupName(deviceGroup.getName());
    validateDeviceSelectors(deviceGroup.getDeviceSelectorsList(), deviceGroup.getName());
  }

  private static void validateDeviceGroupName(String deviceGroupName) {
    if (deviceGroupName.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage("Device groups must specify a name.")
          .build();
    }

    if (!GROUP_NAME_REGEX.matcher(deviceGroupName).matches()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Device group name should match the regex '%s', but found '%s'.",
              GROUP_NAME_REGEX, deviceGroupName)
          .build();
    }
  }

  private static void validateDeviceSelectors(
      List<DeviceSelector> deviceSelectors, String groupName) {
    if (deviceSelectors.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage("Device group '%s' must specify at least one selector.", groupName)
          .build();
    }
  }
}

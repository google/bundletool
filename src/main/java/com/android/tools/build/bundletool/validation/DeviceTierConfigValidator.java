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

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Collections.max;
import static java.util.Collections.min;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceSelector;
import com.android.bundle.DeviceTier;
import com.android.bundle.DeviceTierConfig;
import com.android.bundle.DeviceTierSet;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import java.util.List;

/** Validates the contents of a DeviceTierConfig. */
public class DeviceTierConfigValidator {

  private DeviceTierConfigValidator() {}

  /** Validates the DeviceGroups and DeviceTierSet of a DeviceTierConfig. */
  public static void validateDeviceTierConfig(DeviceTierConfig deviceTierConfig) {
    validateGroups(deviceTierConfig.getDeviceGroupsList());
    validateTiers(deviceTierConfig.getDeviceTierSet(), deviceTierConfig.getDeviceGroupsList());
  }

  private static void validateGroups(List<DeviceGroup> deviceGroups) {
    if (deviceGroups.isEmpty()) {
      throw CommandExecutionException.builder()
          .withInternalMessage("The device tier config must contain at least one group.")
          .build();
    }

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

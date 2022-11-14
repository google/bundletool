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

package com.android.tools.build.bundletool.device;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;

import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceId;
import com.android.bundle.DeviceProperties;
import com.android.bundle.DeviceRam;
import com.android.bundle.DeviceSelector;
import com.android.bundle.DeviceTier;
import com.android.bundle.DeviceTierConfig;
import com.android.bundle.UserCountrySet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;

/** Utility class to evaluate {@link DeviceTierConfig} and {@link DeviceProperties}. */
public class DeviceTargetingConfigEvaluator {

  private DeviceTargetingConfigEvaluator() {}

  /**
   * Gets the highest {@link DeviceTier} that matches the provided {@link DeviceProperties}. If none
   * of the defined tiers match, an empty optional will be returned, indicating that the device
   * belongs in the default tier.
   */
  public static Optional<DeviceTier> getSelectedDeviceTier(
      DeviceTierConfig config, DeviceProperties deviceProperties) {
    ImmutableList<DeviceTier> sortedDeviceTiers = getSortedDeviceTiers(config);
    ImmutableMap<String, DeviceGroup> deviceGroupNameToDeviceGroup =
        getDeviceGroupNameToDeviceGroupMap(config);

    return sortedDeviceTiers.stream()
        .filter(
            tier ->
                devicePropertiesMatchDeviceTier(
                    deviceProperties, tier, deviceGroupNameToDeviceGroup))
        .findFirst();
  }

  /** Sorts a list of {@link DeviceTier}s by level from highest to lowest. */
  public static ImmutableList<DeviceTier> getSortedDeviceTiers(DeviceTierConfig config) {
    return ImmutableList.sortedCopyOf(
        comparingInt(DeviceTier::getLevel).reversed(),
        config.getDeviceTierSet().getDeviceTiersList());
  }

  /** Creates a map from {@link DeviceGroup} name to {@link DeviceGroup}. */
  public static ImmutableMap<String, DeviceGroup> getDeviceGroupNameToDeviceGroupMap(
      DeviceTierConfig config) {
    return config.getDeviceGroupsList().stream()
        .collect(toImmutableMap(DeviceGroup::getName, identity()));
  }

  /** Gets the set of {@link DeviceGroup}s that match the provided {@link DeviceProperties}. */
  public static ImmutableSet<DeviceGroup> getMatchingDeviceGroups(
      DeviceTierConfig config, DeviceProperties deviceProperties) {
    return config.getDeviceGroupsList().stream()
        .filter(group -> devicePropertiesMatchDeviceGroup(deviceProperties, group))
        .collect(toImmutableSet());
  }

  /**
   * Returns the union of all {@link DeviceSelector}s specified by the {@link DeviceGroup}s in the
   * provided {@link DeviceTier}.
   */
  public static ImmutableSet<DeviceSelector> getDeviceSelectorsInTier(
      DeviceTier deviceTier, ImmutableMap<String, DeviceGroup> deviceGroupNameToDeviceGroup) {
    return deviceTier.getDeviceGroupNamesList().stream()
        .map(name -> deviceGroupNameToDeviceGroup.get(name).getDeviceSelectorsList())
        .flatMap(List::stream)
        .collect(toImmutableSet());
  }

  /** Get the {@link UserCountrySet} that matches the provided user country. */
  public static String getMatchingCountrySet(DeviceTierConfig config, String countryCode) {
    return config.getUserCountrySetsList().stream()
        .filter(countrySet -> countrySet.getCountryCodesList().contains(countryCode))
        .map(UserCountrySet::getName)
        .findAny()
        .orElse("");
  }

  private static boolean devicePropertiesMatchDeviceGroup(
      DeviceProperties deviceProperties, DeviceGroup deviceGroup) {
    return deviceGroup.getDeviceSelectorsList().stream()
        .anyMatch(selector -> devicePropertiesMatchDeviceSelector(deviceProperties, selector));
  }

  private static boolean devicePropertiesMatchDeviceTier(
      DeviceProperties deviceProperties,
      DeviceTier deviceTier,
      ImmutableMap<String, DeviceGroup> deviceGroupNameToDeviceGroup) {
    return getDeviceSelectorsInTier(deviceTier, deviceGroupNameToDeviceGroup).stream()
        .anyMatch(selector -> devicePropertiesMatchDeviceSelector(deviceProperties, selector));
  }

  private static boolean devicePropertiesMatchDeviceSelector(
      DeviceProperties deviceProperties, DeviceSelector deviceSelector) {
    return devicePropertiesMatchRamRule(deviceProperties, deviceSelector.getDeviceRam())
        && deviceIdInList(deviceProperties.getDeviceId(), deviceSelector.getIncludedDeviceIdsList())
        && !deviceSelector.getExcludedDeviceIdsList().contains(deviceProperties.getDeviceId())
        && deviceProperties
            .getSystemFeaturesList()
            .containsAll(deviceSelector.getRequiredSystemFeaturesList())
        && deviceSelector.getForbiddenSystemFeaturesList().stream()
            .noneMatch(deviceProperties.getSystemFeaturesList()::contains);
  }

  private static boolean devicePropertiesMatchRamRule(
      DeviceProperties deviceProperties, DeviceRam deviceRam) {
    long minBytes = deviceRam.getMinBytes();
    long maxBytes = deviceRam.getMaxBytes() == 0 ? Long.MAX_VALUE : deviceRam.getMaxBytes();
    return minBytes <= deviceProperties.getRam() && deviceProperties.getRam() < maxBytes;
  }

  private static boolean deviceIdInList(DeviceId deviceId, List<DeviceId> deviceIdList) {
    // If the deviceIdList is empty, it means that the targeting configuration is not specifying any
    // DeviceId in particular. This means that any DeviceId provided satisfies the
    // included_device_ids rule in the DeviceSelector. Hence, we return true in this case.
    if (deviceIdList.isEmpty()) {
      return true;
    }
    return deviceIdList.contains(deviceId);
  }
}

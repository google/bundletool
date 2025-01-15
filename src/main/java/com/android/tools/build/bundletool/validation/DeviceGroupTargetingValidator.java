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

import static com.android.bundle.Config.SplitDimension.Value.DEVICE_GROUP;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractAssetsTargetedDirectories;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractDeviceGroups;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Config.SplitDimension;
import com.android.bundle.Config.SuffixStripping;
import com.android.bundle.DeviceGroup;
import com.android.bundle.DeviceGroupConfig;
import com.android.bundle.Targeting.DeviceGroupModuleTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Validates that all device groups are defined in the device group config. */
public class DeviceGroupTargetingValidator extends SubValidator {

  private static final ImmutableSet<String> NO_GROUPS = ImmutableSet.of();
  private static final Logger logger = Logger.getLogger(DeviceGroupTargetingValidator.class.getName());

  @Override
  public void validateBundle(AppBundle bundle) {
    Optional<DeviceGroupConfig> deviceGroupConfig = bundle.getDeviceGroupConfig();
    ImmutableSet<String> configGroups =
        deviceGroupConfig
            .map(
                dtc ->
                    dtc.getDeviceGroupsList().stream()
                        .map(DeviceGroup::getName)
                        .collect(toImmutableSet()))
            .orElse(NO_GROUPS);

    // Check bundle config
    Optional<String> defaultDeviceGroup =
        bundle
            .getBundleConfig()
            .getOptimizations()
            .getSplitsConfig()
            .getSplitDimensionList()
            .stream()
            .filter(d -> d.getValue() == DEVICE_GROUP)
            .map(SplitDimension::getSuffixStripping)
            .map(SuffixStripping::getDefaultSuffix)
            .findFirst();
    if (defaultDeviceGroup.isPresent() && !configGroups.contains(defaultDeviceGroup.get())) {
      fail(
          "The bundle config specified a default ",
          ImmutableSet.of(defaultDeviceGroup.get()),
          configGroups,
          deviceGroupConfig);
    }

    // Check targeting
    for (BundleModule module : bundle.getModules().values()) {
      ImmutableSet<String> moduleAssetGroups =
          extractDeviceGroups(extractAssetsTargetedDirectories(module));
      DeviceGroupModuleTargeting moduleTargeting =
          module.getModuleMetadata().getTargeting().getDeviceGroupTargeting();

      ImmutableSet<String> undefinedGroups =
          Stream.concat(moduleAssetGroups.stream(), moduleTargeting.getValueList().stream())
              .filter(g -> !configGroups.contains(g))
              .collect(toImmutableSet());

      if (!undefinedGroups.isEmpty()) {
        fail(
            String.format("Module '%s' refers to ", module.getName()),
            undefinedGroups,
            configGroups,
            deviceGroupConfig);
      }
    }
  }

  private void fail(
      String prefix,
      ImmutableSet<String> badGroups,
      ImmutableSet<String> configGroups,
      Optional<DeviceGroupConfig> deviceGroupConfig) {
    String bad =
        badGroups.size() == 1
            ? String.format("device group %s which is not ", badGroups)
            : String.format("device groups %s which are not ", badGroups);
    String context = String.format("in the list %s defined in the bundle metadata.", configGroups);
    if (deviceGroupConfig.isPresent()) {
      throw InvalidBundleException.createWithUserMessage(prefix + bad + context);
    } else {
      logger.warning(
          prefix + bad + "defined. Please add DeviceGroupConfig.json to the bundle metadata.");
    }
  }
}

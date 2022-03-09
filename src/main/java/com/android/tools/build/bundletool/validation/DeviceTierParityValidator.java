/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractAssetsTargetedDirectories;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractDeviceTier;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractDeviceTiers;
import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static java.util.Comparator.naturalOrder;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;

/** Validates that all modules that use device tier targeting contain the same set of tiers. */
public class DeviceTierParityValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    BundleModule referenceModule = null;
    ImmutableSet<Integer> referenceTiers = null;
    for (BundleModule module : modules) {
      ImmutableSet<Integer> moduleTiers =
          extractDeviceTiers(extractAssetsTargetedDirectories(module));

      if (moduleTiers.isEmpty()) {
        continue;
      }

      validateContiguousTiers(module, moduleTiers);
      validateModuleDirectoryParity(module, moduleTiers);

      if (referenceTiers == null) {
        referenceModule = module;
        referenceTiers = moduleTiers;
      } else if (!referenceTiers.equals(moduleTiers)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "All modules with device tier targeting must support the same set of"
                    + " tiers, but module '%s' supports %s and module '%s' supports %s.",
                referenceModule.getName(), referenceTiers, module.getName(), moduleTiers)
            .build();
      }
    }
  }

  /** Validates that tiers used by a module are contiguous and start from 0. */
  private void validateContiguousTiers(BundleModule module, ImmutableSet<Integer> moduleTiers) {
    int minTier = moduleTiers.stream().min(naturalOrder()).get();
    int maxTier = moduleTiers.stream().max(naturalOrder()).get();

    if (minTier != 0 || maxTier != moduleTiers.size() - 1) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "All modules with device tier targeting must support the same contiguous"
                  + " range of tier values starting from 0, but module '%s' supports %s.",
              module.getName(), moduleTiers)
          .build();
    }
  }

  /** Validates that all targeted directories in a module have the same set of tiers. */
  private void validateModuleDirectoryParity(
      BundleModule module, ImmutableSet<Integer> moduleTiers) {
    ImmutableSetMultimap<String /* subpath */, Integer /* tier */> tiersPerDirectory =
        extractAssetsTargetedDirectories(module).stream()
            .filter(directory -> extractDeviceTier(directory).isPresent())
            .collect(
                toImmutableSetMultimap(
                    directory -> directory.getSubPathBaseName(TargetingDimension.DEVICE_TIER),
                    directory -> extractDeviceTier(directory).get()));

    for (String subpath : tiersPerDirectory.keySet()) {
      if (!tiersPerDirectory.get(subpath).equals(moduleTiers)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "All device-tier-targeted folders in a module must support the same set of"
                    + " tiers, but module '%s' supports %s and folder '%s' supports only %s.",
                module.getName(), moduleTiers, subpath, tiersPerDirectory.get(subpath))
            .build();
      }
    }
  }
}

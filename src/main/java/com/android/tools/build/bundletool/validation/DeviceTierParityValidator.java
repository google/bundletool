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
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.extractDeviceTiers;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

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
}

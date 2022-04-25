/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.ResourcesUtils.entries;
import static com.android.tools.build.bundletool.model.version.VersionGuardedFeature.MODULE_TITLE_VALIDATION_ENFORCED;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;

/** Validates module Titles for On Demand Modules */
public class ModuleTitleValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    checkModuleTitles(modules);
  }

  private static void checkModuleTitles(ImmutableList<BundleModule> modules) {
    if (BundleValidationUtils.isAssetOnlyBundle(modules)) {
      return;
    }

    BundleModule baseModule = BundleValidationUtils.expectBaseModule(modules);
    boolean isolatedSplits = baseModule.getAndroidManifest().getIsolatedSplits().orElse(false);

    // For bundles built using older versions we haven't strictly enforced module Title Validation.
    Version bundletoolVersion = baseModule.getBundletoolVersion();
    if (!MODULE_TITLE_VALIDATION_ENFORCED.enabledForVersion(bundletoolVersion)) {
      return;
    }
    ResourceTable table = baseModule.getResourceTable().orElse(ResourceTable.getDefaultInstance());

    ImmutableSet<Integer> stringResourceIds =
        entries(table)
            .filter(entry -> entry.getType().getName().equals("string"))
            .map(entry -> entry.getResourceId().getFullResourceId())
            .collect(toImmutableSet());

    for (BundleModule module : modules) {
      if (module.getModuleType().equals(ModuleType.ASSET_MODULE)) {
        if (module.getAndroidManifest().getTitleRefId().isPresent()) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "Module titles not supported in asset packs, but found in '%s'.",
                  module.getName())
              .build();
        }
      } else if (!module.getDeliveryType().equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)) {
        Optional<Integer> titleRefId = module.getAndroidManifest().getTitleRefId();
        if (isolatedSplits) {
          // Isolated splits may be built independently from the base split, and can't include
          // references into the base split resource table.
          return;
        }
        if (!titleRefId.isPresent()) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "Mandatory title is missing in manifest for module '%s'.", module.getName())
              .build();
        }
        if (!stringResourceIds.contains(titleRefId.get())) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "Title for module '%s' is missing in the base resource table.", module.getName())
              .build();
        }
      }
    }
  }
}

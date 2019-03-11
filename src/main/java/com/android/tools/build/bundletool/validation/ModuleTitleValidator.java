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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
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

    BundleModule baseModule = modules.stream().filter(BundleModule::isBaseModule).findFirst().get();

    // For bundles built using older versions we haven't strictly enforced module Title Validation.
    if (BundleToolVersion.getVersionFromBundleConfig(baseModule.getBundleConfig())
        .isOlderThan(Version.of("0.4.3"))) {
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
          throw ValidationException.builder()
              .withMessage(
                    "Module titles not supported in asset packs, but found in '%s'.",
                  module.getName())
              .build();
        }
      } else if (!module.getDeliveryType().equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)) {
        Optional<Integer> titleRefId = module.getAndroidManifest().getTitleRefId();

        if (!titleRefId.isPresent()) {
          throw ValidationException.builder()
              .withMessage(
                  "Mandatory title is missing in manifest for module '%s'.", module.getName())
              .build();
        }
        if (!stringResourceIds.contains(titleRefId.get())) {
          throw ValidationException.builder()
              .withMessage(
                  "Title for module '%s' is missing in the base resource table.", module.getName())
              .build();
        }
      }
    }
  }
}

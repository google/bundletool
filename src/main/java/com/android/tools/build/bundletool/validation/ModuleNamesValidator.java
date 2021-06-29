/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Validator ensuring that all modules have distinct module names and splitId are correctly set.
 *
 * <p>This validator is used to validate both App Bundle files, as well as individual bundle module
 * zip files used to build an App Bundle.
 */
final class ModuleNamesValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    Set<BundleModuleName> moduleNames = new HashSet<>();

    for (BundleModule module : modules) {
      Optional<String> splitId = module.getAndroidManifest().getSplitId();
      BundleModuleName moduleName = module.getName();
      boolean isFeatureModule = module.getAndroidManifest().getModuleType().isFeatureModule();

      if (moduleName.equals(BundleModuleName.BASE_MODULE_NAME)) {
        if (splitId.isPresent()) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "The base module should not have the 'split' attribute set in the "
                      + "AndroidManifest.xml")
              .build();
        }
      } else {
        if (splitId.isPresent()) {
          if (!splitId.get().equals(moduleName.getName())) {
            throw InvalidBundleException.builder()
                .withUserMessage(
                    "The 'split' attribute in the AndroidManifest.xml of modules must be the name "
                        + "of the module, but has the value '%s' in module '%s'",
                    splitId.get(), moduleName)
                .build();
          }
        } else {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "No 'split' attribute found in the AndroidManifest.xml of module '%s'.",
                  moduleName)
              .build();
        }
      }

      boolean alreadyPresent = !moduleNames.add(moduleName);
      if (alreadyPresent) {
        if (splitId.isPresent()) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "More than one module have the 'split' attribute set to '%s' in the "
                      + "AndroidManifest.xml.",
                  splitId.get())
              .build();
        } else if (isFeatureModule) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "More than one module was found without the 'split' attribute set in the"
                      + " AndroidManifest.xml. Ensure that all the dynamic features have the"
                      + " 'split' attribute correctly set in the AndroidManifest.xml.")
              .build();
        }
      }
    }

    if (!BundleValidationUtils.isAssetOnlyBundle(modules)
        && !moduleNames.contains(BundleModuleName.BASE_MODULE_NAME)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "No base module found. At least one module must not have a 'split' attribute set in"
                  + " the AndroidManifest.xml.")
          .build();
    }
  }
}

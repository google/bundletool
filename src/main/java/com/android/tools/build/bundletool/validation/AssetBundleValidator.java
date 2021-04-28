/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;

/** Validates asset only bundles. */
public class AssetBundleValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    checkAssetOnlyBundleHasOnlyAssetModules(modules);
    checkAssetOnlyBundleHasOnlyOptionalAssetModules(modules);
  }

  private static void checkAssetOnlyBundleHasOnlyAssetModules(ImmutableList<BundleModule> modules) {
    if (BundleValidationUtils.isAssetOnlyBundle(modules)) {
      modules.stream()
          .filter(module -> !module.getModuleType().equals(ModuleType.ASSET_MODULE))
          .findFirst()
          .ifPresent(
              module -> {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Asset only bundle contains a module that is not an asset module '%s'.",
                        module.getName())
                    .build();
              });
    }
  }

  private static void checkAssetOnlyBundleHasOnlyOptionalAssetModules(
      ImmutableList<BundleModule> modules) {
    if (BundleValidationUtils.isAssetOnlyBundle(modules)) {
      modules.stream()
          .filter(module -> !module.getDeliveryType().equals(ModuleDeliveryType.NO_INITIAL_INSTALL))
          .findFirst()
          .ifPresent(
              module -> {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Asset-only bundle contains an install-time asset module '%s'.",
                        module.getName())
                    .build();
              });
    }
  }
}

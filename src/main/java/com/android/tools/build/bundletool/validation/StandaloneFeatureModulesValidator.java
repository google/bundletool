/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.bundle.Config.StandaloneConfig.FeatureModulesMode;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;

/**
 * Validates bundles with enabled SEPARATE_FEATURE_MODULES.
 *
 * <p>Only AABs with only on-demand feature modules and minSdk < 21 are allowed to have this flag.
 */
final class StandaloneFeatureModulesValidator extends SubValidator {

  @Override
  public void validateBundle(AppBundle bundle) {
    if (!bundle.hasBaseModule()
        || !bundle
            .getBundleConfig()
            .getOptimizations()
            .getStandaloneConfig()
            .getFeatureModulesMode()
            .equals(FeatureModulesMode.SEPARATE_FEATURE_MODULES)) {
      return;
    }
    if (bundle.getBaseModule().getAndroidManifest().getEffectiveMinSdkVersion() >= 21) {
      throw InvalidBundleException.createWithUserMessage(
          "STANDALONE_FEATURE_MODULES can only be used for "
              + "Android App Bundles with minSdk < 21.");
    }
    boolean onlyOnDemandFeatures =
        bundle.getFeatureModules().values().stream()
            .filter(module -> !module.isBaseModule())
            .allMatch(module -> module.getDeliveryType() == ModuleDeliveryType.NO_INITIAL_INSTALL);
    if (!onlyOnDemandFeatures) {
      throw InvalidBundleException.createWithUserMessage(
          "Only on-demand feature modules are supported for "
              + "Android App Bundles with STANDALONE_FEATURE_MODULES enabled.");
    }
    if (!bundle.getAssetModules().isEmpty()) {
      throw InvalidBundleException.createWithUserMessage(
          "Asset modules are not supported for "
              + "Android App Bundles with STANDALONE_FEATURE_MODULES enabled.");
    }
  }
}

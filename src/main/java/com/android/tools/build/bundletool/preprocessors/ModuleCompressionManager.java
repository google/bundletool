/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.build.bundletool.preprocessors;

import static com.android.tools.build.bundletool.model.ModuleDeliveryType.ALWAYS_INITIAL_INSTALL;
import static com.android.tools.build.bundletool.model.ModuleDeliveryType.CONDITIONAL_INITIAL_INSTALL;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Compression.AssetModuleCompression;
import com.android.tools.build.bundletool.model.AndroidManifest;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import javax.inject.Inject;

/** Special rules for the compression of entries in asset modules. */
class ModuleCompressionManager {

  @Inject
  ModuleCompressionManager() {}

  /** Returns whether all assets should be uncompressed in that module. */
  boolean shouldForceUncompressAssets(BundleConfig bundleConfig, AndroidManifest moduleManifest) {
    boolean shouldForceInstallTimeAssetModulesUncompressed =
        !bundleConfig
            .getCompression()
            .getInstallTimeAssetModuleDefaultCompression()
            .equals(AssetModuleCompression.COMPRESSED);
    ModuleDeliveryType moduleDeliveryType = moduleManifest.getModuleDeliveryType();
    ModuleType moduleType = moduleManifest.getModuleType();

    boolean isInstallTimeModule =
        moduleDeliveryType.equals(ALWAYS_INITIAL_INSTALL)
            || moduleDeliveryType.equals(CONDITIONAL_INITIAL_INSTALL);

    return moduleType.equals(ModuleType.ASSET_MODULE)
        && (shouldForceInstallTimeAssetModulesUncompressed || !isInstallTimeModule);
  }
}

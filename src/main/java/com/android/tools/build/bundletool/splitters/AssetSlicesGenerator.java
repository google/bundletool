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

package com.android.tools.build.bundletool.splitters;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/**
 * Generates asset slices from asset modules.
 *
 * <p>Each asset in the module is inserted in at most one asset slice, according to its target.
 */
public class AssetSlicesGenerator {

  private final AppBundle appBundle;
  private final ApkGenerationConfiguration apkGenerationConfiguration;
  private final Optional<Long> assetModulesVersionOverride;

  public AssetSlicesGenerator(
      AppBundle appBundle,
      ApkGenerationConfiguration apkGenerationConfiguration,
      Optional<Long> assetModulesVersionOverride) {
    this.appBundle = checkNotNull(appBundle);
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
    this.assetModulesVersionOverride = assetModulesVersionOverride;
  }

  public ImmutableList<ModuleSplit> generateAssetSlices() {
    ImmutableList.Builder<ModuleSplit> splits = ImmutableList.builder();
    Optional<Integer> appVersionCode =
        appBundle.isAssetOnly()
            ? Optional.empty()
            : appBundle.getBaseModule().getAndroidManifest().getVersionCode();

    for (BundleModule module : appBundle.getAssetModules().values()) {
      AssetModuleSplitter moduleSplitter =
          new AssetModuleSplitter(module, apkGenerationConfiguration, appBundle);
      splits.addAll(
          moduleSplitter.splitModule().stream()
              .map(
                  split -> {
                    if (module.getDeliveryType().equals(ModuleDeliveryType.NO_INITIAL_INSTALL)) {
                      // In slices for on-demand and fast-follow asset modules the version name
                      // instead of the version code is set, since their version code is not used by
                      // Android.
                      Optional<String> nonUpfrontAssetModulesVersionName =
                          (assetModulesVersionOverride.isPresent()
                                  ? assetModulesVersionOverride
                                  : appVersionCode)
                              .map(Object::toString);
                      return addVersionName(split, nonUpfrontAssetModulesVersionName);
                    } else {
                      // Install-time assets module have the same version code as the app.
                      return addVersionCode(split, appVersionCode);
                    }
                  })
              .collect(toImmutableList()));
    }
    return splits.build();
  }

  private static ModuleSplit addVersionCode(
      ModuleSplit moduleSplit, Optional<Integer> versionCode) {
    if (!versionCode.isPresent()) {
      return moduleSplit;
    }
    return moduleSplit.toBuilder()
        .setAndroidManifest(
            moduleSplit.getAndroidManifest().toEditor().setVersionCode(versionCode.get()).save())
        .build();
  }

  private static ModuleSplit addVersionName(ModuleSplit moduleSplit, Optional<String> versionName) {
    if (!versionName.isPresent()) {
      return moduleSplit;
    }
    return moduleSplit.toBuilder()
        .setAndroidManifest(
            moduleSplit.getAndroidManifest().toEditor().setVersionName(versionName.get()).save())
        .build();
  }
}

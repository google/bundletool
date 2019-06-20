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
import com.android.tools.build.bundletool.model.BundleModule.ModuleDeliveryType;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableList;

/**
 * Generates asset slices from remote asset modules.
 *
 * <p>Each asset in the module is inserted in at most one asset slice, according to its target.
 */
public class AssetSlicesGenerator {

  private final AppBundle appBundle;
  private final ApkGenerationConfiguration apkGenerationConfiguration;

  public AssetSlicesGenerator(
      AppBundle appBundle, ApkGenerationConfiguration apkGenerationConfiguration) {
    this.appBundle = checkNotNull(appBundle);
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
  }

  public ImmutableList<ModuleSplit> generateAssetSlices() {
    ImmutableList.Builder<ModuleSplit> splits = ImmutableList.builder();
    int versionCode = appBundle.getBaseModule().getAndroidManifest().getVersionCode();

    for (BundleModule module : appBundle.getAssetModules().values()) {
      RemoteAssetModuleSplitter moduleSplitter =
          new RemoteAssetModuleSplitter(module, apkGenerationConfiguration);
      if (module.getDeliveryType().equals(ModuleDeliveryType.ALWAYS_INITIAL_INSTALL)) {
        splits.addAll(
            moduleSplitter.splitModule().stream()
                .map(split -> addVersionCode(split, versionCode))
                .collect(toImmutableList()));
      } else {
        splits.addAll(moduleSplitter.splitModule());
      }
    }
    return splits.build();
  }

  public static ModuleSplit addVersionCode(ModuleSplit moduleSplit, int versionCode) {
    return moduleSplit.toBuilder()
        .setAndroidManifest(
            moduleSplit.getAndroidManifest().toEditor().setVersionCode(versionCode).save())
        .build();
  }
}

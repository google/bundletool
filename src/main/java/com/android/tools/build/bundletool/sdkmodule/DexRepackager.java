/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.build.bundletool.sdkmodule;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.SdkModulesConfigOuterClass.SdkModulesConfig;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntriesMutator;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Mutator that moves dex files of a given SDK module to the assets directory, so that SDK classes
 * are not loaded together with the app.
 */
public class DexRepackager extends ModuleEntriesMutator {

  private static final String ASSETS_DIRECTORY = "assets";
  private static final String ASSETS_SUBDIRECTORY_PREFIX = "RuntimeEnabledSdk-";

  private final SdkModulesConfig sdkModulesConfig;

  DexRepackager(SdkModulesConfig sdkModulesConfig) {
    this.sdkModulesConfig = sdkModulesConfig;
  }

  @Override
  public Predicate<ModuleEntry> getFilter() {
    return entry -> entry.getPath().startsWith("dex/");
  }

  @Override
  public Function<ImmutableList<ModuleEntry>, ImmutableList<ModuleEntry>> getMutator() {
    return (dexEntries) ->
        dexEntries.stream().map(this::updateDexEntryPath).collect(toImmutableList());
  }

  @Override
  public boolean shouldApplyMutation(BundleModule module) {
    return true;
  }

  private ModuleEntry updateDexEntryPath(ModuleEntry dexEntry) {
    String dexFileName = dexEntry.getPath().getFileName().toString();
    return dexEntry.toBuilder()
        .setPath(ZipPath.create(getNewDexDirectoryPath() + "/" + dexFileName))
        .build();
  }

  String getNewDexDirectoryPath() {
    return ASSETS_DIRECTORY + "/" + getNewDexDirectoryPathInsideAssets();
  }

  String getNewDexDirectoryPathInsideAssets() {
    return ASSETS_SUBDIRECTORY_PREFIX + sdkModulesConfig.getSdkPackageName() + "/dex";
  }
}

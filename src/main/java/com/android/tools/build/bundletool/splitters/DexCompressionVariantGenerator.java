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

import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.utils.TargetingProtoUtils.variantTargeting;
import static com.android.tools.build.bundletool.utils.Versions.ANDROID_P_API_VERSION;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/** Generates variant targetings based on compression of dex files. */
public final class DexCompressionVariantGenerator implements BundleModuleVariantGenerator {

  private final ApkGenerationConfiguration apkGenerationConfiguration;

  public DexCompressionVariantGenerator(ApkGenerationConfiguration apkGenerationConfiguration) {
    this.apkGenerationConfiguration = apkGenerationConfiguration;
  }

  @Override
  public ImmutableCollection<VariantTargeting> generate(BundleModule module) {
    if (!apkGenerationConfiguration.getEnableDexCompressionSplitter()
        || apkGenerationConfiguration.isForInstantAppVariants()) {
      return ImmutableList.of();
    }
    ImmutableSet<ModuleEntry> dexEntries =
        module.getEntries().stream()
            .filter(entry -> entry.getPath().startsWith(DEX_DIRECTORY))
            .collect(toImmutableSet());

    if (dexEntries.isEmpty()) {
      return ImmutableList.of();
    }

    return ImmutableList.of(
        variantTargeting(sdkVersionTargeting(sdkVersionFrom(ANDROID_P_API_VERSION))));
  }
}

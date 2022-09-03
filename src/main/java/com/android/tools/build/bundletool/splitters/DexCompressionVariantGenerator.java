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
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.variantTargeting;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_Q_API_VERSION;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_S_API_VERSION;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Config.UncompressDexFiles.UncompressedDexTargetSdk;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.google.common.collect.ImmutableSet;
import java.util.stream.Stream;

/** Generates variant targetings based on compression of dex files. */
public final class DexCompressionVariantGenerator implements BundleModuleVariantGenerator {

  private final ApkGenerationConfiguration apkGenerationConfiguration;

  public DexCompressionVariantGenerator(ApkGenerationConfiguration apkGenerationConfiguration) {
    this.apkGenerationConfiguration = apkGenerationConfiguration;
  }

  @Override
  public Stream<VariantTargeting> generate(BundleModule module) {
    if (!apkGenerationConfiguration.getEnableDexCompressionSplitter()
        || apkGenerationConfiguration.isForInstantAppVariants()) {
      return Stream.of();
    }
    ImmutableSet<ModuleEntry> dexEntries =
        module.getEntries().stream()
            .filter(entry -> entry.getPath().startsWith(DEX_DIRECTORY))
            .collect(toImmutableSet());

    if (dexEntries.isEmpty()) {
      return Stream.of();
    }
    Stream.Builder<VariantTargeting> variantTargetings = Stream.builder();
    if (apkGenerationConfiguration
        .getDexCompressionSplitterForTargetSdk()
        .equals(UncompressedDexTargetSdk.SDK_31)) {
      variantTargetings.add(
          variantTargeting(sdkVersionTargeting(sdkVersionFrom(ANDROID_S_API_VERSION))));
    } else {
      variantTargetings.add(
          variantTargeting(sdkVersionTargeting(sdkVersionFrom(ANDROID_Q_API_VERSION))));
    }
    // Uncompressed dex are supported starting from Android P, but only starting from Android Q the
    // performance impact is negligible compared to a compressed dex.
    return variantTargetings.build();
  }
}

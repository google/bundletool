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

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.lPlusVariantTargeting;
import static com.android.tools.build.bundletool.model.utils.Versions.ANDROID_L_API_VERSION;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.android.tools.build.bundletool.model.targeting.TargetingUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Generates all variant targetings that would be created from the {@link BundleModule}.
 *
 * <p>Pre-L variants are out of scope of this generator. Standalone APKs create a single variant per
 * APK.
 */
public final class VariantGenerator {

  private final BundleModule module;
  private final ApkGenerationConfiguration apkGenerationConfiguration;

  @VisibleForTesting
  VariantGenerator(BundleModule module) {
    this(module, ApkGenerationConfiguration.getDefaultInstance());
  }

  public VariantGenerator(
      BundleModule module, ApkGenerationConfiguration apkGenerationConfiguration) {
    this.module = module;
    this.apkGenerationConfiguration = apkGenerationConfiguration;
  }

  public ImmutableSet<VariantTargeting> generateVariants() {
    if (targetsOnlyPreL(module)) {
      throw CommandExecutionException.builder()
          .withMessage(
              "Cannot generate variants '%s' because it does not target devices on Android "
                  + "L or above.",
              module.getName())
          .build();
    }

    ImmutableSet<VariantTargeting> splitVariants =
        getVariantGenerators().stream()
            .flatMap(generator -> generator.generate(module))
            .collect(toImmutableSet());

    return TargetingUtils.cropVariantsWithAppSdkRange(
        splitVariants, module.getAndroidManifest().getSdkRange());
  };

  private ImmutableList<BundleModuleVariantGenerator> getVariantGenerators() {
    return ImmutableList.of(
        apkGenerationConfiguration -> Stream.of(lPlusVariantTargeting()),
        new NativeLibsCompressionVariantGenerator(apkGenerationConfiguration),
        new DexCompressionVariantGenerator(apkGenerationConfiguration));
  }

  private static boolean targetsOnlyPreL(BundleModule module) {
    Optional<Integer> maxSdkVersion = module.getAndroidManifest().getMaxSdkVersion();
    return maxSdkVersion.isPresent() && maxSdkVersion.get() < ANDROID_L_API_VERSION;
  }
}

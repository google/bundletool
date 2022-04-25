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
package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.generateAllVariantTargetings;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javax.inject.Inject;

/**
 * Generates all variant targetings that would be created from the given list of {@link
 * BundleModule}s.
 *
 * <p>Pre-L variants are out of scope of this generator. Standalone APKs create a single variant per
 * APK.
 */
public class VariantTargetingGenerator {

  private final PerModuleVariantTargetingGenerator perModuleVariantTargetingGenerator;
  private final SdkRuntimeVariantGenerator sdkRuntimeVariantGenerator;

  @Inject
  VariantTargetingGenerator(
      PerModuleVariantTargetingGenerator perModuleVariantTargetingGenerator,
      SdkRuntimeVariantGenerator sdkRuntimeVariantGenerator) {
    this.perModuleVariantTargetingGenerator = perModuleVariantTargetingGenerator;
    this.sdkRuntimeVariantGenerator = sdkRuntimeVariantGenerator;
  }

  public ImmutableSet<VariantTargeting> generateVariantTargetings(
      ImmutableList<BundleModule> modules, ApkGenerationConfiguration apkGenerationConfiguration) {
    ImmutableSet<VariantTargeting> nonSdkRuntimeVariantTargetings =
        generateNonSdkRuntimeVariantTargetings(modules, apkGenerationConfiguration);
    ImmutableSet<VariantTargeting> sdkRuntimeVariantTargetings =
        sdkRuntimeVariantGenerator.generate(nonSdkRuntimeVariantTargetings);
    return ImmutableSet.<VariantTargeting>builder()
        .addAll(nonSdkRuntimeVariantTargetings)
        .addAll(sdkRuntimeVariantTargetings)
        .build();
  }

  private ImmutableSet<VariantTargeting> generateNonSdkRuntimeVariantTargetings(
      ImmutableList<BundleModule> modules, ApkGenerationConfiguration apkGenerationConfiguration) {
    ImmutableSet.Builder<VariantTargeting> builder = ImmutableSet.builder();
    for (BundleModule module : modules) {
      ImmutableSet<VariantTargeting> variantTargetings =
          perModuleVariantTargetingGenerator.generateVariants(module, apkGenerationConfiguration);
      builder.addAll(variantTargetings);
    }
    return generateAllVariantTargetings(builder.build());
  }
}

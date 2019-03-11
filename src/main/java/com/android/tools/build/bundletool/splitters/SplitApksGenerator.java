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

import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.generateAllVariantTargetings;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/** Generates split APKs. */
public final class SplitApksGenerator {

  private final ImmutableList<BundleModule> modules;
  private final ApkGenerationConfiguration apkGenerationConfiguration;
  private final Version bundleVersion;

  public SplitApksGenerator(
      ImmutableList<BundleModule> modules,
      Version bundleVersion,
      ApkGenerationConfiguration apkGenerationConfiguration) {
    this.modules = checkNotNull(modules);
    this.bundleVersion = checkNotNull(bundleVersion);
    this.apkGenerationConfiguration = checkNotNull(apkGenerationConfiguration);
  }

  public ImmutableList<ModuleSplit> generateSplits() {
    ImmutableSet<VariantTargeting> variantTargetings = generateVariants();
    return variantTargetings.stream()
        .flatMap(variantTargeting -> generateSplitApks(variantTargeting).stream())
        .collect(toImmutableList());
  }

  private ImmutableSet<VariantTargeting> generateVariants() {
    ImmutableSet.Builder<VariantTargeting> builder = ImmutableSet.builder();
    for (BundleModule module : modules) {
      VariantGenerator variantGenerator = new VariantGenerator(module, apkGenerationConfiguration);
      ImmutableSet<VariantTargeting> splitApks = variantGenerator.generateVariants();
      builder.addAll(splitApks);
    }
    return generateAllVariantTargetings(builder.build());
  }

  private ImmutableList<ModuleSplit> generateSplitApks(VariantTargeting variantTargeting) {
    ImmutableSet<String> allModuleNames =
        modules.stream().map(module -> module.getName().getName()).collect(toImmutableSet());
    ImmutableList.Builder<ModuleSplit> splits = ImmutableList.builder();

    for (BundleModule module : modules) {
      ModuleSplitter moduleSplitter =
          new ModuleSplitter(
              module, bundleVersion, apkGenerationConfiguration, variantTargeting, allModuleNames);
      splits.addAll(moduleSplitter.splitModule());
    }
    return splits.build();
  }
}

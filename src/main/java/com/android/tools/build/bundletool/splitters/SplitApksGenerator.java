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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.SourceStampConstants.StampType;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.inject.Inject;

/** Generates split APKs. */
public final class SplitApksGenerator {

  private final Version bundletoolVersion;
  private final Optional<SourceStamp> stampSource;
  private final VariantTargetingGenerator variantTargetingGenerator;
  private final AppBundle appBundle;

  @Inject
  public SplitApksGenerator(
      Version bundletoolVersion,
      Optional<SourceStamp> stampSource,
      VariantTargetingGenerator variantTargetingGenerator,
      AppBundle appBundle) {
    this.bundletoolVersion = bundletoolVersion;
    this.stampSource = stampSource;
    this.variantTargetingGenerator = variantTargetingGenerator;
    this.appBundle = appBundle;
  }

  public ImmutableList<ModuleSplit> generateSplits(
      ImmutableList<BundleModule> modules, ApkGenerationConfiguration apkGenerationConfiguration) {
    return variantTargetingGenerator
        .generateVariantTargetings(modules, apkGenerationConfiguration)
        .stream()
        .flatMap(
            variantTargeting ->
                generateSplitApks(modules, apkGenerationConfiguration, variantTargeting).stream())
        .collect(toImmutableList());
  }

  private ImmutableList<ModuleSplit> generateSplitApks(
      ImmutableList<BundleModule> modules,
      ApkGenerationConfiguration commonApkGenerationConfiguration,
      VariantTargeting variantTargeting) {
    ImmutableList<BundleModule> modulesForVariant = getModulesForVariant(modules, variantTargeting);
    ImmutableSet<String> allModuleNames =
        modulesForVariant.stream()
            .map(module -> module.getName().getName())
            .collect(toImmutableSet());
    ImmutableList.Builder<ModuleSplit> splits = ImmutableList.builder();

    for (BundleModule module : modulesForVariant) {
      ModuleSplitter moduleSplitter =
          ModuleSplitter.create(
              module,
              bundletoolVersion,
              appBundle,
              getApkGenerationConfigurationForModule(module, commonApkGenerationConfiguration),
              variantTargeting,
              allModuleNames,
              stampSource.map(SourceStamp::getSource),
              StampType.STAMP_TYPE_DISTRIBUTION_APK);
      splits.addAll(moduleSplitter.splitModule());
    }
    return splits.build();
  }

  private ImmutableList<BundleModule> getModulesForVariant(
      ImmutableList<BundleModule> modules, VariantTargeting variantTargeting) {
    if (variantTargeting.getSdkRuntimeTargeting().getRequiresSdkRuntime()) {
      return modules.stream()
          .filter(module -> !module.getModuleType().equals(ModuleType.SDK_DEPENDENCY_MODULE))
          .collect(toImmutableList());
    }
    return modules;
  }

  private ApkGenerationConfiguration getApkGenerationConfigurationForModule(
      BundleModule module, ApkGenerationConfiguration commonGenerationConfig) {
    if (module.getModuleType().equals(ModuleType.SDK_DEPENDENCY_MODULE)) {
      // We never generate splits for runtime-enabled SDK dependency modules.
      return ApkGenerationConfiguration.getDefaultInstance();
    }
    return commonGenerationConfig;
  }
}

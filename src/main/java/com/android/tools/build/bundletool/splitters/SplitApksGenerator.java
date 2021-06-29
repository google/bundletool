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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.SourceStamp;
import com.android.tools.build.bundletool.model.SourceStamp.StampType;
import com.android.tools.build.bundletool.model.version.Version;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.inject.Inject;

/** Generates split APKs. */
public final class SplitApksGenerator {

  private final Version bundletoolVersion;
  private final Optional<SourceStamp> stampSource;
  private final VariantGenerator variantGenerator;
  private final AppBundle appBundle;

  @Inject
  public SplitApksGenerator(
      Version bundletoolVersion,
      Optional<SourceStamp> stampSource,
      VariantGenerator variantGenerator,
      AppBundle appBundle) {
    this.bundletoolVersion = bundletoolVersion;
    this.stampSource = stampSource;
    this.variantGenerator = variantGenerator;
    this.appBundle = appBundle;
  }

  public ImmutableList<ModuleSplit> generateSplits(
      ImmutableList<BundleModule> modules, ApkGenerationConfiguration apkGenerationConfiguration) {
    ImmutableSet<VariantTargeting> variantTargetings =
        generateVariants(modules, apkGenerationConfiguration);
    return variantTargetings.stream()
        .flatMap(
            variantTargeting ->
                generateSplitApks(modules, apkGenerationConfiguration, variantTargeting).stream())
        .collect(toImmutableList());
  }

  private ImmutableSet<VariantTargeting> generateVariants(
      ImmutableList<BundleModule> modules, ApkGenerationConfiguration apkGenerationConfiguration) {
    ImmutableSet.Builder<VariantTargeting> builder = ImmutableSet.builder();
    for (BundleModule module : modules) {
      ImmutableSet<VariantTargeting> splitApks =
          variantGenerator.generateVariants(module, apkGenerationConfiguration);
      builder.addAll(splitApks);
    }
    return generateAllVariantTargetings(builder.build());
  }

  private ImmutableList<ModuleSplit> generateSplitApks(
      ImmutableList<BundleModule> modules,
      ApkGenerationConfiguration apkGenerationConfiguration,
      VariantTargeting variantTargeting) {
    ImmutableSet<String> allModuleNames =
        modules.stream().map(module -> module.getName().getName()).collect(toImmutableSet());
    ImmutableList.Builder<ModuleSplit> splits = ImmutableList.builder();

    for (BundleModule module : modules) {
      ModuleSplitter moduleSplitter =
          ModuleSplitter.create(
              module,
              bundletoolVersion,
              appBundle,
              apkGenerationConfiguration,
              variantTargeting,
              allModuleNames,
              stampSource.map(SourceStamp::getSource),
              StampType.STAMP_TYPE_DISTRIBUTION_APK);
      splits.addAll(moduleSplitter.splitModule());
    }
    return splits.build();
  }
}

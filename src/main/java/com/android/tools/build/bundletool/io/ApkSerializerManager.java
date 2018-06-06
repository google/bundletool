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
package com.android.tools.build.bundletool.io;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.ModuleMetadata;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory.ApkSetBuilder;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.utils.ConcurrencyUtils;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/** Creates parts of table of contents and writes out APKs. */
public class ApkSerializerManager {

  private static final ModuleMetadata STANDALONE_MODULE_METADATA =
      ModuleMetadata.newBuilder().setName(BundleModuleName.BASE_MODULE_NAME).build();

  private final ListeningExecutorService executorService;

  public ApkSerializerManager(ListeningExecutorService executorService) {
    this.executorService = executorService;
  }

  public ImmutableList<Variant> serializeAndGenerateAllVariants(
      ImmutableList<ModuleSplit> allApks,
      ApkSetBuilder apkSetBuilder,
      AppBundle appBundle,
      boolean isUniversalApk) {
    ImmutableList<ModuleSplit> splitApks =
        allApks
            .stream()
            .filter(Predicates.not(ModuleSplit::isStandalone))
            .collect(toImmutableList());
    checkArgument(
        splitApks.isEmpty() || !isUniversalApk,
        "Internal error: for universal APK expecting only standalone APKs.");
    ImmutableList<ModuleSplit> standaloneApks =
        allApks.stream().filter(ModuleSplit::isStandalone).collect(toImmutableList());
    ImmutableList<Variant> standaloneVariants =
        serializeAndGenerateStandaloneApkVariants(standaloneApks, apkSetBuilder, isUniversalApk);
    ImmutableList<Variant> splitVariants =
        serializeAndGenerateSplitApkVariants(splitApks, apkSetBuilder, appBundle);
    return Stream.of(standaloneVariants, splitVariants)
        .flatMap(Collection::stream)
        .collect(toImmutableList());
  }

  public Variant serializeAndGenerateVariantForDevice(
      DeviceSpec deviceSpec,
      ImmutableList<ModuleSplit> allApks,
      ApkSetBuilder apkSetBuilder,
      AppBundle appBundle) {

    ImmutableList<ModuleSplit> filteredApks = filterApksForDevice(allApks, deviceSpec);

    ImmutableList<Variant> variantList;
    if (isAllStandalone(filteredApks)) {
      variantList =
          serializeAndGenerateStandaloneApkVariants(
              filteredApks, apkSetBuilder, /* isUniversalApk= */ false);
    } else if (isAllNotStandalone(filteredApks)) {
      variantList = serializeAndGenerateSplitApkVariants(filteredApks, apkSetBuilder, appBundle);
    } else {
      throw new IllegalStateException("Expected APKs to be either all split or standalone.");
    }

    checkState(variantList.size() == 1);
    return variantList.get(0);
  }

  private ImmutableList<Variant> serializeAndGenerateSplitApkVariants(
      ImmutableList<ModuleSplit> splitApks, ApkSetBuilder apkSetBuilder, AppBundle appBundle) {
    // Group splits by variant.
    ImmutableMultimap<VariantTargeting, ModuleSplit> targetingToSplits =
        Multimaps.index(splitApks, ModuleSplit::getVariantTargeting);
    ImmutableList.Builder<Variant> variantsBuilder = ImmutableList.builder();
    for (VariantTargeting variantTargeting : targetingToSplits.keySet()) {
      // Group splits in each variant by the module which they belong to.
      ImmutableMultimap<BundleModule, ModuleSplit> splitsByModule =
          Multimaps.index(
              targetingToSplits.get(variantTargeting),
              moduleSplit -> appBundle.getModule(moduleSplit.getModuleName()));
      Variant.Builder variantBuilder = Variant.newBuilder().setTargeting(variantTargeting);
      for (BundleModule module : splitsByModule.keySet()) {
        List<ApkDescription> apkDescriptions =
            // Wait for all concurrent tasks to succeed, or any to fail.
            ConcurrencyUtils.waitForAll(
                splitsByModule
                    .get(module)
                    .stream()
                    .map(
                        splitApk ->
                            executorService.submit(() -> apkSetBuilder.addSplitApk(splitApk)))
                    .collect(toImmutableList()));

        variantBuilder.addApkSet(
            ApkSet.newBuilder()
                .setModuleMetadata(module.getModuleMetadata())
                .addAllApkDescription(apkDescriptions)
                .build());
      }
      variantsBuilder.add(variantBuilder.build());
    }
    return variantsBuilder.build();
  }

  private ImmutableList<Variant> serializeAndGenerateStandaloneApkVariants(
      ImmutableList<ModuleSplit> standaloneApks,
      ApkSetBuilder apkSetBuilder,
      boolean isUniversalApk) {

    // Wait for all concurrent tasks to succeed, or any to fail.
    return ConcurrencyUtils.waitForAll(
        standaloneApks
            .stream()
            .map(
                standaloneApk ->
                    executorService.submit(
                        () ->
                            writeStandaloneApkVariant(
                                standaloneApk, isUniversalApk, apkSetBuilder)))
            .collect(toImmutableList()));
  }

  private Variant writeStandaloneApkVariant(
      ModuleSplit standaloneApk, boolean isUniversalApk, ApkSetBuilder apkSetBuilder) {

    ApkDescription apkDescription =
        isUniversalApk
            ? apkSetBuilder.addStandaloneUniversalApk(standaloneApk)
            : apkSetBuilder.addStandaloneApk(standaloneApk);

    // Each standalone APK is represented as a single variant.
    return Variant.newBuilder()
        .setTargeting(standaloneApk.getVariantTargeting())
        .addApkSet(
            ApkSet.newBuilder()
                .setModuleMetadata(STANDALONE_MODULE_METADATA)
                .addApkDescription(apkDescription))
        .build();
  }

  private static ImmutableList<ModuleSplit> filterApksForDevice(
      ImmutableList<ModuleSplit> allApks, DeviceSpec deviceSpec) {
    ApkMatcher apkMatcher = new ApkMatcher(deviceSpec);
    return allApks.stream().filter(apkMatcher::matchesModuleSplit).collect(toImmutableList());
  }

  private static boolean isAllStandalone(ImmutableList<ModuleSplit> moduleSplits) {
    return moduleSplits.stream().allMatch(ModuleSplit::isStandalone);
  }

  private static boolean isAllNotStandalone(ImmutableList<ModuleSplit> moduleSplits) {
    return moduleSplits.stream().noneMatch(ModuleSplit::isStandalone);
  }
}

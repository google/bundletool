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

import static com.android.tools.build.bundletool.utils.CollectorUtils.groupingBySortedKeys;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.io.ApkSetBuilderFactory.ApkSetBuilder;
import com.android.tools.build.bundletool.model.ApkListener;
import com.android.tools.build.bundletool.model.ApkModifier;
import com.android.tools.build.bundletool.model.ApkModifier.ApkDescription.ApkType;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.android.tools.build.bundletool.model.VariantKey;
import com.android.tools.build.bundletool.utils.ConcurrencyUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Creates parts of table of contents and writes out APKs. */
public class ApkSerializerManager {

  private final ListeningExecutorService executorService;
  private final ApkListener apkListener;
  private final ApkModifier apkModifier;
  private final int firstVariantNumber;
  private final AppBundle appBundle;
  private final ApkSetBuilder apkSetBuilder;

  public ApkSerializerManager(
      AppBundle appBundle,
      ApkSetBuilder apkSetBuilder,
      ListeningExecutorService executorService,
      ApkListener apkListener,
      ApkModifier apkModifier,
      int firstVariantNumber) {
    this.appBundle = appBundle;
    this.apkSetBuilder = apkSetBuilder;
    this.executorService = executorService;
    this.apkListener = apkListener;
    this.apkModifier = apkModifier;
    this.firstVariantNumber = firstVariantNumber;
  }

  public ImmutableList<Variant> serializeApksForDevice(
      GeneratedApks generatedApks, DeviceSpec deviceSpec) {
    return serializeApks(generatedApks, ApkBuildMode.DEFAULT, Optional.of(deviceSpec));
  }

  @VisibleForTesting
  ImmutableList<Variant> serializeApks(GeneratedApks generatedApks) {
    return serializeApks(generatedApks, ApkBuildMode.DEFAULT);
  }

  public ImmutableList<Variant> serializeApks(
      GeneratedApks generatedApks, ApkBuildMode apkBuildMode) {
    return serializeApks(generatedApks, apkBuildMode, Optional.empty());
  }

  private ImmutableList<Variant> serializeApks(
      GeneratedApks generatedApks, ApkBuildMode apkBuildMode, Optional<DeviceSpec> deviceSpec) {
    validateInput(generatedApks, apkBuildMode);

    Predicate<ModuleSplit> deviceFilter =
        deviceSpec.isPresent()
            ? new ApkMatcher(deviceSpec.get())::matchesModuleSplitByTargeting
            : alwaysTrue();

    ImmutableListMultimap<VariantKey, ModuleSplit> splitsByVariant =
        generatedApks.getAllApksGroupedByOrderedVariants();

    // Assign the variant numbers to each variant present.
    AtomicInteger variantNumberCounter = new AtomicInteger(firstVariantNumber);
    ImmutableMap<VariantKey, Integer> variantNumberByVariantKey =
        splitsByVariant.keySet().stream()
            .collect(toImmutableMap(identity(), unused -> variantNumberCounter.getAndIncrement()));

    // 1. Remove APKs not matching the device spec.
    // 2. Modify the APKs based on the ApkModifier.
    // 3. Serialize all APKs in parallel.
    ApkSerializer apkSerializer = new ApkSerializer(apkListener, apkBuildMode);

    // Modifies the APK using APK modifier, then returns a map by extracting the variant
    // of APK first and later clearing out its variant targeting.

    ImmutableListMultimap<VariantKey, ModuleSplit> finalSplitsByVariant =
        splitsByVariant.entries().stream()
            .filter(keyModuleSplitEntry -> deviceFilter.test(keyModuleSplitEntry.getValue()))
            .collect(
                groupingBySortedKeys(
                    Entry::getKey,
                    entry ->
                        clearVariantTargeting(
                            modifyApk(
                                entry.getValue(), variantNumberByVariantKey.get(entry.getKey())))));

    // After variant targeting of APKs are cleared, there might be duplicate APKs
    // which are removed and the distinct APKs are then serialized in parallel.
    // Note: Only serializing compressed system APK produces multiple ApkDescriptions,
    // i.e compressed and stub APK descriptions.
    ImmutableMap<ModuleSplit, ImmutableList<ApkDescription>> apkDescriptionBySplit =
        finalSplitsByVariant.values().stream()
            .distinct()
            .collect(
                Collectors.collectingAndThen(
                    toImmutableMap(
                        identity(),
                        split -> executorService.submit(() -> apkSerializer.serialize(split))),
                    ConcurrencyUtils::waitForAll));

    // Build the result proto.
    ImmutableList.Builder<Variant> variants = ImmutableList.builder();
    for (VariantKey variantKey : finalSplitsByVariant.keySet()) {
      Variant.Builder variant =
          Variant.newBuilder()
              .setVariantNumber(variantNumberByVariantKey.get(variantKey))
              .setTargeting(variantKey.getVariantTargeting());

      Multimap<BundleModuleName, ModuleSplit> splitsByModuleName =
          finalSplitsByVariant.get(variantKey).stream()
              .collect(groupingBySortedKeys(ModuleSplit::getModuleName));

      for (BundleModuleName moduleName : splitsByModuleName.keySet()) {
        variant.addApkSet(
            ApkSet.newBuilder()
                .setModuleMetadata(appBundle.getModule(moduleName).getModuleMetadata())
                .addAllApkDescription(
                    splitsByModuleName.get(moduleName).stream()
                        .flatMap(split -> apkDescriptionBySplit.get(split).stream())
                        .collect(toImmutableList())));
      }
      variants.add(variant.build());
    }

    return variants.build();
  }

  private void validateInput(GeneratedApks generatedApks, ApkBuildMode apkBuildMode) {
    switch (apkBuildMode) {
      case DEFAULT:
        checkArgument(
            generatedApks.getSystemApks().isEmpty(),
            "Internal error: System APKs can only be set in system mode.");
        break;
      case UNIVERSAL:
        checkArgument(
            generatedApks.getSplitApks().isEmpty()
                && generatedApks.getInstantApks().isEmpty()
                && generatedApks.getSystemApks().isEmpty(),
            "Internal error: For universal APK expecting only standalone APKs.");
        break;
      case SYSTEM_COMPRESSED:
      case SYSTEM:
        checkArgument(
            generatedApks.getSplitApks().isEmpty()
                && generatedApks.getInstantApks().isEmpty()
                && generatedApks.getStandaloneApks().isEmpty(),
            "Internal error: For system mode expecting only system APKs.");
        break;
    }
  }

  private ModuleSplit modifyApk(ModuleSplit moduleSplit, int variantNumber) {
    ApkModifier.ApkDescription apkDescription =
        ApkModifier.ApkDescription.builder()
            .setBase(moduleSplit.isBaseModuleSplit())
            .setApkType(
                moduleSplit.getSplitType().equals(SplitType.STANDALONE)
                    ? ApkType.STANDALONE
                    : (moduleSplit.isMasterSplit() ? ApkType.MASTER_SPLIT : ApkType.CONFIG_SPLIT))
            .setVariantNumber(variantNumber)
            .setVariantTargeting(moduleSplit.getVariantTargeting())
            .setApkTargeting(moduleSplit.getApkTargeting())
            .build();

    return moduleSplit
        .toBuilder()
        .setAndroidManifest(
            apkModifier.modifyManifest(moduleSplit.getAndroidManifest(), apkDescription))
        .build();
  }

  private static ModuleSplit clearVariantTargeting(ModuleSplit moduleSplit) {
    return moduleSplit
        .toBuilder()
        .setVariantTargeting(VariantTargeting.getDefaultInstance())
        .build();
  }

  private final class ApkSerializer {
    private final ApkListener apkListener;
    private final ApkBuildMode apkBuildMode;

    public ApkSerializer(ApkListener apkListener, ApkBuildMode apkBuildMode) {
      this.apkListener = apkListener;
      this.apkBuildMode = apkBuildMode;
    }

    public ImmutableList<ApkDescription> serialize(ModuleSplit split) {
      ImmutableList<ApkDescription> apkDescriptions;
      switch (split.getSplitType()) {
        case INSTANT:
          apkDescriptions = ImmutableList.of(apkSetBuilder.addInstantApk(split));
          break;
        case SPLIT:
          apkDescriptions = ImmutableList.of(apkSetBuilder.addSplitApk(split));
          break;
        case SYSTEM:
          apkDescriptions =
              apkBuildMode.equals(ApkBuildMode.SYSTEM_COMPRESSED)
                  ? apkSetBuilder.addCompressedSystemApks(split)
                  : ImmutableList.of(apkSetBuilder.addSystemApk(split));
          break;
        case STANDALONE:
          apkDescriptions =
              apkBuildMode.equals(ApkBuildMode.UNIVERSAL)
                  ? ImmutableList.of(apkSetBuilder.addStandaloneUniversalApk(split))
                  : ImmutableList.of(apkSetBuilder.addStandaloneApk(split));
          break;
        default:
          throw new IllegalStateException("Unexpected splitType: " + split.getSplitType());
      }

      // Notify apk listener.
      apkDescriptions.forEach(apkListener::onApkFinalized);
      return apkDescriptions;
    }
  }
}

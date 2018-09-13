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

import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.INSTANT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.SPLIT;
import static com.android.tools.build.bundletool.model.ModuleSplit.SplitType.STANDALONE;
import static com.android.tools.build.bundletool.targeting.TargetingComparators.VARIANT_TARGETING_COMPARATOR;
import static com.android.tools.build.bundletool.utils.CollectorUtils.groupingBySortedKeys;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.Variant;
import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.VariantTargeting;
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
import com.android.tools.build.bundletool.utils.ConcurrencyUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.ListeningExecutorService;
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

  public ImmutableList<Variant> serializeUniversalApk(GeneratedApks generatedApks) {
    checkArgument(
        generatedApks.getSplitApks().isEmpty() && generatedApks.getInstantApks().isEmpty(),
        "Internal error: For universal APK expecting only standalone APKs.");
    return serializeApks(generatedApks, /* isUniversalApk= */ true, Optional.empty());
  }

  public ImmutableList<Variant> serializeApksForDevice(
      GeneratedApks generatedApks, DeviceSpec deviceSpec) {
    return serializeApks(generatedApks, /* isUniversalApk= */ false, Optional.of(deviceSpec));
  }

  public ImmutableList<Variant> serializeApks(GeneratedApks generatedApks) {
    return serializeApks(generatedApks, /* isUniversalApk= */ false, Optional.empty());
  }

  private ImmutableList<Variant> serializeApks(
      GeneratedApks generatedApks, boolean isUniversalApk, Optional<DeviceSpec> deviceSpec) {
    Predicate<ModuleSplit> deviceFilter =
        deviceSpec.isPresent()
            ? new ApkMatcher(deviceSpec.get())::matchesModuleSplitByTargeting
            : alwaysTrue();

    // Assign the variant numbers to each variant present.
    AtomicInteger variantNumberCounter = new AtomicInteger(firstVariantNumber);
    ImmutableMap<VariantKey, Integer> variantNumberByVariantKey =
        generatedApks
            .getAllApksStream()
            .map(VariantKey::create)
            .sorted()
            .distinct()
            .collect(toImmutableMap(identity(), unused -> variantNumberCounter.getAndIncrement()));

    // 1. Remove APKs not matching the device spec.
    // 2. Modify the APKs based on the ApkModifier.
    // 3. Serialize all APKs in parallel.
    ApkSerializer apkSerializer = new ApkSerializer(apkListener, isUniversalApk);

    // Modifies the APK using APK modifier, then returns a map by extracting the variant
    // of APK first and later clearing out its variant targeting.
    ImmutableListMultimap<VariantKey, ModuleSplit> splitByVariant =
        generatedApks
            .getAllApksStream()
            .filter(deviceFilter)
            .map(
                split -> {
                  int variantNumber = variantNumberByVariantKey.get(VariantKey.create(split));
                  return modifyApk(split, variantNumber);
                })
            .collect(
                groupingBySortedKeys(
                    VariantKey::create, ApkSerializerManager::clearVariantTargeting));

    // After variant targeting of APKs are cleared, there might be duplicate APKs
    // which are removed and the distinct APKs are then serialized in parallel.
    ImmutableMap<ModuleSplit, ApkDescription> apkDescriptionBySplit =
        splitByVariant
            .values()
            .stream()
            .distinct()
            .collect(
                Collectors.collectingAndThen(
                    toImmutableMap(
                        identity(),
                        split -> executorService.submit(() -> apkSerializer.serialize(split))),
                    ConcurrencyUtils::waitForAll));

    // Build the result proto.
    ImmutableList.Builder<Variant> variants = ImmutableList.builder();
    for (VariantKey variantKey : splitByVariant.keySet()) {
      Variant.Builder variant =
          Variant.newBuilder()
              .setVariantNumber(variantNumberByVariantKey.get(variantKey))
              .setTargeting(variantKey.getVariantTargeting());

      Multimap<BundleModuleName, ModuleSplit> splitsByModuleName =
          splitByVariant
              .get(variantKey)
              .stream()
              .collect(groupingBySortedKeys(ModuleSplit::getModuleName));

      for (BundleModuleName moduleName : splitsByModuleName.keySet()) {
        variant.addApkSet(
            ApkSet.newBuilder()
                .setModuleMetadata(appBundle.getModule(moduleName).getModuleMetadata())
                .addAllApkDescription(
                    splitsByModuleName
                        .get(moduleName)
                        .stream()
                        .map(apkDescriptionBySplit::get)
                        .collect(toImmutableList())));
      }
      variants.add(variant.build());
    }

    return variants.build();
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

  /**
   * Key identifying a variant.
   *
   * <p>A variant is a set of APKs. One device is guaranteed to receive only APKs from the same
   * variant.
   */
  @AutoValue
  abstract static class VariantKey implements Comparable<VariantKey> {
    static VariantKey create(ModuleSplit moduleSplit) {
      return new AutoValue_ApkSerializerManager_VariantKey(
          moduleSplit.getSplitType(), moduleSplit.getVariantTargeting());
    }

    abstract SplitType getSplitType();

    abstract VariantTargeting getVariantTargeting();

    @Override
    public int compareTo(VariantKey o) {
      // Instant APKs get the lowest variant numbers followed by standalone and then split APKs.
      return comparing(VariantKey::getSplitType, Ordering.explicit(INSTANT, STANDALONE, SPLIT))
          .thenComparing(VariantKey::getVariantTargeting, VARIANT_TARGETING_COMPARATOR)
          .compare(this, o);
    }
  }

  private final class ApkSerializer {
    private final ApkListener apkListener;
    private final boolean isUniversalApk;

    public ApkSerializer(ApkListener apkListener, boolean isUniversalApk) {
      this.apkListener = apkListener;
      this.isUniversalApk = isUniversalApk;
    }

    public ApkDescription serialize(ModuleSplit split) {
      ApkDescription apkDescription;
      switch (split.getSplitType()) {
        case INSTANT:
          apkDescription = apkSetBuilder.addInstantApk(split);
          break;
        case SPLIT:
          apkDescription = apkSetBuilder.addSplitApk(split);
          break;
        case STANDALONE:
          apkDescription =
              isUniversalApk
                  ? apkSetBuilder.addStandaloneUniversalApk(split)
                  : apkSetBuilder.addStandaloneApk(split);
          break;
        default:
          throw new IllegalStateException("Unexpected splitType: " + split.getSplitType());
      }

      apkListener.onApkFinalized(apkDescription);

      return apkDescription;
    }
  }
}

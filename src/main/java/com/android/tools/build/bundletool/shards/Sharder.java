/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.build.bundletool.shards;

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.abiUniverse;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.densityUniverse;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Devices.DeviceSpec;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.device.ApkMatcher;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;

/** Groups module splits into shards that next are merged in standalone apks. */
public class Sharder {

  private final Optional<DeviceSpec> deviceSpec;

  @Inject
  public Sharder(Optional<DeviceSpec> deviceSpec) {
    this.deviceSpec = deviceSpec;
  }

  /**
   * Groups a flat list of splits from all bundle modules into shards. There is one shard per each
   * ABI x density x language targeting available in module splits. Each shard contains all master
   * splits + specific ABI splits + specific density splits + specific language splits. Shards are
   * next merged into standalone APKs.
   */
  public ImmutableList<ImmutableList<ModuleSplit>> groupSplitsToShards(
      ImmutableList<ModuleSplit> splits) {
    // The input contains master split and possibly ABI and/or density splits for module m1, m2 etc.
    // Let's denote the splits as follows:
    //   * for module 1: {m1-master, m1-abi1, m1-abi2, ..., m1-density1, m1-density2, ...}
    //   * for module 2: {m2-master, m2-abi1, m2-abi2, ..., m2-density1, m2-density2, ...}
    //   * etc.
    //
    // First we partition the splits by their targeting dimension:
    //   * master splits:  {m1-master, m2-master, ...}
    //   * ABI splits:     {m1-abi1, m1-abi2, ..., m2-abi1, m2-abi2, ...}
    //   * density splits: {m1-density1, m1-density2, ..., m2-density1, m2-density2, ...}
    ImmutableSet<ModuleSplit> abiSplits =
        subsetWithTargeting(splits, ApkTargeting::hasAbiTargeting);
    ImmutableSet<ModuleSplit> densitySplits =
        subsetWithTargeting(splits, ApkTargeting::hasScreenDensityTargeting);

    ImmutableSet<ModuleSplit> languageSplits = getLanguageSplits(splits);
    ImmutableSet<ModuleSplit> masterSplits = getMasterSplits(splits);

    checkState(
        Collections.disjoint(Sets.newHashSet(abiSplits), Sets.newHashSet(densitySplits)),
        "No split is expected to have both ABI and screen density targeting.");
    // Density splitter is expected to produce the same density universe for any module.
    checkState(
        sameTargetedUniverse(densitySplits, split -> densityUniverse(split.getApkTargeting())),
        "Density splits are expected to cover the same densities.");
    if (!sameTargetedUniverse(abiSplits, split -> abiUniverse(split.getApkTargeting()))) {
      throw CommandExecutionException.builder()
          .withInternalMessage(
              "Modules for standalone APKs must cover the same ABIs when optimizing for ABI.")
          .build();
    }

    // Next, within each of the groups perform additional partitioning based on the actual value in
    // the targeting dimension:
    //   * master splits: { {m1-master, m2-master, ...} }
    //   * abi splits: {
    //                   {m1-abi1, m2-abi1, ...},  // targeting abi1
    //                   {m1-abi2, m2-abi2, ...},  // targeting abi2
    //                   ...
    //                 }
    //   * density splits: {
    //                       {m1-density1, m2-density1, ...},  // targeting density1
    //                       {m1-density2, m2-density2, ...},  // targeting density2
    //                       ...
    //                     }
    // Note that if any of the partitioning was empty, we use {{}} instead.
    Collection<Collection<ModuleSplit>> abiSplitsSubsets =
        nonEmpty(partitionByTargeting(abiSplits));
    Collection<Collection<ModuleSplit>> densitySplitsSubsets =
        nonEmpty(partitionByTargeting(densitySplits));

    // Finally each member of cartesian product "master splits" x "abi splits" x "density splits"
    // represents a collection of splits that need to be fused in order to produce a single
    // sharded APK.
    ImmutableList.Builder<ImmutableList<ModuleSplit>> shards = ImmutableList.builder();
    for (Collection<ModuleSplit> abiSplitsSubset : abiSplitsSubsets) {
      for (Collection<ModuleSplit> densitySplitsSubset : densitySplitsSubsets) {
        // Describe a future shard as a collection of splits that need to be fused.
        shards.add(
            ImmutableList.<ModuleSplit>builder()
                .addAll(masterSplits)
                .addAll(languageSplits)
                .addAll(abiSplitsSubset)
                .addAll(densitySplitsSubset)
                .build());
      }
    }

    return shards.build();
  }

  private ImmutableSet<ModuleSplit> subsetWithTargeting(
      ImmutableList<ModuleSplit> splits, Predicate<ApkTargeting> predicate) {
    return splits.stream()
        .filter(split -> predicate.test(split.getApkTargeting()))
        .filter(split -> deviceSpec.map(spec -> splitMatchesDeviceSpec(split, spec)).orElse(true))
        .collect(toImmutableSet());
  }

  /** Returns master splits, i.e splits without any targeting. */
  private static ImmutableSet<ModuleSplit> getMasterSplits(ImmutableList<ModuleSplit> splits) {
    ImmutableSet<ModuleSplit> masterSplits =
        splits.stream().filter(ModuleSplit::isMasterSplit).collect(toImmutableSet());

    checkState(
        masterSplits.size() >= 1,
        "Expecting at least one master split, got %s.",
        masterSplits.size());
    checkState(
        masterSplits.stream()
            .allMatch(
                split ->
                    split.getApkTargeting().toBuilder()
                        .clearTextureCompressionFormatTargeting()
                        .clearDeviceTierTargeting()
                        .clearCountrySetTargeting()
                        .build()
                        .equals(ApkTargeting.getDefaultInstance())),
        "Master splits can not have any targeting other than Texture Compression Format, Device"
            + " Tier and Country Set.");

    return masterSplits;
  }

  private static ImmutableSet<ModuleSplit> getLanguageSplits(ImmutableList<ModuleSplit> splits) {
    return splits.stream()
        .filter(split -> split.getApkTargeting().hasLanguageTargeting())
        .collect(toImmutableSet());
  }

  private static ImmutableCollection<Collection<ModuleSplit>> partitionByTargeting(
      Collection<ModuleSplit> splits) {
    return Multimaps.index(splits, ModuleSplit::getApkTargeting).asMap().values();
  }

  private static <T> Collection<Collection<T>> nonEmpty(Collection<Collection<T>> x) {
    return x.isEmpty() ? ImmutableList.of(ImmutableList.of()) : x;
  }

  private static boolean sameTargetedUniverse(
      Set<ModuleSplit> splits, Function<ModuleSplit, Collection<?>> getUniverseFn) {
    long distinctNonEmptyUniverseCount =
        splits.stream()
            .map(getUniverseFn::apply)
            // Filter out splits having no targeting in the dimension of the universe.
            .filter(not(Collection::isEmpty))
            .distinct()
            .count();
    return distinctNonEmptyUniverseCount <= 1;
  }

  static boolean splitMatchesDeviceSpec(ModuleSplit moduleSplit, DeviceSpec deviceSpec) {
    return new ApkMatcher(deviceSpec).matchesModuleSplitByTargeting(moduleSplit);
  }
}

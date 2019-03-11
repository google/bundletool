/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.abiUniverse;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.abiValues;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.densityUniverse;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.densityValues;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.languageUniverse;
import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.languageValues;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.AbiTargeting;
import com.android.bundle.Targeting.ApkTargeting;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensityTargeting;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Utilities for merging module splits.
 *
 * <p>Package-private because the helper methods are highly specific and possibly confusing in other
 * contexts.
 */
final class MergingUtils {

  /**
   * Returns the other value if the values are either equal or the first value is {@code null}.
   * Otherwise returns an empty {@link Optional}.
   */
  public static <T> Optional<T> getSameValueOrNonNull(@Nullable T nullableValue, T otherValue) {
    checkNotNull(otherValue);
    if (nullableValue == null || nullableValue.equals(otherValue)) {
      return Optional.of(otherValue);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Merges two targetings into targeting of an APK shard.
   *
   * <p>Expects that the input targetings have only ABI, screen density or language targeting.
   *
   * <p>If both targetings target a common dimension, then the targeted universe in that dimension
   * must be the same.
   */
  public static ApkTargeting mergeShardTargetings(
      ApkTargeting targeting1, ApkTargeting targeting2) {

    checkHasOnlyAbiDensityAndLanguageTargeting(targeting1);
    checkHasOnlyAbiDensityAndLanguageTargeting(targeting2);

    ApkTargeting.Builder merged = ApkTargeting.newBuilder();
    if (targeting1.hasAbiTargeting() || targeting2.hasAbiTargeting()) {
      merged.setAbiTargeting(mergeAbiTargetingsOf(targeting1, targeting2));
    }
    if (targeting1.hasScreenDensityTargeting() || targeting2.hasScreenDensityTargeting()) {
      merged.setScreenDensityTargeting(mergeDensityTargetingsOf(targeting1, targeting2));
    }

    if (targeting1.hasLanguageTargeting() || targeting2.hasLanguageTargeting()) {
      merged.setLanguageTargeting(mergeLanguageTargetingsOf(targeting1, targeting2));
    }

    return merged.build();
  }

  private static void checkHasOnlyAbiDensityAndLanguageTargeting(ApkTargeting targeting) {
    ApkTargeting targetingWithoutAbiDensityAndLanguage =
        targeting
            .toBuilder()
            .clearAbiTargeting()
            .clearScreenDensityTargeting()
            .clearLanguageTargeting()
            .build();
    if (!targetingWithoutAbiDensityAndLanguage.equals(ApkTargeting.getDefaultInstance())) {
      throw CommandExecutionException.builder()
          .withMessage(
              "Expecting only ABI, screen density and language targeting, got '%s'.", targeting)
          .build();
    }
  }

  private static AbiTargeting mergeAbiTargetingsOf(
      ApkTargeting targeting1, ApkTargeting targeting2) {
    Set<Abi> universe = Sets.union(abiUniverse(targeting1), abiUniverse(targeting2));
    Set<Abi> values = Sets.union(abiValues(targeting1), abiValues(targeting2));
    return AbiTargeting.newBuilder()
        .addAllValue(values)
        .addAllAlternatives(Sets.difference(universe, values))
        .build();
  }

  private static ScreenDensityTargeting mergeDensityTargetingsOf(
      ApkTargeting targeting1, ApkTargeting targeting2) {
    Set<ScreenDensity> universe =
        Sets.union(densityUniverse(targeting1), densityUniverse(targeting2));
    Set<ScreenDensity> values = Sets.union(densityValues(targeting1), densityValues(targeting2));
    return ScreenDensityTargeting.newBuilder()
        .addAllValue(values)
        .addAllAlternatives(Sets.difference(universe, values))
        .build();
  }

  private static LanguageTargeting mergeLanguageTargetingsOf(
      ApkTargeting targeting1, ApkTargeting targeting2) {
    Set<String> universe = Sets.union(languageUniverse(targeting1), languageUniverse(targeting2));
    Set<String> values = Sets.union(languageValues(targeting1), languageValues(targeting2));
    return LanguageTargeting.newBuilder()
        .addAllValue(values)
        .addAllAlternatives(Sets.difference(universe, values))
        .build();
  }

  private MergingUtils() {}
}

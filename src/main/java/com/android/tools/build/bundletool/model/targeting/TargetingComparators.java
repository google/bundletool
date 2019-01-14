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

package com.android.tools.build.bundletool.model.targeting;

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.getScreenDensityDpi;
import static com.google.common.collect.Comparators.emptiesFirst;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparing;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Ordering;
import java.util.Comparator;
import java.util.Optional;

/**
 * Comparators for Targetings.
 *
 * <p>The underlying order is the serving preference, i.e. one targeting is greater if it is
 * considered more optimized for the user's device and settings.
 */
public final class TargetingComparators {

  /**
   * This should verify the following statements in order:
   *
   * <ul>
   *   <li>arm < x86 < mips
   *   <li>32 bits < 64 bits
   *   <li>less recent version of CPU < more recent version of CPU
   */
  public static final Ordering<AbiAlias> ARCHITECTURE_ORDERING =
      Ordering.explicit(
          AbiAlias.ARMEABI,
          AbiAlias.ARMEABI_V7A,
          AbiAlias.ARM64_V8A,
          AbiAlias.X86,
          AbiAlias.X86_64,
          AbiAlias.MIPS,
          AbiAlias.MIPS64);

  private static final Comparator<VariantTargeting> ABI_COMPARATOR =
      comparing(TargetingComparators::getAbi, emptiesFirst(ARCHITECTURE_ORDERING));

  private static final Comparator<VariantTargeting> SDK_COMPARATOR =
      comparing(TargetingComparators::getMinSdk, emptiesFirst(Ordering.natural()));

  private static final Comparator<VariantTargeting> SCREEN_DENSITY_COMPARATOR =
      comparing(TargetingComparators::getScreenDensity, emptiesFirst(Ordering.natural()));

  /**
   * Comparator for sets of AbiAliases, according to ARCHITECTURE_ORDERING.
   *
   * <p>The ABIs in a MultiAbi are not ordered, but we sort them when comparing MultiAbis, from most
   * to least preferable ABI. The sorted sets are then compared lexicographically. This means that:
   *
   * - MultiAbis are ordered by the most preferable ABI that is different between the two (e.g.
   * [x86_64] > [x86]; [x86, arm64-v8a] > [x86, armeabi-v7a]).
   *
   * - A set of ABIs that contains another is always larger (e.g. [x86_64, X86] > [x86_64]).
   */
  public static final Comparator<ImmutableSet<AbiAlias>> MULTI_ABI_ALIAS_COMPARATOR =
      comparing(
          TargetingComparators::sortMultiAbi, Comparators.lexicographical(ARCHITECTURE_ORDERING));

  @VisibleForTesting
  static final Comparator<VariantTargeting> MULTI_ABI_COMPARATOR =
      comparing(TargetingComparators::getMultiAbi, MULTI_ABI_ALIAS_COMPARATOR);

  public static final Comparator<VariantTargeting> VARIANT_TARGETING_COMPARATOR =
      SDK_COMPARATOR
          .thenComparing(ABI_COMPARATOR)
          .thenComparing(MULTI_ABI_COMPARATOR)
          .thenComparing(SCREEN_DENSITY_COMPARATOR);

  private static Optional<Integer> getMinSdk(VariantTargeting variantTargeting) {
    // If the variant does not have an SDK targeting, it is suitable for all SDK values.
    if (variantTargeting.getSdkVersionTargeting().getValueList().isEmpty()) {
      return Optional.empty();
    }

    // If there are many different minSdks values, select the maximum of those.
    // We ignore the alternatives.
    return variantTargeting
        .getSdkVersionTargeting()
        .getValueList()
        .stream()
        .map(sdkVersion -> sdkVersion.getMin().getValue())
        .max(Comparator.naturalOrder());
  }

  private static Optional<AbiAlias> getAbi(VariantTargeting variantTargeting) {
    if (variantTargeting.getAbiTargeting().getValueList().isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        variantTargeting
            .getAbiTargeting()
            .getValueList()
            .stream()
            // For now we only support one value in AbiTargeting.
            .collect(MoreCollectors.onlyElement())
            .getAlias());
  }

  private static ImmutableSet<AbiAlias> getMultiAbi(VariantTargeting variantTargeting) {
    if (variantTargeting.getMultiAbiTargeting().getValueList().isEmpty()) {
      return ImmutableSet.of();
    }

    return variantTargeting.getMultiAbiTargeting().getValueList().stream()
        // For now we only support one value in MultiAbiTargeting.
        .collect(MoreCollectors.onlyElement())
        .getAbiList()
        .stream()
        .map(Abi::getAlias)
        .collect(toImmutableSet());
  }

  /** Sort a set of ABIs from most preferable (e.g. X86_64) to least (e.g. ARMEABI_V7A). */
  private static ImmutableSortedSet<AbiAlias> sortMultiAbi(ImmutableSet<AbiAlias> abis) {
    return ImmutableSortedSet.copyOf(ARCHITECTURE_ORDERING.reverse(), abis);
  }

  private static Optional<Integer> getScreenDensity(VariantTargeting variantTargeting) {
    return getScreenDensityDpi(variantTargeting.getScreenDensityTargeting());
  }

  private TargetingComparators() {}
}

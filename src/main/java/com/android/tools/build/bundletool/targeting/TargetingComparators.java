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

package com.android.tools.build.bundletool.targeting;

import static com.android.bundle.Targeting.ScreenDensity.DensityOneofCase.DENSITY_ALIAS;
import static com.android.tools.build.bundletool.utils.ResourcesUtils.DENSITY_ALIAS_TO_DPI_MAP;
import static com.google.common.collect.Comparators.emptiesFirst;
import static java.util.Comparator.comparing;

import com.android.bundle.Targeting.Abi.AbiAlias;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.VariantTargeting;
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
  private static final Ordering<AbiAlias> ARCHITECTURE_ORDERING =
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
      comparing(TargetingComparators::getMinSdk, emptiesFirst(Integer::compare));

  private static final Comparator<VariantTargeting> SCREEN_DENSITY_COMPARATOR =
      comparing(TargetingComparators::getScreenDensity, emptiesFirst(Integer::compare));

  public static final Comparator<VariantTargeting> VARIANT_TARGETING_COMPARATOR =
      SDK_COMPARATOR.thenComparing(ABI_COMPARATOR).thenComparing(SCREEN_DENSITY_COMPARATOR);

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

  private static Optional<Integer> getScreenDensity(VariantTargeting variantTargeting) {
    if (variantTargeting.getScreenDensityTargeting().getValueList().isEmpty()) {
      return Optional.empty();
    }

    ScreenDensity densityTargeting =
        variantTargeting
            .getScreenDensityTargeting()
            .getValueList()
            .stream()
            // For now we only support one value in ScreenDensityTargeting.
            .collect(MoreCollectors.onlyElement());
    return Optional.of(
        (densityTargeting.getDensityOneofCase() == DENSITY_ALIAS)
            ? DENSITY_ALIAS_TO_DPI_MAP.get(densityTargeting.getDensityAlias())
            : densityTargeting.getDensityDpi());
  }

  private TargetingComparators() {}
}

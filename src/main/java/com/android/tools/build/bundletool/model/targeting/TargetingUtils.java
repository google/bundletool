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

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionTargeting;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.utils.TargetingProtoUtils;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;

/** Utility functions for Targeting proto. */
public final class TargetingUtils {

  /** Returns the targeting dimensions of the targeting proto. */
  public static ImmutableList<TargetingDimension> getTargetingDimensions(
      AssetsDirectoryTargeting targeting) {
    ImmutableList.Builder<TargetingDimension> dimensions = new ImmutableList.Builder<>();
    if (targeting.hasAbi()) {
      dimensions.add(TargetingDimension.ABI);
    }
    if (targeting.hasGraphicsApi()) {
      dimensions.add(TargetingDimension.GRAPHICS_API);
    }
    if (targeting.hasTextureCompressionFormat()) {
      dimensions.add(TargetingDimension.TEXTURE_COMPRESSION_FORMAT);
    }
    if (targeting.hasLanguage()) {
      dimensions.add(TargetingDimension.LANGUAGE);
    }
    return dimensions.build();
  }

  /**
   * Given a set of potentially overlapping variant targetings generate smallest set of disjoint
   * variant targetings covering all of them.
   *
   * <p>Assumption: All Variants only support sdk targeting.
   */
  public static ImmutableSet<VariantTargeting> generateAllVariantTargetings(
      ImmutableSet<VariantTargeting> variantTargetings) {

    if (variantTargetings.size() <= 1) {
      return variantTargetings;
    }

    ImmutableList<SdkVersionTargeting> sdkVersionTargetings =
        disjointSdkTargetings(
            variantTargetings.stream()
                .map(variantTargeting -> variantTargeting.getSdkVersionTargeting())
                .collect(toImmutableList()));

    return sdkVersionTargetings
        .stream()
        .map(
            sdkVersionTargeting ->
                VariantTargeting.newBuilder().setSdkVersionTargeting(sdkVersionTargeting).build())
        .collect(toImmutableSet());
  }

  /**
   * Returns the filtered out or modified variant targetings that are an intersection of the given
   * variant targetings and SDK version range we want to support.
   *
   * <p>It is assumed that the variantTargetings contain only {@link SdkVersionTargeting}.
   *
   * <p>Each given and returned variant doesn't have the alternatives populated.
   */
  public static ImmutableSet<VariantTargeting> cropVariantsWithAppSdkRange(
      ImmutableSet<VariantTargeting> variantTargetings, Range<Integer> sdkRange) {
    ImmutableList<Range<Integer>> ranges = calculateVariantSdkRanges(variantTargetings, sdkRange);

    return ranges.stream()
        .map(range -> sdkVariantTargeting(range.lowerEndpoint()))
        .collect(toImmutableSet());
  }

  /**
   * Generates ranges of effective sdk versions each given variants covers intersected with the
   * range of SDK levels an app supports.
   *
   * <p>Variants that have no overlap with app's SDK level range are discarded.
   */
  private static ImmutableList<Range<Integer>> calculateVariantSdkRanges(
      ImmutableSet<VariantTargeting> variantTargetings, Range<Integer> appSdkRange) {
    return disjointSdkTargetings(
            variantTargetings.stream()
                .map(variantTargeting -> variantTargeting.getSdkVersionTargeting())
                .collect(toImmutableList()))
        .stream()
        .map(sdkTargeting -> Range.closedOpen(getMinSdk(sdkTargeting), getMaxSdk(sdkTargeting)))
        .filter(appSdkRange::isConnected)
        .map(appSdkRange::intersection)
        .filter(Predicates.not(Range::isEmpty))
        .collect(toImmutableList());
  }

  /**
   * Given a set of potentially overlapping sdk targetings generate set of disjoint sdk targetings
   * covering all of them.
   *
   * <p>Assumption: There are no sdk range gaps in targetings.
   */
  private static ImmutableList<SdkVersionTargeting> disjointSdkTargetings(
      ImmutableList<SdkVersionTargeting> sdkVersionTargetings) {

    sdkVersionTargetings.forEach(
        sdkVersionTargeting -> checkState(sdkVersionTargeting.getValueList().size() == 1));

    ImmutableList<Integer> minSdkValues =
        sdkVersionTargetings
            .stream()
            .map(sdkVersionTargeting -> sdkVersionTargeting.getValue(0).getMin().getValue())
            .distinct()
            .sorted()
            .collect(toImmutableList());

    ImmutableSet<SdkVersion> sdkVersions =
        minSdkValues.stream().map(TargetingProtoUtils::sdkVersionFrom).collect(toImmutableSet());

    return sdkVersions
        .stream()
        .map(
            sdkVersion ->
                sdkVersionTargeting(
                    sdkVersion,
                    Sets.difference(sdkVersions, ImmutableSet.of(sdkVersion)).immutableCopy()))
        .collect(toImmutableList());
  }

  /** Extracts the minimum sdk (inclusive) supported by the targeting. */
  public static int getMinSdk(SdkVersionTargeting sdkVersionTargeting) {
    if (sdkVersionTargeting.getValueList().isEmpty()) {
      return 1;
    }
    return Iterables.getOnlyElement(sdkVersionTargeting.getValueList()).getMin().getValue();
  }

  /** Extracts the maximum sdk (exclusive) supported by the targeting. */
  public static int getMaxSdk(SdkVersionTargeting sdkVersionTargeting) {
    int minSdk = getMinSdk(sdkVersionTargeting);
    int alternativeMinSdk =
        sdkVersionTargeting
            .getAlternativesList()
            .stream()
            .mapToInt(alternativeSdk -> alternativeSdk.getMin().getValue())
            .filter(sdkValue -> minSdk < sdkValue)
            .min()
            .orElse(Integer.MAX_VALUE);

    return alternativeMinSdk;
  }

  private static VariantTargeting sdkVariantTargeting(int minSdk) {
    return VariantTargeting.newBuilder()
        .setSdkVersionTargeting(
            SdkVersionTargeting.newBuilder().addValue(TargetingProtoUtils.sdkVersionFrom(minSdk)))
        .build();
  }
}

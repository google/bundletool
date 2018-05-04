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

package com.android.tools.build.bundletool.targeting;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.Variant;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.VariantTargeting;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.protobuf.Message;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.CheckReturnValue;

/** Adds alternative targeting in the {@code T} dimension. */
public abstract class AlternativeVariantTargetingPopulator<T extends Message> {

  public static ImmutableList<Variant> populateAlternativeVariantTargeting(
      ImmutableList<Variant> splitApkVariants, ImmutableList<Variant> standaloneVariants) {
    // Standalone variants currently target only ABI and density.
    standaloneVariants =
        new AbiAlternativesPopulator().addAlternativeVariantTargeting(standaloneVariants);
    standaloneVariants =
        new ScreenDensityAlternativesPopulator().addAlternativeVariantTargeting(standaloneVariants);

    // Both standalone and split APK variants differ by SDK targeting.
    return new SdkVersionAlternativesPopulator()
        .addAlternativeVariantTargeting(splitApkVariants, standaloneVariants);
  }

  /**
   * Populates alternative targeting in dimension {@code T} in all variants.
   *
   * <p>Does nothing when none of the variants targets dimension {@code T}.
   *
   * <p>Throws when some variants target dimension {@code T} and some don't. This is to protect the
   * caller from mixing inappropriate variants.
   */
  @CheckReturnValue
  ImmutableList<Variant> addAlternativeVariantTargeting(ImmutableList<Variant>... variants) {
    return addAlternativeVariantTargeting(
        Arrays.stream(variants).flatMap(Collection::stream).collect(toImmutableList()));
  }

  /** @see AlternativeVariantTargetingPopulator#addAlternativeVariantTargeting(ImmutableList...) */
  @CheckReturnValue
  ImmutableList<Variant> addAlternativeVariantTargeting(ImmutableList<Variant> variants) {
    ImmutableSet<Boolean> dimensionIsTargeted =
        variants
            .stream()
            .map(variant -> !getValues(variant.getTargeting()).isEmpty())
            .collect(toImmutableSet());
    checkArgument(
        dimensionIsTargeted.size() <= 1,
        "Some variants are agnostic to the dimension, and some are not.");
    if (variants.isEmpty() || !Iterables.getOnlyElement(dimensionIsTargeted)) {
      // Variants are entirely agnostic to the given dimension.
      return variants;
    }

    ImmutableSet<T> allValues =
        variants
            .stream()
            .flatMap(variant -> getValues(variant.getTargeting()).stream())
            .collect(toImmutableSet());

    return variants
        .stream()
        .map(
            variant -> {
              Variant.Builder result = variant.toBuilder();
              setDimensionAlternatives(
                  result.getTargetingBuilder(),
                  ImmutableSet.copyOf(
                      Sets.difference(
                          allValues, ImmutableSet.copyOf(getValues(variant.getTargeting())))));
              return result.build();
            })
        .collect(toImmutableList());
  }

  /** Reads the `values` targeting field in dimension {@code T}. */
  protected abstract ImmutableList<T> getValues(VariantTargeting targeting);

  /** Sets the `alternatives` targeting field in dimension {@code T}. */
  protected abstract void setDimensionAlternatives(
      VariantTargeting.Builder targetingBuilder, ImmutableCollection<T> alternatives);

  /** Populates alternative ABI targeting. */
  @VisibleForTesting
  static class AbiAlternativesPopulator extends AlternativeVariantTargetingPopulator<Abi> {

    @Override
    protected ImmutableList<Abi> getValues(VariantTargeting targeting) {
      return ImmutableList.copyOf(targeting.getAbiTargeting().getValueList());
    }

    @Override
    protected void setDimensionAlternatives(
        VariantTargeting.Builder targetingBuilder, ImmutableCollection<Abi> alternatives) {
      targetingBuilder
          .getAbiTargetingBuilder()
          .clearAlternatives()
          .addAllAlternatives(alternatives);
    }
  }

  /** Populates alternative screen density targeting. */
  @VisibleForTesting
  static class ScreenDensityAlternativesPopulator
      extends AlternativeVariantTargetingPopulator<ScreenDensity> {

    @Override
    protected ImmutableList<ScreenDensity> getValues(VariantTargeting targeting) {
      return ImmutableList.copyOf(targeting.getScreenDensityTargeting().getValueList());
    }

    @Override
    protected void setDimensionAlternatives(
        VariantTargeting.Builder targetingBuilder,
        ImmutableCollection<ScreenDensity> alternatives) {
      targetingBuilder
          .getScreenDensityTargetingBuilder()
          .clearAlternatives()
          .addAllAlternatives(alternatives);
    }
  }

  /** Populates alternative SDK targeting. */
  @VisibleForTesting
  static class SdkVersionAlternativesPopulator
      extends AlternativeVariantTargetingPopulator<SdkVersion> {

    @Override
    protected ImmutableList<SdkVersion> getValues(VariantTargeting targeting) {
      return ImmutableList.copyOf(targeting.getSdkVersionTargeting().getValueList());
    }

    @Override
    protected void setDimensionAlternatives(
        VariantTargeting.Builder targetingBuilder, ImmutableCollection<SdkVersion> alternatives) {
      targetingBuilder
          .getSdkVersionTargetingBuilder()
          .clearAlternatives()
          .addAllAlternatives(alternatives);
    }
  }
}

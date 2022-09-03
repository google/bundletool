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

package com.android.tools.build.bundletool.model.targeting;

import static com.android.tools.build.bundletool.model.utils.TargetingProtoUtils.sdkVersionFrom;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.partitioningBy;

import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.SdkVersion;
import com.android.bundle.Targeting.SdkVersionTargeting;
import com.android.bundle.Targeting.VariantTargeting;
import com.android.tools.build.bundletool.model.GeneratedApks;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.Message;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Adds alternative targeting in the {@code T} dimension. */
public abstract class AlternativeVariantTargetingPopulator<T extends Message> {

  public static GeneratedApks populateAlternativeVariantTargeting(
      GeneratedApks generatedApks, int maxSdkVersion) {
    return populateAlternativeVariantTargeting(generatedApks, Optional.of(maxSdkVersion));
  }

  public static GeneratedApks populateAlternativeVariantTargeting(GeneratedApks generatedApks) {
    return populateAlternativeVariantTargeting(generatedApks, Optional.empty());
  }

  public static GeneratedApks populateAlternativeVariantTargeting(
      GeneratedApks generatedApks, Optional<Integer> maxSdkVersion) {
    ImmutableList<ModuleSplit> standaloneApks =
        new AbiAlternativesPopulator()
            .addAlternativeVariantTargeting(generatedApks.getStandaloneApks());
    standaloneApks =
        new ScreenDensityAlternativesPopulator().addAlternativeVariantTargeting(standaloneApks);

    Map<Boolean, ImmutableList<ModuleSplit>> partitionedRuntimeEnabledAndRegularSplits =
        generatedApks.getSplitApks().stream()
            .collect(
                partitioningBy(
                    moduleSplit ->
                        moduleSplit
                            .getVariantTargeting()
                            .getSdkRuntimeTargeting()
                            .getRequiresSdkRuntime(),
                    toImmutableList()));

    ImmutableList<ModuleSplit> moduleSplits =
        ImmutableList.<ModuleSplit>builder()
            .addAll(
                new SdkVersionAlternativesPopulator(maxSdkVersion)
                    .addAlternativeVariantTargeting(
                        partitionedRuntimeEnabledAndRegularSplits.get(true)))
            .addAll(
                new SdkVersionAlternativesPopulator(maxSdkVersion)
                    .addAlternativeVariantTargeting(
                        partitionedRuntimeEnabledAndRegularSplits.get(false), standaloneApks))
            .addAll(generatedApks.getInstantApks())
            .addAll(generatedApks.getSystemApks())
            .addAll(generatedApks.getArchivedApks())
            .build();
    return GeneratedApks.fromModuleSplits(moduleSplits);
  }

  /**
   * See AlternativeVariantTargetingPopulator#addAlternativeVariantTargeting(ImmutableList...)
   *
   * <p>This is a version for {@link ModuleSplit} type.
   */
  @CheckReturnValue
  ImmutableList<ModuleSplit> addAlternativeVariantTargeting(ImmutableList<ModuleSplit>... splits) {
    return addAlternativeVariantTargeting(
        Arrays.stream(splits).flatMap(Collection::stream).collect(toImmutableList()));
  }

  @CheckReturnValue
  ImmutableList<ModuleSplit> addAlternativeVariantTargeting(ImmutableList<ModuleSplit> apks) {
    ImmutableList<VariantTargeting> variantTargeting =
        apks.stream().map(ModuleSplit::getVariantTargeting).collect(toImmutableList());
    variantTargeting = addAlternativeVariantTargetingInternal(variantTargeting);
    checkState(variantTargeting.size() == apks.size());

    ImmutableList.Builder<ModuleSplit> result = ImmutableList.builder();
    for (int i = 0; i < apks.size(); i++) {
      result.add(apks.get(i).toBuilder().setVariantTargeting(variantTargeting.get(i)).build());
    }
    return result.build();
  }

  @CheckReturnValue
  ImmutableList<VariantTargeting> addAlternativeVariantTargetingInternal(
      ImmutableList<VariantTargeting> variantTargetings) {
    ImmutableSet<Boolean> dimensionIsTargeted =
        variantTargetings
            .stream()
            .map(variantTargeting -> !getValues(variantTargeting).isEmpty())
            .collect(toImmutableSet());
    checkArgument(
        dimensionIsTargeted.size() <= 1,
        "Some variants are agnostic to the dimension, and some are not.");
    if (variantTargetings.isEmpty() || !Iterables.getOnlyElement(dimensionIsTargeted)) {
      // Variants are entirely agnostic to the given dimension.
      return variantTargetings;
    }

    ImmutableSet<T> allValues =
        variantTargetings
            .stream()
            .flatMap(variantTargeting -> getValues(variantTargeting).stream())
            .collect(toImmutableSet());

    return variantTargetings
        .stream()
        .map(
            variantTargeting -> {
              VariantTargeting.Builder result = variantTargeting.toBuilder();
              setDimensionAlternatives(
                  result,
                  ImmutableSet.copyOf(
                      Sets.difference(
                          allValues, ImmutableSet.copyOf(getValues(variantTargeting)))));
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

    private final Optional<Integer> maxSdkVersion;

    public SdkVersionAlternativesPopulator() {
      this(Optional.empty());
    }

    public SdkVersionAlternativesPopulator(Optional<Integer> maxSdkVersion) {
      this.maxSdkVersion = maxSdkVersion;
    }

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

    /** This extends the helper method to add a sentinel alternative to express maxSdkVersion. */
    @CheckReturnValue
    @Override
    ImmutableList<VariantTargeting> addAlternativeVariantTargetingInternal(
        ImmutableList<VariantTargeting> variantTargetings) {
      ImmutableList<VariantTargeting> variantsWithoutSentinel =
          super.addAlternativeVariantTargetingInternal(variantTargetings);
      if (!maxSdkVersion.isPresent()) {
        return variantsWithoutSentinel;
      } else {
        return variantsWithoutSentinel.stream()
            .map(targeting -> addSentinelVariantTargeting(targeting, maxSdkVersion.get()))
            .collect(toImmutableList());
      }
    }

    private static VariantTargeting addSentinelVariantTargeting(
        VariantTargeting targeting, int maxSdkVersion) {
      SdkVersionTargeting sdkTargeting = targeting.getSdkVersionTargeting();
      return targeting
          .toBuilder()
          .setSdkVersionTargeting(
              sdkTargeting.toBuilder().addAlternatives(sdkVersionFrom(maxSdkVersion + 1)))
          .build();
    }
  }
}

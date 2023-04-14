/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.targeting.TargetedDirectorySegment.constructTargetingSegmentPath;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getAssetDirectories;
import static com.android.tools.build.bundletool.model.targeting.TargetingUtils.getAssetsDirectoryTargetingByDimension;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.naturalOrder;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.targeting.TargetedDirectory;
import com.android.tools.build.bundletool.model.targeting.TargetingDimension;
import com.android.tools.build.bundletool.model.targeting.TargetingUtils;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Validator class for nested targeting validations. */
public class NestedTargetingValidator extends SubValidator {

  /** Validates nested targeting in all the modules. */
  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    modules.forEach(NestedTargetingValidator::validateNestedTargetingInModule);
    if (modules.stream()
            .map(NestedTargetingValidator::getDirectoryAllNestedTargetingByPathBaseName)
            .filter(
                moduleDirectoryAllNestedTargetingByPathBaseName ->
                    !moduleDirectoryAllNestedTargetingByPathBaseName.isEmpty())
            .map(ImmutableSetMultimap::asMap)
            .map(ImmutableMap::values)
            .distinct()
            .count()
        > 1) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Found different nested targeting across different modules. Please make sure all"
                  + " modules use same nested targeting.")
          .build();
    }
  }

  private static void validateNestedTargetingInModule(BundleModule module) {
    ImmutableSetMultimap<String, DirectoryNestedTargeting>
        directoryAllNestedTargetingByPathBaseName =
            getDirectoryAllNestedTargetingByPathBaseName(module);
    if (directoryAllNestedTargetingByPathBaseName.isEmpty()) {
      return;
    }
    validateAllDirectoryNestedTargetingAreDistinct(
        module, directoryAllNestedTargetingByPathBaseName);
    validateAllDirectoriesHaveSameNestedTargeting(
        module, directoryAllNestedTargetingByPathBaseName);
    ImmutableSet<DirectoryNestedTargeting> supportedNestedTargeting =
        ImmutableSet.copyOf(
            directoryAllNestedTargetingByPathBaseName.asMap().values().stream().findAny().get());
    validateNestingDimensionOrder(module, supportedNestedTargeting);
    validateNestedTargetingCoversAllCartesianProductPoints(module, supportedNestedTargeting);
  }

  private static void validateNestedTargetingCoversAllCartesianProductPoints(
      BundleModule module, ImmutableSet<DirectoryNestedTargeting> directoryAllNestedTargeting) {
    // 1. Create dimension values map.
    ImmutableMultimap<TargetingDimension, AssetsDirectoryTargeting>
        assetsDirectoryTargetingByDimension =
            getAssetsDirectoryTargetingByDimension(
                directoryAllNestedTargeting.stream()
                    .map(DirectoryNestedTargeting::getTargeting)
                    .collect(toImmutableList()));

    // 2. Create cartesian product of all the dimension values and populate in the universe.
    ImmutableList<AssetsDirectoryTargeting> nestedTargetingUniverse =
        ImmutableList.of(AssetsDirectoryTargeting.getDefaultInstance());
    for (Collection<AssetsDirectoryTargeting> values :
        assetsDirectoryTargetingByDimension.asMap().values()) {
      nestedTargetingUniverse =
          nestedTargetingUniverse.stream()
              .flatMap(
                  targeting ->
                      values.stream()
                          .map(targeting1 -> targeting.toBuilder().mergeFrom(targeting1).build()))
              .collect(toImmutableList());
    }

    // 3. Check that the supportedNestedTargeting covers all points in the universe.
    if (!directoryAllNestedTargeting.stream()
        .map(DirectoryNestedTargeting::getTargeting)
        .collect(toImmutableList())
        .containsAll(nestedTargetingUniverse)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' uses nested targeting but does not define targeting for all of the"
                  + " points in the cartesian product of dimension values used.",
              module.getName())
          .build();
    }
  }

  /** Validates that all the directories/folders have same targeting. */
  private static void validateAllDirectoriesHaveSameNestedTargeting(
      BundleModule module,
      ImmutableSetMultimap<String, DirectoryNestedTargeting>
          directoryAllNestedTargetingByPathBaseName) {
    if (directoryAllNestedTargetingByPathBaseName.asMap().values().stream().distinct().count()
        > 1) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Found directories targeted on different set of targeting dimensions in module '%s'."
                  + " Please make sure all directories are targeted on same set of targeting"
                  + " dimensions in same order.",
              module.getName())
          .build();
    }
  }

  private static void validateNestingDimensionOrder(
      BundleModule module, ImmutableSet<DirectoryNestedTargeting> directoryAllNestedTargeting) {
    ImmutableList<TargetingDimension> targetingDimensionOrderRef =
        directoryAllNestedTargeting.stream()
            .map(DirectoryNestedTargeting::getTargetingDimensionOrder)
            .max(Comparator.comparing(List::size))
            .get();
    ImmutableSet<TargetingDimension> targetingDimensionOrderRefSet =
        ImmutableSet.copyOf(targetingDimensionOrderRef);

    directoryAllNestedTargeting.stream()
        .map(DirectoryNestedTargeting::getTargetingDimensionOrder)
        .forEach(
            dimensionOrder -> {
              if (!targetingDimensionOrderRefSet.containsAll(dimensionOrder)) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Found directory targeted on different set of targeting dimensions in"
                            + " module '%s'. Targeting Used: '%s'. Please make sure all directories"
                            + " are targeted on same set of targeting dimensions in same order.",
                        module.getName(),
                        directoryAllTargetingToString(directoryAllNestedTargeting))
                    .build();
              }
              if (!Comparators.isInOrder(
                  dimensionOrder.stream()
                      .map(targetingDimensionOrderRef::indexOf)
                      .collect(toImmutableList()),
                  naturalOrder())) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Found directory targeted on different order of targeting dimensions in"
                            + " module '%s'. Targeting Used: '%s'. Please make sure all directories"
                            + " are targeted on same set of targeting dimensions in same order.",
                        module.getName(),
                        directoryAllTargetingToString(directoryAllNestedTargeting))
                    .build();
              }
            });
  }

  /**
   * Validates that the nested targeting applied on a directory are distinct i.e. one can not have
   * the directories 'a/b/c#countries_latam#tcf_astc' and 'a/b/c#tcf_astc#countries_latam'
   * simultaneously.
   */
  private static void validateAllDirectoryNestedTargetingAreDistinct(
      BundleModule module,
      ImmutableSetMultimap<String, DirectoryNestedTargeting>
          directoryAllNestedTargetingByPathBaseName) {
    directoryAllNestedTargetingByPathBaseName
        .asMap()
        .forEach(
            (key, value) -> {
              if (value.stream().map(DirectoryNestedTargeting::getTargeting).distinct().count()
                  != value.size()) {
                throw InvalidBundleException.builder()
                    .withUserMessage(
                        "Found multiple directories using same targeting values in module '%s'."
                            + " Directory '%s' is targeted on following dimension values some of"
                            + " which vary only in nesting order: '%s'.",
                        module.getName(),
                        key,
                        directoryAllTargetingToString(ImmutableSet.copyOf(value)))
                    .build();
              }
            });
  }

  private static ImmutableSetMultimap<String, DirectoryNestedTargeting>
      getDirectoryAllNestedTargetingByPathBaseName(BundleModule module) {
    ImmutableList<ZipPath> assetDirectories = getAssetDirectories(module);
    // This map stores all targeting (single & nested) for a path base name.
    LinkedHashMultimap<String, DirectoryNestedTargeting> directoryAllTargetingByPathBaseName =
        LinkedHashMultimap.create();
    FileUtils.toPathWalkingOrder(assetDirectories)
        .forEach(
            assetDirectory -> {
              TargetedDirectory targetedDirectory = TargetedDirectory.parse(assetDirectory);
              directoryAllTargetingByPathBaseName.put(
                  targetedDirectory.getPathBaseName(),
                  DirectoryNestedTargeting.create(
                      targetedDirectory.getLastSegment().getTargeting(),
                      targetedDirectory.getLastSegment().getTargetingDimensionOrder()));
            });

    // This map stores all nested targeting for a path base name.
    LinkedHashMultimap<String, DirectoryNestedTargeting> directoryAllNestedTargetingByPathBaseName =
        LinkedHashMultimap.create();
    directoryAllTargetingByPathBaseName.asMap().entrySet().stream()
        .filter(
            directoryTargeting ->
                targetedOnMultipleDimension(ImmutableList.copyOf(directoryTargeting.getValue())))
        .forEach(
            directoryAllTargeting ->
                directoryAllTargeting.getValue().stream()
                    .forEach(
                        directoryTargeting ->
                            directoryAllNestedTargetingByPathBaseName.put(
                                directoryAllTargeting.getKey(), directoryTargeting)));
    return ImmutableSetMultimap.copyOf(directoryAllNestedTargetingByPathBaseName);
  }

  private static boolean targetedOnMultipleDimension(
      ImmutableList<DirectoryNestedTargeting> directoryNestedTargetingList) {
    return directoryNestedTargetingList.stream()
            .flatMap(
                directoryNestedTargeting ->
                    TargetingUtils.getTargetingDimensions(directoryNestedTargeting.getTargeting())
                        .stream())
            .distinct()
            .count()
        > 1;
  }

  private static String directoryAllTargetingToString(
      ImmutableSet<DirectoryNestedTargeting> directoryAllTargeting) {
    return directoryAllTargeting.stream()
        .map(
            targeting ->
                constructTargetingSegmentPath(
                    targeting.getTargeting(), targeting.getTargetingDimensionOrder()))
        .collect(toImmutableList())
        .toString();
  }

  /** Represents the nested targeting attributed to a directory. */
  @AutoValue
  abstract static class DirectoryNestedTargeting {

    public static NestedTargetingValidator.DirectoryNestedTargeting create(
        AssetsDirectoryTargeting directoryTargeting,
        ImmutableList<TargetingDimension> targetingDimensionOrder) {
      return new AutoValue_NestedTargetingValidator_DirectoryNestedTargeting(
          directoryTargeting, targetingDimensionOrder);
    }

    public abstract AssetsDirectoryTargeting getTargeting();

    public abstract ImmutableList<TargetingDimension> getTargetingDimensionOrder();
  }
}

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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Directory with an optional associated targeting.
 *
 * <p>Each directory within assets can have the "name" or "name#key_value" form. The latter form
 * allows to associate the targeting to the directory.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class TargetedDirectory {

  public abstract ImmutableList<TargetedDirectorySegment> getPathSegments();

  public abstract ZipPath originalPath();

  public TargetedDirectorySegment getLastSegment() {
    return Iterables.getLast(getPathSegments());
  }

  /**
   * Returns the full name of the path with the last segment stripped of the key and value if
   * present.
   *
   * <p>Used to identify alternatives and perform type checking.
   */
  public String getPathBaseName() {
    return getSubPathBaseName(getPathSegments().size() - 1);
  }

  /**
   * Returns the base name of the subpath composed from the first element up to and including the
   * given element index.
   *
   * <p>See {@link ZipPath#subpath} and {@link TargetedDirectory#getPathBaseName}.
   */
  public String getSubPathBaseName(int maxIndex) {
    return originalPath()
        .subpath(0, maxIndex + 1)
        .resolveSibling(getPathSegments().get(maxIndex).getName())
        .toString();
  }

  /**
   * Returns the base name of the subpath composed from the first element up to and including the
   * element that is targeted by the requested targeting dimension.
   *
   * <p>Example: given directory a/b/c#tier_1/d/e, a request for DEVICE_TIER returns a/b/c.
   *
   * <p>See {@link ZipPath#subpath} and {@link TargetedDirectory#getPathBaseName}.
   */
  public String getSubPathBaseName(TargetingDimension dimension) {
    Optional<Integer> targetedSegmentIndex = getTargetedPathSegmentIndex(dimension);
    return targetedSegmentIndex.isPresent()
        ? getSubPathBaseName(targetedSegmentIndex.get())
        : getPathBaseName();
  }

  /**
   * Returns the value of the targeting for the given dimension, if this dimension is targeted by
   * this directory.
   *
   * @param dimension The dimension for which the targeting must be extracted.
   * @return The targeting for the specified dimension, or an empty optional if not found.
   */
  public Optional<AssetsDirectoryTargeting> getTargeting(TargetingDimension dimension) {
    // We're assuming that dimensions are not duplicated (see checkNoDuplicateDimensions).
    return getPathSegments().stream()
        .filter(segment -> segment.getTargetingDimension().equals(Optional.of(dimension)))
        .map(segment -> segment.getTargeting())
        .collect(toOptional());
  }

  /**
   * Returns a copy of the TargetedDirectory with a targeting dimension removed.
   *
   * @param dimension The dimension to be removed
   * @return A new TargetedDirectory with the specified dimension removed.
   */
  public TargetedDirectory removeTargeting(TargetingDimension dimension) {
    ImmutableList<TargetedDirectorySegment> newSegments =
        getPathSegments().stream()
            .map(segment -> segment.removeTargeting(dimension))
            .collect(toImmutableList());

    // If no changes are made to any segments, return the original immutable object.
    if (getPathSegments().equals(newSegments)) {
      return this;
    }

    return create(newSegments, originalPath());
  }

  /**
   * Parses a given path giving back object that can be used to determine the directory targeting.
   *
   * @param directoryPath relative directory inside the App Bundle module.
   */
  public static TargetedDirectory parse(ZipPath directoryPath) {
    checkArgument(directoryPath.getNameCount() > 0, "Empty paths are not supported.");

    ImmutableList<TargetedDirectorySegment> segments =
        directoryPath.getNames().stream()
            .map(TargetedDirectorySegment::parse)
            .collect(toImmutableList());
    checkNoDuplicateDimensions(segments, directoryPath);

    return TargetedDirectory.create(segments, directoryPath);
  }

  public ZipPath toZipPath() {
    ImmutableList<String> pathSegments =
        getPathSegments().stream()
            .map(TargetedDirectorySegment::toPathSegment)
            .collect(toImmutableList());
    return ZipPath.create(pathSegments);
  }

  private Optional<Integer> getTargetedPathSegmentIndex(TargetingDimension dimension) {
    // We're assuming that dimensions are not duplicated (see checkNoDuplicateDimensions).
    return IntStream.range(0, getPathSegments().size())
        .filter(
            idx ->
                getPathSegments().get(idx).getTargetingDimension().equals(Optional.of(dimension)))
        .boxed()
        .collect(toOptional());
  }

  private static void checkNoDuplicateDimensions(
      ImmutableList<TargetedDirectorySegment> directorySegments, ZipPath directoryPath) {
    Set<TargetingDimension> coveredDimensions = new HashSet<>();
    for (TargetedDirectorySegment targetedDirectorySegment : directorySegments) {
      if (targetedDirectorySegment.getTargetingDimension().isPresent()) {
        TargetingDimension lastSegmentDimension =
            targetedDirectorySegment.getTargetingDimension().get();
        if (coveredDimensions.contains(lastSegmentDimension)) {
          throw InvalidBundleException.builder()
              .withUserMessage(
                  "Duplicate targeting dimension '%s' on path '%s'.",
                  lastSegmentDimension, directoryPath)
              .build();
        }
        coveredDimensions.add(lastSegmentDimension);
      }
    }
  }

  private static TargetedDirectory create(
      ImmutableList<TargetedDirectorySegment> segments, ZipPath originalPath) {
    return new AutoValue_TargetedDirectory(segments, originalPath);
  }
}

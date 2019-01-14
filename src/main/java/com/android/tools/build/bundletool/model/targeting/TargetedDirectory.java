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

import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.errorprone.annotations.Immutable;
import java.util.HashSet;
import java.util.Set;

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
   * Parses a given path giving back object that can be used to determine the directory targeting.
   *
   * @param directoryPath relative directory inside the App Bundle module.
   */
  public static TargetedDirectory parse(ZipPath directoryPath) {
    checkArgument(directoryPath.getNameCount() > 0, "Empty paths are not supported.");

    ImmutableList<TargetedDirectorySegment> segments =
        Streams.stream(directoryPath)
            .map(path -> TargetedDirectorySegment.parse((ZipPath) path))
            .collect(toImmutableList());
    checkNoDuplicateDimensions(segments, directoryPath);

    return TargetedDirectory.create(segments, directoryPath);
  }

  private static void checkNoDuplicateDimensions(
      ImmutableList<TargetedDirectorySegment> directorySegments, ZipPath directoryPath) {
    Set<TargetingDimension> coveredDimensions = new HashSet<>();
    for (TargetedDirectorySegment targetedDirectorySegment : directorySegments) {
      if (targetedDirectorySegment.getTargetingDimension().isPresent()) {
        TargetingDimension lastSegmentDimension =
            targetedDirectorySegment.getTargetingDimension().get();
        if (coveredDimensions.contains(lastSegmentDimension)) {
          throw ValidationException.builder()
              .withMessage(
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

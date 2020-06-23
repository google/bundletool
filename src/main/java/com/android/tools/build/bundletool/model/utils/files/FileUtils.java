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

package com.android.tools.build.bundletool.model.utils.files;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/** Misc utilities for working with files. */
public final class FileUtils {

  /** Creates the parent directories of the given path. */
  public static void createParentDirectories(Path path) {
    createDirectories(path.getParent());
  }

  /** Creates the given directory (including parents) with the given path. */
  public static void createDirectories(Path dir) {
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new UncheckedIOException(String.format("Error creating directories '%s'.", dir), e);
    }
  }

  /** Gets distinct parents for given paths. */
  public static ImmutableList<Path> getDistinctParentPaths(Collection<Path> paths) {
    return paths.stream().map(Path::getParent).distinct().collect(toImmutableList());
  }

  /** Gets the extension of the path file. */
  public static String getFileExtension(ZipPath path) {
    if (path.getNameCount() == 0) {
      return "";
    }
    ZipPath name = path.getFileName();

    // null for empty paths and root-only paths
    if (name == null) {
      return "";
    }

    String fileName = name.toString();
    int dotIndex = fileName.lastIndexOf('.');
    return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
  }

  /**
   * For a given collection of paths returns the order of traversal of their hierarchy tree.
   *
   * <p>The returned order assumes the path walk starts at the root and will also return non-leaf
   * path nodes of the given paths. The order of traversal is lexicographical and is stable.
   */
  public static List<ZipPath> toPathWalkingOrder(Collection<ZipPath> paths) {
    ImmutableSortedSet.Builder<ZipPath> walkOrderedSet = ImmutableSortedSet.naturalOrder();
    for (ZipPath path : paths) {
      for (int i = 0; i < path.getNameCount(); i++) {
        walkOrderedSet.add(path.subpath(0, i + 1));
      }
    }
    return walkOrderedSet.build().asList();
  }

  // Not meant to be instantiated.
  private FileUtils() {}
}

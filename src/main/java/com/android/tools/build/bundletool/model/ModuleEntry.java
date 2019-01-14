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
package com.android.tools.build.bundletool.model;

import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Represents an entry in a an App Bundle's module.
 *
 * <p>All subclasses should be immutable, and we assume that they are as long as the data source
 * backing this entry remains unchanged.
 */
@Immutable
public interface ModuleEntry {

  /**
   * Returns the content of the entry as a stream of bytes.
   *
   * <p>Each implementation should strongly consider returning {@link java.io.BufferedInputStream}
   * for efficiency.
   */
  @MustBeClosed
  InputStream getContent();

  @SuppressWarnings("MustBeClosedChecker") // InputStreamSupplier is annotated with @MustBeClosed
  default InputStreamSupplier getContentSupplier() {
    return () -> getContent();
  }

  /** Path of the entry inside the module. */
  ZipPath getPath();

  /** Whether the entry is a directory. */
  boolean isDirectory();

  boolean shouldCompress();

  /**
   * Creates a new instance if passed shouldCompress doesnt match object's shouldCompress, otherwise
   * returns original object.
   */
  ModuleEntry setCompression(boolean shouldCompress);

  /** Checks whether the given entries are identical. */
  static boolean equal(ModuleEntry entry1, ModuleEntry entry2) {
    if (!entry1.getPath().equals(entry2.getPath())) {
      return false;
    }

    if (entry1.isDirectory() != entry2.isDirectory()) {
      return false;
    } else if (entry1.isDirectory() && entry2.isDirectory()) {
      return true;
    }

    try (InputStream inputStream1 = entry1.getContent();
        InputStream inputStream2 = entry2.getContent()) {
      return FileUtils.equalContent(inputStream1, inputStream2);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "Failed to compare contents of module entries '%s' and '%s'.",
              entry1.getPath(), entry2.getPath()),
          e);
    }
  }
}

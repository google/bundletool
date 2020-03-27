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

package com.android.tools.build.bundletool.model;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Various factory methods for {@link InputStreamSupplier}. */
public final class InputStreamSuppliers {
  private InputStreamSuppliers() {}

  /**
   * Creates an {@link InputStreamSupplier} from a file entry in {@code zipFile}.
   *
   * <p>It is the responsibility of the caller to ensure that the referenced {@link ZipFile} stays
   * opened (and immutable) for the lifetime of the {@link InputStreamSupplier} instance.
   */
  public static InputStreamSupplier fromZipEntry(ZipEntry zipEntry, ZipFile zipFile) {
    checkArgument(!zipEntry.getName().isEmpty(), "Path is empty");
    checkArgument(
        !zipEntry.isDirectory(), "Expected file, found directory: %s", zipEntry.getName());
    return BufferedIo.inputStreamSupplier(zipFile, zipEntry);
  }

  /** Create an in-memory {@link InputStreamSupplier} from {@code contents}. */
  public static InputStreamSupplier fromBytes(byte[] contents) {
    final byte[] contentsCopy = Arrays.copyOf(contents, contents.length);
    return () -> new ByteArrayInputStream(contentsCopy);
  }

  /**
   * Creates an {@link InputStreamSupplier} which points to a file located on the filesystem.
   *
   * <p>It is the responsibility of the caller to ensure that the referenced file exists for the
   * lifetime of the {@link InputStreamSupplier} instance.
   *
   * <p>Always reflects current content of the underlying file.
   *
   * @param fileSystemPath path of the underlying file
   */
  public static InputStreamSupplier fromFile(Path fileSystemPath) {
    checkArgument(
        Files.isRegularFile(fileSystemPath),
        "Expecting '%s' to be an existing regular file.",
        fileSystemPath);
    return BufferedIo.inputStreamSupplier(fileSystemPath);
  }
}

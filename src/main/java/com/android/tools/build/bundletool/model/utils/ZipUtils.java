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

package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.model.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.not;

import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.base.Optional;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Misc utilities for working with zip files. */
public final class ZipUtils {

  public static Stream<ZipPath> allFileEntriesPaths(ZipFile zipFile) {
    return allFileEntries(zipFile).map(zipEntry -> ZipPath.create(zipEntry.getName()));
  }

  public static Stream<? extends ZipEntry> allFileEntries(ZipFile zipFile) {
    return zipFile.stream().filter(not(ZipEntry::isDirectory));
  }

  public static ZipFile openZipFile(Path path) {
    checkFileExistsAndReadable(path);
    try {
      return new ZipFile(path.toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(String.format("Error reading zip file '%s'.", path), e);
    }
  }

  /**
   * Converts a path relative to the bundle zip root to one relative to the module path.
   *
   * <p>In the bundle zip, top-level directories denote module names (eg.
   * "module_name/res/drawable.icon"), hence 1 path name needs to be skipped when resolving relative
   * path of a module entry.
   */
  public static ZipPath convertBundleToModulePath(ZipPath bundlePath) {
    return bundlePath.subpath(1, bundlePath.getNameCount());
  }

  /**
   * Returns a new {@link ByteSource} for reading the contents of the given entry in the given zip
   * file.
   */
  public static ByteSource asByteSource(ZipFile file, ZipEntry entry) {
    return new ZipEntryByteSource(file, entry);
  }

  private static final class ZipEntryByteSource extends ByteSource {
    private final ZipFile file;
    private final ZipEntry entry;

    ZipEntryByteSource(ZipFile file, ZipEntry entry) {
      this.file = checkNotNull(file);
      this.entry = checkNotNull(entry);
    }

    @Override
    public InputStream openStream() throws IOException {
      return file.getInputStream(entry);
    }

    @Override
    public Optional<Long> sizeIfKnown() {
      return entry.getSize() == -1 ? Optional.absent() : Optional.of(entry.getSize());
    }

    @Override
    public String toString() {
      return "ZipUtils.asByteSource(" + file + ", " + entry + ")";
    }
  }

  // Not meant to be instantiated.
  private ZipUtils() {}
}

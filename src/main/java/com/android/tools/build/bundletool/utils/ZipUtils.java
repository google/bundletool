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

package com.android.tools.build.bundletool.utils;

import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileExistsAndReadable;
import static com.google.common.base.Predicates.not;

import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.ZipPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Misc utilities for working with zip files. */
public final class ZipUtils {

  /**
   * From the specified zip file extracts paths to all file entries that are under the given path.
   */
  public static Stream<ZipPath> getFilesWithPathPrefix(ZipFile zipFile, ZipPath prefix) {
    return getFileEntriesWithPathPrefix(zipFile, prefix)
        .map(ZipEntry::getName)
        .map(ZipPath::create);
  }

  /**
   * From the specified zip file returns all non-directory {@link ZipEntry} matching the given path
   * prefix.
   */
  public static Stream<? extends ZipEntry> getFileEntriesWithPathPrefix(
      ZipFile zipFile, ZipPath prefix) {
    return zipFile
        .stream()
        .filter(not(ZipEntry::isDirectory))
        .filter(entry -> ZipPath.create(entry.getName()).startsWith(prefix));
  }

  public static ZipFile openZipFile(Path path) {
    checkFileExistsAndReadable(path);
    try {
      return new ZipFile(path.toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(String.format("Error reading zip file '%s'.", path), e);
    }
  }

  public static void closeZipFiles(Collection<ZipFile> zipFiles) {
    IOException thrown = null;
    for (ZipFile zipFile : zipFiles) {
      try {
        zipFile.close();
      } catch (IOException e) {
        thrown = e;
      }
    }
    if (thrown != null) {
      throw new ValidationException("Error closing zip files.", thrown);
    }
  }

  // Not meant to be instantiated.
  private ZipUtils() {}
}

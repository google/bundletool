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

import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.WillNotClose;

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

  /** Calculates the GZip compressed size in bytes of the target {@code stream}. */
  public static long calculateGzipCompressedSize(@WillNotClose InputStream stream)
      throws IOException {
    CountingOutputStream countingOutputStream =
        new CountingOutputStream(ByteStreams.nullOutputStream());
    try (GZIPOutputStream compressedStream = new GZIPOutputStream(countingOutputStream)) {
      ByteStreams.copy(stream, compressedStream);
    }
    return countingOutputStream.getCount();
  }

  // Not meant to be instantiated.
  private ZipUtils() {}
}

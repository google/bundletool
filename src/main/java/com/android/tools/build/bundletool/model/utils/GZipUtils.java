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

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;
import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import javax.annotation.WillNotClose;

/** Misc utilities for gzipping files. */
public final class GZipUtils {

  /** Calculates the GZip compressed size in bytes of the target {@code file}. */
  public static long calculateGzipCompressedSize(Path file) throws IOException {
    return calculateGzipCompressedSize(MoreFiles.asByteSource(file));
  }

  /** Calculates the GZip compressed size in bytes of the target {@code file}. */
  public static long calculateGzipCompressedSize(File file) throws IOException {
    return calculateGzipCompressedSize(Files.asByteSource(file));
  }

  /** Calculates the GZip compressed size in bytes of the target {@code stream}. */
  public static long calculateGzipCompressedSize(ByteSource byteSource) throws IOException {
    try (InputStream is = byteSource.openStream()) {
      return calculateGzipCompressedSize(is);
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

  private GZipUtils() {}
}

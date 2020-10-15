/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.build.bundletool.io;

import com.android.tools.build.bundletool.model.CompressionLevel;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.zipflinger.Entry;
import java.io.IOException;

/**
 * Factory to build {@link ZipEntrySource} for a given zip file.
 *
 * <p>This is just a convenience class to avoid passing the same {@link ZipReader} and {@link
 * TempDirectory} instances every time we want to build a new {@link ZipEntrySource}.
 */
public final class ZipEntrySourceFactory {

  private final ZipReader zipReader;
  private final TempDirectory tempDirectory;

  /**
   * Builds a factory of {@link ZipEntrySource} where all created objects will share the given zip
   * file as source.
   */
  public ZipEntrySourceFactory(ZipReader zipReader, TempDirectory tempDirectory) {
    this.zipReader = zipReader;
    this.tempDirectory = tempDirectory;
  }

  /**
   * Creates a {@link ZipEntrySource} for the given zip entry that will be compressed with the given
   * compression level.
   */
  public ZipEntrySource create(Entry entry, CompressionLevel compressionLevel) throws IOException {
    return ZipEntrySource.create(zipReader, entry, compressionLevel, tempDirectory);
  }

  /**
   * Creates a {@link ZipEntrySource} for the given zip entry that will be compressed with the given
   * compression level.
   *
   * <p>When serialized, the entry will be given the name {@code newEntryName}.
   */
  public ZipEntrySource create(Entry entry, ZipPath newEntryName, CompressionLevel compressionLevel)
      throws IOException {
    return ZipEntrySource.create(zipReader, entry, newEntryName, compressionLevel, tempDirectory);
  }
}

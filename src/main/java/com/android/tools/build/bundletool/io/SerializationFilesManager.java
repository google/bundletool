/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.google.common.io.Closeables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

/** Manages temp directory and all files opened during APK serialization. */
class SerializationFilesManager implements AutoCloseable {
  private static final String RESOURCES_ENTRIES_ZIPPED_PACK = "resources-pack.zip";
  private static final String COMPRESSED_ENTRIES_PACK = "compressed.zip";
  private static final String COMPRESSED_RESOURCE_ENTRIES_PACK = "compressed-res.zip";
  private static final String UNCOMPRESSED_ENTRIES_PACK = "uncompressed.zip";

  private final TempDirectory tempDirectory = new TempDirectory();
  private final AtomicInteger counter = new AtomicInteger();
  private final ConcurrentHashMap<Path, ZipFile> openedBinaryApks = new ConcurrentHashMap<>();

  @Override
  public void close() throws IOException {
    for (ZipFile zipFile : openedBinaryApks.values()) {
      Closeables.close(zipFile, /* swallowIOException= */ true);
    }
    tempDirectory.close();
  }

  Path getRootDirectory() {
    return tempDirectory.getPath();
  }

  Path getResourcesEntriesPackPath() {
    return tempDirectory.getPath().resolve(RESOURCES_ENTRIES_ZIPPED_PACK);
  }

  Path getCompressedResourceEntriesPackPath() {
    return tempDirectory.getPath().resolve(COMPRESSED_RESOURCE_ENTRIES_PACK);
  }

  Path getCompressedEntriesPackPath() {
    return tempDirectory.getPath().resolve(COMPRESSED_ENTRIES_PACK);
  }

  Path getUncompressedEntriesPackPath() {
    return tempDirectory.getPath().resolve(UNCOMPRESSED_ENTRIES_PACK);
  }

  Path getNextAapt2ProtoApkPath() {
    return tempDirectory.getPath().resolve("proto" + counter.incrementAndGet() + ".apk");
  }

  Path getNextAapt2BinaryApkPath() {
    return tempDirectory.getPath().resolve("binary" + counter.incrementAndGet() + ".apk");
  }

  ZipFile openBinaryApk(Path zipPath) {
    return openedBinaryApks.computeIfAbsent(zipPath, ZipUtils::openZipFile);
  }

  void closeAndRemoveBinaryApks() throws IOException {
    for (Entry<Path, ZipFile> opened : openedBinaryApks.entrySet()) {
      Closeables.close(opened.getValue(), /* swallowIOException= */ true);
      Files.delete(opened.getKey());
    }
    openedBinaryApks.clear();
  }
}

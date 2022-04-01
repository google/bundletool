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

import com.android.tools.build.bundletool.androidtools.P7ZipCommand;
import com.android.tools.build.bundletool.model.utils.SystemEnvironmentProvider;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.android.zipflinger.Source;
import com.android.zipflinger.Sources;
import com.android.zipflinger.ZipArchive;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.zip.Deflater;

/** Interface to zip pairs of name/content to zip archive. */
interface Zipper {
  /**
   * Threshold for an entry size below which we consider that compressing its payload in a separate
   * thread would not provide any speed benefit.
   *
   * <p>Magic number found empirically. Can be overridden using the system property
   * "bundletool.compression.newthread.entrysize".
   */
  long LARGE_ENTRY_SIZE_THRESHOLD_BYTES =
      SystemEnvironmentProvider.DEFAULT_PROVIDER
          .getProperty("bundletool.compression.newthread.entrysize")
          .map(Long::parseLong)
          .orElse(100_000L);

  /** Zips pairs of name/content into zip archive. */
  void zip(Path outputZip, ImmutableMap<String, ByteSource> entries);

  /** Creates instance of {@link Zipper} which creates ZIP with uncompressed entries. */
  static Zipper uncompressedZip() {
    return (outputZip, entries) -> {
      try (ZipArchive archive = new ZipArchive(outputZip)) {
        for (Map.Entry<String, ByteSource> entry : entries.entrySet()) {
          archive.add(
              Sources.from(entry.getValue().openStream(), entry.getKey(), Deflater.NO_COMPRESSION));
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  /** Creates instance of {@link Zipper} which creates ZIP with compressed entries. */
  static Zipper compressedZip(ListeningExecutorService executorService, int compressionLevel) {
    return (outputZip, entries) -> {
      try (ZipArchive archive = new ZipArchive(outputZip)) {
        ImmutableList.Builder<ListenableFuture<Source>> largeSources = ImmutableList.builder();
        for (Map.Entry<String, ByteSource> entry : entries.entrySet()) {
          String path = entry.getKey();
          ByteSource content = entry.getValue();
          boolean smallEntry =
              content
                  .sizeIfKnown()
                  .transform(size -> size < LARGE_ENTRY_SIZE_THRESHOLD_BYTES)
                  .or(false);

          if (smallEntry) {
            archive.add(Sources.from(content.openStream(), path, compressionLevel));
          } else {
            largeSources.add(
                executorService.submit(
                    () -> Sources.from(content.openStream(), path, compressionLevel)));
          }
        }
        for (Future<Source> source : Futures.inCompletionOrder(largeSources.build())) {
          archive.add(Futures.getUnchecked(source));
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  /** Creates instance of {@link Zipper} which creates ZIP using 7zip command-line tool. */
  static Zipper compressedZip(P7ZipCommand p7ZipCommand, Path tempDirectory) {
    return (outputZip, entries) -> {
      try {
        FileUtils.createDirectories(tempDirectory);
        for (Entry<String, ByteSource> content : entries.entrySet()) {
          content.getValue().copyTo(MoreFiles.asByteSink(tempDirectory.resolve(content.getKey())));
        }
        p7ZipCommand.compress(outputZip, tempDirectory);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.build.bundletool.size;

import static com.android.tools.build.bundletool.size.SizeUtils.addSizes;
import static com.android.tools.build.bundletool.size.SizeUtils.sizes;
import static com.android.tools.build.bundletool.size.SizeUtils.subtractSizes;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.bundle.SizesOuterClass.Breakdown;
import com.android.bundle.SizesOuterClass.Sizes;
import com.android.tools.build.bundletool.model.InputStreamSupplier;
import com.android.tools.build.bundletool.model.InputStreamSuppliers;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Calculates a breakdown of a single APK. */
public class ApkBreakdownGenerator {

  // Each time we add an entry to the deflater a syncronization entry is added.
  // This would not be present when we acually compress the APK for serving, it's just an artifact
  // of flushing after each file.
  static final int DEFLATER_SYNC_OVERHEAD_BYTES = 5;

  public static Breakdown calculateBreakdown(Path apkPath) throws IOException {
    try (ZipFile apk = new ZipFile(apkPath.toFile())) {
      ImmutableMap<String, Long> downloadSizeByEntry = calculateDownloadSizePerEntry(apk);

      ImmutableMap<ApkComponent, Long> downloadSizeByComponent =
          downloadSizeByEntry.entrySet().stream()
              .collect(
                  Collectors.collectingAndThen(
                      Collectors.groupingBy(
                          entry -> ApkComponent.fromEntryName(entry.getKey()),
                          Collectors.summingLong(Map.Entry<String, Long>::getValue)),
                      ImmutableMap::copyOf));

      ImmutableMap<ApkComponent, Long> diskSizeByComponent =
          apk.stream()
              .collect(
                  Collectors.collectingAndThen(
                      Collectors.groupingBy(
                          zipEntry -> ApkComponent.fromEntryName(zipEntry.getName()),
                          Collectors.summingLong(ZipEntry::getCompressedSize)),
                      ImmutableMap::copyOf));

      Sizes actualTotalSize = calculateActualTotals(apkPath);
      Sizes zipOverheads =
          subtractSizes(
              actualTotalSize,
              sizes(
                  diskSizeByComponent.values().stream().mapToLong(Long::longValue).sum(),
                  downloadSizeByComponent.values().stream().mapToLong(Long::longValue).sum()));

      return Breakdown.newBuilder()
          .setDex(getSizes(ApkComponent.DEX, diskSizeByComponent, downloadSizeByComponent))
          .setAssets(getSizes(ApkComponent.ASSETS, diskSizeByComponent, downloadSizeByComponent))
          .setNativeLibs(
              getSizes(ApkComponent.NATIVE_LIBS, diskSizeByComponent, downloadSizeByComponent))
          .setResources(
              getSizes(ApkComponent.RESOURCES, diskSizeByComponent, downloadSizeByComponent))
          .setOther(
              addSizes(
                  getSizes(ApkComponent.OTHER, diskSizeByComponent, downloadSizeByComponent),
                  zipOverheads))
          .setTotal(actualTotalSize)
          .build();
    }
  }

  private static Sizes calculateActualTotals(Path apkPath) throws IOException {
    try (InputStream inputStream = new FileInputStream(apkPath.toFile());
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream)) {
      return sizes(Files.size(apkPath), ZipUtils.calculateGzipCompressedSize(bufferedStream));
    }
  }

  private static Sizes getSizes(
      ApkComponent component,
      Map<ApkComponent, Long> diskSizes,
      Map<ApkComponent, Long> downloadSizes) {
    return sizes(diskSizes.getOrDefault(component, 0L), downloadSizes.getOrDefault(component, 0L));
  }

  private static ImmutableMap<String, Long> calculateDownloadSizePerEntry(ZipFile zipFile)
      throws IOException {

    ImmutableList<InputStreamSupplier> streams =
        zipFile.stream()
            .map(zipStreamEntry -> InputStreamSuppliers.fromZipEntry(zipStreamEntry, zipFile))
            .collect(toImmutableList());

    ImmutableList<Long> downloadSizes = calculateGZipSizeForEntries(streams);

    return Streams.zip(zipFile.stream(), downloadSizes.stream(), AbstractMap.SimpleEntry::new)
        .collect(
            toImmutableMap(entry -> entry.getKey().getName(), AbstractMap.SimpleEntry::getValue));
  }

  /**
   * Given a list of {@link InputStreamSupplier} passes those streams through a {@link
   * GZIPOutputStream} and computes the GZIP size increments attributed to each stream.
   */
  public static ImmutableList<Long> calculateGZipSizeForEntries(
      ImmutableList<InputStreamSupplier> streams) throws IOException {
    ImmutableList.Builder<Long> gzipSizeIncrements = ImmutableList.builder();

    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, /* noWrap */ true);

    try (Closeable unused = () -> deflater.end()) {
      // matches the {@code ByteStreams} buffer size
      byte[] inputBuffer = new byte[8192];

      // Worse case overestimate for the max size deflation should result it.
      // (most of the time deflation should result in a smaller output, but there are cases
      // where it can be larger).
      byte[] outputBuffer = new byte[2 * inputBuffer.length];

      for (InputStreamSupplier stream : streams) {
        try (InputStream is = stream.get()) {
          long gzipSize = 0;
          while (true) {
            int r = is.read(inputBuffer);
            if (r == -1) {
              // We need to use syncFlush which is slower but allows us to accurately count GZIP
              // bytes. See {@link Deflater#SYNC_FLUSH}. Sync-flush flushes all deflater's pending
              // output upon calling flush().
              gzipSize +=
                  deflater.deflate(outputBuffer, 0, outputBuffer.length, Deflater.SYNC_FLUSH);
              gzipSizeIncrements.add(Math.max(0, gzipSize - DEFLATER_SYNC_OVERHEAD_BYTES));
              break;
            }
            deflater.setInput(inputBuffer, 0, r);
            while (!deflater.needsInput()) {
              gzipSize += deflater.deflate(outputBuffer, 0, outputBuffer.length, Deflater.NO_FLUSH);
            }
          }
        }
      }
    }
    return gzipSizeIncrements.build();
  }

  private ApkBreakdownGenerator() {}
}

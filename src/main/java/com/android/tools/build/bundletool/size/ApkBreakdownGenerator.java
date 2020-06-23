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

import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingByDeterministic;
import static com.android.tools.build.bundletool.size.SizeUtils.addSizes;
import static com.android.tools.build.bundletool.size.SizeUtils.sizes;
import static com.android.tools.build.bundletool.size.SizeUtils.subtractSizes;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.bundle.SizesOuterClass.Breakdown;
import com.android.bundle.SizesOuterClass.Sizes;
import com.android.tools.build.bundletool.model.utils.GZipUtils;
import com.android.tools.build.bundletool.model.utils.ZipUtils;
import com.android.tools.build.bundletool.size.ApkCompressedSizeCalculator.JavaUtilZipDeflater;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Calculates a breakdown of a single APK. */
public final class ApkBreakdownGenerator {

  private final ApkCompressedSizeCalculator compressedSizeCalculator;

  public ApkBreakdownGenerator() {
    this(new ApkCompressedSizeCalculator(JavaUtilZipDeflater::new));
  }

  private ApkBreakdownGenerator(ApkCompressedSizeCalculator compressedSizeCalculator) {
    this.compressedSizeCalculator = compressedSizeCalculator;
  }

  public Breakdown calculateBreakdown(Path apkPath) throws IOException {
    try (ZipFile apk = new ZipFile(apkPath.toFile())) {
      ImmutableMap<String, Long> downloadSizeByEntry = calculateDownloadSizePerEntry(apk);

      ImmutableMap<ApkComponent, Long> downloadSizeByComponent =
          downloadSizeByEntry.entrySet().stream()
              .collect(
                      groupingByDeterministic(
                          entry -> ApkComponent.fromEntryName(entry.getKey()),
                          Collectors.summingLong(Map.Entry<String, Long>::getValue)));

      ImmutableMap<ApkComponent, Long> diskSizeByComponent =
          apk.stream()
              .collect(
                  groupingByDeterministic(
                      zipEntry -> ApkComponent.fromEntryName(zipEntry.getName()),
                      Collectors.summingLong(ZipEntry::getCompressedSize)));

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
    return sizes(Files.size(apkPath), GZipUtils.calculateGzipCompressedSize(apkPath));
  }

  private static Sizes getSizes(
      ApkComponent component,
      Map<ApkComponent, Long> diskSizes,
      Map<ApkComponent, Long> downloadSizes) {
    return sizes(diskSizes.getOrDefault(component, 0L), downloadSizes.getOrDefault(component, 0L));
  }

  private ImmutableMap<String, Long> calculateDownloadSizePerEntry(ZipFile zipFile)
      throws IOException {

    ImmutableList<ByteSource> streams =
        zipFile.stream()
            .map(zipStreamEntry -> ZipUtils.asByteSource(zipFile, zipStreamEntry))
            .collect(toImmutableList());

    ImmutableList<Long> downloadSizes =
        compressedSizeCalculator.calculateGZipSizeForEntries(streams);

    return Streams.zip(zipFile.stream(), downloadSizes.stream(), AbstractMap.SimpleEntry::new)
        .collect(
            toImmutableMap(entry -> entry.getKey().getName(), AbstractMap.SimpleEntry::getValue));
  }
}

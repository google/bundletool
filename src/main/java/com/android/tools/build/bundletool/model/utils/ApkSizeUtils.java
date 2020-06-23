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

package com.android.tools.build.bundletool.model.utils;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.Variant;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Utils for calculating APK sizes inside APK Sets. */
public class ApkSizeUtils {

  /**
   * Returns a map of APK Paths inside the APK Set with the sizes, for all APKs in variants
   * provided.
   */
  public static ImmutableMap<String, Long> getVariantCompressedSizeByApkPaths(
      ImmutableList<Variant> variants, Path apksArchive) {
    ImmutableList<String> apkPaths =
        variants.stream()
            .flatMap(variant -> variant.getApkSetList().stream())
            .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
            .map(ApkDescription::getPath)
            .distinct()
            .collect(toImmutableList());
    return getCompressedSizeByApkPaths(apkPaths, apksArchive);
  }

  public static ImmutableMap<String, Long> getCompressedSizeByApkPaths(
      ImmutableList<String> apkPaths, Path apksArchive) {
    ImmutableMap.Builder<String, Long> sizeByApkPath = ImmutableMap.builder();
    try (ZipFile apksZip = new ZipFile(apksArchive.toFile())) {
      for (String apkPath : apkPaths) {
        ZipEntry entry = checkNotNull(apksZip.getEntry(apkPath));
        // It's possible that the compressed size is larger than the uncompressed one, but the
        // smallest APK is the one that is actually served.
        long size =
            Math.min(
                entry.getSize(),
                GZipUtils.calculateGzipCompressedSize(ZipUtils.asByteSource(apksZip, entry)));
        sizeByApkPath.put(apkPath, size);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while processing the APK Set archive '%s'.", apksArchive), e);
    }
    return sizeByApkPath.build();
  }

  private ApkSizeUtils() {}
}

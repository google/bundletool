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

import static com.android.tools.build.bundletool.model.utils.FileNames.TABLE_OF_CONTENTS_FILE;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.Variant;
import com.android.tools.build.bundletool.model.utils.files.BufferedIo;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Utility class for result objects returned by bundletool commands. */
public final class ResultUtils {

  public static BuildApksResult readTableOfContents(Path apksPath) {
    try {
      if (Files.isDirectory(apksPath)) {
        return readTableOfContentFromApksDirectory(apksPath);
      } else {
        return readTableOfContentFromApksArchive(apksPath);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while reading the table of contents file from '%s'.", apksPath), e);
    }
  }

  private static BuildApksResult readTableOfContentFromApksArchive(Path apksArchivePath)
      throws IOException {
    try (ZipFile apksArchive = new ZipFile(apksArchivePath.toFile());
        InputStream tocStream =
            BufferedIo.inputStream(apksArchive, new ZipEntry(TABLE_OF_CONTENTS_FILE))) {
      return BuildApksResult.parseFrom(tocStream);
    }
  }

  private static BuildApksResult readTableOfContentFromApksDirectory(Path apksDirectoryPath)
      throws IOException {
    try (FileInputStream fileInputStream =
        new FileInputStream(apksDirectoryPath.resolve(TABLE_OF_CONTENTS_FILE).toFile())) {
      return BuildApksResult.parseFrom(fileInputStream);
    }
  }

  public static ImmutableList<Variant> splitApkVariants(BuildApksResult result) {
    return splitApkVariants(ImmutableList.copyOf(result.getVariantList()));
  }

  public static ImmutableList<Variant> splitApkVariants(ImmutableList<Variant> variants) {
    return variants.stream().filter(ResultUtils::isSplitApkVariant).collect(toImmutableList());
  }

  public static ImmutableList<Variant> instantApkVariants(BuildApksResult result) {
    return instantApkVariants(ImmutableList.copyOf(result.getVariantList()));
  }

  public static ImmutableList<Variant> instantApkVariants(ImmutableList<Variant> variants) {
    return variants.stream().filter(ResultUtils::isInstantApkVariant).collect(toImmutableList());
  }

  public static ImmutableList<Variant> standaloneApkVariants(BuildApksResult result) {
    return standaloneApkVariants(ImmutableList.copyOf(result.getVariantList()));
  }

  public static ImmutableList<Variant> standaloneApkVariants(ImmutableList<Variant> variants) {
    return variants.stream().filter(ResultUtils::isStandaloneApkVariant).collect(toImmutableList());
  }

  public static ImmutableList<Variant> apexApkVariants(BuildApksResult result) {
    return apexApkVariants(ImmutableList.copyOf(result.getVariantList()));
  }

  public static ImmutableList<Variant> apexApkVariants(ImmutableList<Variant> variants) {
    return variants.stream().filter(ResultUtils::isApexApkVariant).collect(toImmutableList());
  }

  public static ImmutableList<Variant> systemApkVariants(BuildApksResult result) {
    return systemApkVariants(ImmutableList.copyOf(result.getVariantList()));
  }

  public static ImmutableList<Variant> systemApkVariants(ImmutableList<Variant> variants) {
    return variants.stream()
        .filter(variant -> !isStandaloneApkVariant(variant))
        .collect(toImmutableList());
  }

  public static boolean isSplitApkVariant(Variant variant) {
    return variant
        .getApkSetList()
        .stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasSplitApkMetadata);
  }

  public static boolean isStandaloneApkVariant(Variant variant) {
    return variant
        .getApkSetList()
        .stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasStandaloneApkMetadata);
  }

  public static boolean isApexApkVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasApexApkMetadata);
  }

  public static boolean isInstantApkVariant(Variant variant) {
    return variant
        .getApkSetList()
        .stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasInstantApkMetadata);
  }

  public static boolean isSystemApkVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasSystemApkMetadata);
  }

  private ResultUtils() {}
}

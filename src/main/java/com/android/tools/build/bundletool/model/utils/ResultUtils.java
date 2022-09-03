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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.BuildSdkApksResult;
import com.android.bundle.Commands.Variant;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    try (ZipFile apksArchive = new ZipFile(apksArchivePath.toFile())) {
      byte[] tocBytes =
          ZipUtils.asByteSource(apksArchive, new ZipEntry(TABLE_OF_CONTENTS_FILE)).read();
      try {
        return BuildApksResult.parseFrom(tocBytes);
      } catch (InvalidProtocolBufferException e) {
        // If loading the toc.pb into BuildApksResult fails, try to load it into BuildSdksApksResult
        return toBuildApksResult(BuildSdkApksResult.parseFrom(tocBytes));
      }
    }
  }

  private static BuildApksResult readTableOfContentFromApksDirectory(Path apksDirectoryPath)
      throws IOException {
    return BuildApksResult.parseFrom(
        Files.readAllBytes(apksDirectoryPath.resolve(TABLE_OF_CONTENTS_FILE)));
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
    return variants.stream().filter(ResultUtils::isSystemApkVariant).collect(toImmutableList());
  }

  public static ImmutableList<Variant> archivedApkVariants(BuildApksResult result) {
    return result.getVariantList().stream()
        .filter(ResultUtils::isArchivedApkVariant)
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
    return variant
        .getApkSetList()
        .stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .anyMatch(ApkDescription::hasSystemApkMetadata);
  }

  public static boolean isArchivedApkVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .anyMatch(ApkDescription::hasArchivedApkMetadata);
  }

  public static ImmutableSet<String> getAllTargetedLanguages(BuildApksResult result) {
    return Streams.concat(
            result.getAssetSliceSetList().stream()
                .flatMap(assetSliceSet -> assetSliceSet.getApkDescriptionList().stream()),
            result.getVariantList().stream()
                .flatMap(variant -> variant.getApkSetList().stream())
                .flatMap(apkSet -> apkSet.getApkDescriptionList().stream()))
        .flatMap(
            apkDescription ->
                apkDescription.getTargeting().getLanguageTargeting().getValueList().stream())
        .collect(toImmutableSet());
  }

  /** Return the paths for all the base master splits in the given {@link BuildApksResult}. */
  public static ImmutableSet<String> getAllBaseMasterSplitPaths(BuildApksResult toc) {
    return splitApkVariants(toc).stream()
        .map(Variant::getApkSetList)
        .flatMap(List::stream)
        .filter(
            apkSet ->
                apkSet
                    .getModuleMetadata()
                    .getName()
                    .equals(BundleModuleName.BASE_MODULE_NAME.getName()))
        .map(ApkSet::getApkDescriptionList)
        .flatMap(List::stream)
        .filter(apkDescription -> apkDescription.getSplitApkMetadata().getIsMasterSplit())
        .map(ApkDescription::getPath)
        .collect(toImmutableSet());
  }

  private static BuildApksResult toBuildApksResult(BuildSdkApksResult result) {
    // Converting BuildSdkApksResult to BuildApksResult allows us to reuse evaluation of shared
    // underlying fields, such as variant, in commands like extract-apks.
    return BuildApksResult.newBuilder()
        .setBundletool(result.getBundletool())
        .setPackageName(result.getPackageName())
        .addAllVariant(result.getVariantList())
        .build();
  }

  private ResultUtils() {}
}

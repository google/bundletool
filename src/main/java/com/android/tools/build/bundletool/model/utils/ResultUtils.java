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
import static com.android.tools.build.bundletool.model.utils.FileNames.TABLE_OF_CONTENTS_JSON_FILE;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.bundle.Commands.ApkDescription;
import com.android.bundle.Commands.ApkSet;
import com.android.bundle.Commands.BuildApksResult;
import com.android.bundle.Commands.BuildSdkApksResult;
import com.android.bundle.Commands.Variant;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
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
      ensureSingleToc(apksPath);
      BuildApksResult result =
          Files.isDirectory(apksPath)
              ? readTableOfContentFromApksDirectory(apksPath)
              : readTableOfContentFromApksArchive(apksPath);
      return applyDefaultValues(result);
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error while reading the table of contents file from '%s'.", apksPath), e);
    }
  }

  private static BuildApksResult readTableOfContentFromApksArchive(Path apksArchivePath)
      throws IOException {
    try (ZipFile apksArchive = new ZipFile(apksArchivePath.toFile())) {
      boolean tocJsonExists = apksArchive.getEntry(TABLE_OF_CONTENTS_JSON_FILE) != null;
      byte[] tocBytes =
          tocJsonExists
              ? ZipUtils.asByteSource(apksArchive, new ZipEntry(TABLE_OF_CONTENTS_JSON_FILE)).read()
              : ZipUtils.asByteSource(apksArchive, new ZipEntry(TABLE_OF_CONTENTS_FILE)).read();
      return tocJsonExists ? parseJsonToc(tocBytes) : parseProtoToc(tocBytes);
    }
  }

  private static BuildApksResult readTableOfContentFromApksDirectory(Path apksDirectoryPath)
      throws IOException {
    boolean tocJsonExists = Files.exists(apksDirectoryPath.resolve(TABLE_OF_CONTENTS_JSON_FILE));
    byte[] tocBytes =
        tocJsonExists
            ? Files.readAllBytes(apksDirectoryPath.resolve(TABLE_OF_CONTENTS_JSON_FILE))
            : Files.readAllBytes(apksDirectoryPath.resolve(TABLE_OF_CONTENTS_FILE));
    return tocJsonExists ? parseJsonToc(tocBytes) : parseProtoToc(tocBytes);
  }

  private static BuildApksResult parseJsonToc(byte[] bytes) throws IOException {
    String jsonToc = new String(bytes, UTF_8);
    BuildApksResult.Builder builder = BuildApksResult.newBuilder();
    JsonFormat.parser().ignoringUnknownFields().merge(jsonToc, builder);
    return builder.build();
  }

  @VisibleForTesting
  static BuildApksResult applyDefaultValues(BuildApksResult buildApksResult) {
    BuildApksResult.Builder builder = buildApksResult.toBuilder();
    if (builder.getBundletool().getVersion().isEmpty()) {
      builder.getBundletoolBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString());
    }
    return builder.build();
  }

  private static BuildApksResult parseProtoToc(byte[] bytes) throws IOException {
    try {
      return BuildApksResult.parseFrom(bytes);
    } catch (InvalidProtocolBufferException e) {
      // If loading the toc.pb into BuildApksResult fails, try to load it into BuildSdksApksResult
      return toBuildApksResult(BuildSdkApksResult.parseFrom(bytes));
    }
  }

  /* Ensures that an apks folder or zip has only one toc. */
  private static void ensureSingleToc(Path file) throws IOException {
    if (Files.isDirectory(file)) {
      if (Files.exists(file.resolve(TABLE_OF_CONTENTS_FILE))
          && Files.exists(file.resolve(TABLE_OF_CONTENTS_JSON_FILE))) {
        throw new IllegalStateException(
            "Apks directory cannot have both toc.pb and toc.json at the same time.");
      }
    } else {
      try (ZipFile apksArchive = new ZipFile(file.toFile())) {
        if (apksArchive.getEntry(TABLE_OF_CONTENTS_FILE) != null
            && apksArchive.getEntry(TABLE_OF_CONTENTS_JSON_FILE) != null) {
          throw new IllegalStateException(
              "Apks archive cannot have both toc.pb and toc.json at the same time.");
        }
      }
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
    return variants.stream().filter(ResultUtils::isSystemApkVariant).collect(toImmutableList());
  }

  public static ImmutableList<Variant> archivedApkVariants(BuildApksResult result) {
    return result.getVariantList().stream()
        .filter(ResultUtils::isArchivedApkVariant)
        .collect(toImmutableList());
  }

  public static boolean isSplitApkVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasSplitApkMetadata);
  }

  public static boolean isStandaloneApkVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasStandaloneApkMetadata);
  }

  public static boolean isApexApkVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasApexApkMetadata);
  }

  public static boolean isInstantApkVariant(Variant variant) {
    return variant.getApkSetList().stream()
        .flatMap(apkSet -> apkSet.getApkDescriptionList().stream())
        .allMatch(ApkDescription::hasInstantApkMetadata);
  }

  public static boolean isSystemApkVariant(Variant variant) {
    return variant.getApkSetList().stream()
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

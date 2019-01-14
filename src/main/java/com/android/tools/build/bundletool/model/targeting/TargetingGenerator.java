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

package com.android.tools.build.bundletool.model.targeting;

import static com.android.tools.build.bundletool.model.BundleModule.ABI_SPLITTER;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.Assets;
import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedApexImage;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.Abi;
import com.android.bundle.Targeting.ApexImageTargeting;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.bundle.Targeting.MultiAbi;
import com.android.bundle.Targeting.MultiAbiTargeting;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.TargetingProtoUtils;
import com.android.tools.build.bundletool.model.utils.files.FileUtils;
import com.google.common.base.Ascii;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * From a list of raw directory names produces targeting.
 *
 * <p>Matching is case-insensitive, following convention of the Android Resource Manager.
 */
public class TargetingGenerator {

  private static final String ASSETS_DIR = "assets/";
  private static final String LIB_DIR = "lib/";

  /**
   * Processes given asset directories, generating targeting based on their names.
   *
   * @param assetDirectories Names of directories under assets/, including the "assets/" prefix.
   * @return Targeting for the given asset directories.
   */
  public Assets generateTargetingForAssets(Collection<ZipPath> assetDirectories) {
    for (ZipPath directory : assetDirectories) {
      // Extra '/' to handle special case when the directory is just "assets".
      checkRootDirectoryName(ASSETS_DIR, directory + "/");
    }

    // Stores all different targeting values for a given set of sibling targeted directories.
    // Key: targeted directory base name {@link TargetedDirectory#getPathBaseName}
    // Values: {@link AssetsDirectoryTargeting} targeting expressed as value for each sibling.
    HashMultimap<String, AssetsDirectoryTargeting> targetingByBaseName = HashMultimap.create();

    // Pass 1: Validating dimensions across paths and storing the targeting of each path base name.

    for (ZipPath assetDirectory : FileUtils.toPathWalkingOrder(assetDirectories)) {
      TargetedDirectory targetedDirectory = TargetedDirectory.parse(assetDirectory);
      targetingByBaseName.put(
          targetedDirectory.getPathBaseName(), targetedDirectory.getLastSegment().getTargeting());
    }
    validateDimensions(targetingByBaseName);

    // Pass 2: Building the directory targeting proto using the targetingByBaseName map.

    Assets.Builder assetsBuilder = Assets.newBuilder();

    for (ZipPath assetDirectory : assetDirectories) {
      AssetsDirectoryTargeting.Builder targeting = AssetsDirectoryTargeting.newBuilder();
      TargetedDirectory targetedDirectory = TargetedDirectory.parse(assetDirectory);

      // We will calculate targeting of each path segment and merge them together.
      for (int i = 0; i < targetedDirectory.getPathSegments().size(); i++) {
        TargetedDirectorySegment segment = targetedDirectory.getPathSegments().get(i);

        // Set targeting values.
        targeting.mergeFrom(segment.getTargeting());

        // Set targeting alternatives.
        if (segment.getTargeting().hasLanguage()) {
          // Special case for languages: Don't set language alternatives for language-targeted
          // directories. The language alternatives are set only for the fallback directory of the
          // directory group (eg. within set {"dir#lang_en", "dir#lang_de", "dir"} only "dir" will
          // have the language alternatives set).
          // Rationale is that language alternatives are not being used for selection of
          // non-fallback APKs, and having the alternatives set would prevent merging of splits with
          // identical language across resources and assets.
          continue;
        }
        targeting.mergeFrom(
            // Remove oneself from the alternatives and merge them together.
            Sets.difference(
                    targetingByBaseName.get(targetedDirectory.getSubPathBaseName(i)),
                    ImmutableSet.of(segment.getTargeting()))
                .stream()
                .map(TargetingProtoUtils::toAlternativeTargeting)
                .reduce(
                    AssetsDirectoryTargeting.newBuilder(),
                    (builder, targetingValue) -> builder.mergeFrom(targetingValue),
                    (builderA, builderB) -> builderA.mergeFrom(builderB.build()))
                .build());
      }
      assetsBuilder.addDirectory(
          TargetedAssetsDirectory.newBuilder()
              .setPath(assetDirectory.toString())
              .setTargeting(targeting));
    }

    return assetsBuilder.build();
  }

  /** Finds targeting dimension mismatches amongst multi-map entries. */
  private void validateDimensions(Multimap<String, AssetsDirectoryTargeting> targetingMultimap) {
    for (String baseName : targetingMultimap.keySet()) {
      ImmutableList<TargetingDimension> distinctDimensions =
          targetingMultimap
              .get(baseName)
              .stream()
              .map(TargetingUtils::getTargetingDimensions)
              .flatMap(Collection::stream)
              .distinct()
              .collect(toImmutableList());
      if (distinctDimensions.size() > 1) {
        throw ValidationException.builder()
            .withMessage(
                "Expected at most one dimension type used for targeting of '%s'. "
                    + "However, the following dimensions were used: %s.",
                baseName, joinDimensions(distinctDimensions))
            .build();
      }
    }
  }

  private static String joinDimensions(ImmutableList<TargetingDimension> dimensions) {
    return dimensions
        .stream()
        .map(dimension -> String.format("'%s'", dimension))
        .sorted()
        .collect(Collectors.joining(", "));
  }

  /**
   * Processes given native library directories, generating targeting based on their names.
   *
   * @param libDirectories Names of directories under lib/, including the "lib/" prefix.
   * @return Targeting for the given native libraries directories.
   */
  public NativeLibraries generateTargetingForNativeLibraries(Collection<String> libDirectories) {
    NativeLibraries.Builder nativeLibraries = NativeLibraries.newBuilder();

    for (String directory : libDirectories) {
      checkRootDirectoryName(LIB_DIR, directory);

      // Split the directory under lib/ into tokens.
      String abiName = directory.substring(LIB_DIR.length());

      Abi abi = checkAbiName(abiName, directory);

      nativeLibraries.addDirectory(
          TargetedNativeDirectory.newBuilder()
              .setPath(directory)
              .setTargeting(NativeDirectoryTargeting.newBuilder().setAbi(abi))
              .build());
    }

    return nativeLibraries.build();
  }

  /**
   * Generates APEX targeting based on the names of the APEX image files.
   *
   * @param apexImageFiles names of all files under apex/, including the "apex/" prefix.
   * @return Targeting for all APEX image files.
   */
  public ApexImages generateTargetingForApexImages(Collection<ZipPath> apexImageFiles) {
    ImmutableMap<ZipPath, MultiAbi> targetingByPath =
        Maps.toMap(apexImageFiles, path -> buildMultiAbi(path.getFileName().toString()));

    ApexImages.Builder apexImages = ApexImages.newBuilder();
    ImmutableSet<MultiAbi> allTargeting = ImmutableSet.copyOf(targetingByPath.values());
    targetingByPath.forEach(
        (imagePath, targeting) ->
            apexImages.addImage(
                TargetedApexImage.newBuilder()
                    .setPath(imagePath.toString())
                    .setTargeting(buildApexTargetingWithAlternatives(targeting, allTargeting))));
    return apexImages.build();
  }

  private static MultiAbi buildMultiAbi(String fileName) {
    ImmutableList<String> tokens = ImmutableList.copyOf(ABI_SPLITTER.splitToList(fileName));
    int nAbis = tokens.size() - 1;
    checkState(tokens.get(nAbis).equals("img"), "File under 'apex/' does not have suffix 'img'");
    return MultiAbi.newBuilder()
        .addAllAbi(
            tokens.stream()
                .limit(nAbis)
                .map(token -> checkAbiName(token, fileName))
                .collect(toImmutableList()))
        .build();
  }

  private static ApexImageTargeting buildApexTargetingWithAlternatives(
      MultiAbi targeting, Set<MultiAbi> allTargeting) {
    return ApexImageTargeting.newBuilder()
        .setMultiAbi(
            MultiAbiTargeting.newBuilder()
                .addValue(targeting)
                .addAllAlternatives(
                    Sets.difference(allTargeting, ImmutableSet.of(targeting)).immutableCopy()))
        .build();
  }

  private static String checkRootDirectoryName(String rootName, String forDirectory) {
    checkArgument(rootName.endsWith("/"), "'%s' does not end with '/'.", rootName);
    checkArgument(
        forDirectory.startsWith(rootName),
        "Directory '%s' must start with '%s'.",
        forDirectory,
        rootName);
    return rootName;
  }

  private static Abi checkAbiName(String token, String forFileOrDirectory) {
    Optional<AbiName> abiName = AbiName.fromPlatformName(token);
    if (!abiName.isPresent()) {
      Optional<AbiName> abiNameLowerCase = AbiName.fromPlatformName(token.toLowerCase());
      if (abiNameLowerCase.isPresent()) {
        throw ValidationException.builder()
            .withMessage(
                "Expecting ABI name in file or directory '%s', but found '%s' "
                    + "which is not recognized. Did you mean '%s'?",
                forFileOrDirectory, token, Ascii.toLowerCase(token))
            .build();
      }
      throw ValidationException.builder()
          .withMessage(
              "Expecting ABI name in file or directory '%s', but found '%s' "
                  + "which is not recognized.",
              forFileOrDirectory, token)
          .build();
    }

    return Abi.newBuilder().setAlias(abiName.get().toProto()).build();
  }
}

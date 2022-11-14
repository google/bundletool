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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.android.tools.build.bundletool.validation.BundleValidationUtils.checkHasValuesOrAlternatives;
import static com.android.tools.build.bundletool.validation.BundleValidationUtils.checkValuesAndAlternativeHaveNoOverlap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.Assets;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.bundle.Targeting.AssetsDirectoryTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableSet;

/** Validates targeting of assets. */
public class AssetsTargetingValidator extends SubValidator {

  @Override
  public void validateModule(BundleModule module) {
    module.getAssetsConfig().ifPresent(targeting -> validateTargeting(module, targeting));
  }

  private void validateTargeting(BundleModule module, Assets assets) {
    ImmutableSet<ZipPath> assetDirsWithFiles = getDirectoriesWithFiles(module);

    for (TargetedAssetsDirectory targetedDirectory : assets.getDirectoryList()) {
      ZipPath path = ZipPath.create(targetedDirectory.getPath());

      if (!path.startsWith(ASSETS_DIRECTORY)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Path of targeted assets directory must start with 'assets/' but found '%s'.", path)
            .build();
      }

      if (!assetDirsWithFiles.contains(path)) {
        throw InvalidBundleException.builder()
            .withUserMessage("Targeted directory '%s' is empty.", path)
            .build();
      }

      checkNoDimensionWithoutValuesAndAlternatives(targetedDirectory);
      checkNoOverlapInValuesAndAlternatives(targetedDirectory);
    }

    if (module.getModuleType().equals(ModuleType.ASSET_MODULE)
        && assets.getDirectoryList().stream().anyMatch(dir -> dir.getTargeting().hasLanguage())) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Language targeting for asset packs is not supported, but found in module %s.",
              module.getName().getName())
          .build();
    }
  }

  private static ImmutableSet<ZipPath> getDirectoriesWithFiles(BundleModule module) {
    return module.getEntries().stream()
        .map(entry -> entry.getPath().getParent())
        .collect(toImmutableSet());
  }

  private static void checkNoDimensionWithoutValuesAndAlternatives(
      TargetedAssetsDirectory targetedDirectory) {
    AssetsDirectoryTargeting targeting = targetedDirectory.getTargeting();

    if (targeting.hasAbi()) {
      checkHasValuesOrAlternatives(targeting.getAbi(), targetedDirectory.getPath());
    }
    if (targeting.hasLanguage()) {
      checkHasValuesOrAlternatives(targeting.getLanguage(), targetedDirectory.getPath());
    }
    if (targeting.hasTextureCompressionFormat()) {
      checkHasValuesOrAlternatives(
          targeting.getTextureCompressionFormat(), targetedDirectory.getPath());
    }
    if (targeting.hasCountrySet()) {
      checkHasValuesOrAlternatives(targeting.getCountrySet(), targetedDirectory.getPath());
    }
  }

  private static void checkNoOverlapInValuesAndAlternatives(
      TargetedAssetsDirectory targetedDirectory) {
    AssetsDirectoryTargeting targeting = targetedDirectory.getTargeting();
    checkValuesAndAlternativeHaveNoOverlap(targeting.getAbi(), targetedDirectory.getPath());
    checkValuesAndAlternativeHaveNoOverlap(targeting.getLanguage(), targetedDirectory.getPath());
    checkValuesAndAlternativeHaveNoOverlap(
        targeting.getTextureCompressionFormat(), targetedDirectory.getPath());
    checkValuesAndAlternativeHaveNoOverlap(targeting.getCountrySet(), targetedDirectory.getPath());
  }
}

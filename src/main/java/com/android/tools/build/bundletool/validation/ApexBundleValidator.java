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

package com.android.tools.build.bundletool.validation;

import static com.android.tools.build.bundletool.model.BundleModule.APEX_DIRECTORY;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.TargetedApexImage;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;

/** Validates an APEX bundle. */
public class ApexBundleValidator extends SubValidator {

  private static final ZipPath APEX_MANIFEST_PATH = ZipPath.create("root/manifest.json");

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    long numberOfApexModules =
        modules.stream().map(BundleModule::getApexConfig).filter(Optional::isPresent).count();
    if (numberOfApexModules == 0) {
      return;
    }

    if (numberOfApexModules > 1) {
      throw ValidationException.builder()
          .withMessage("Multiple APEX modules are not allowed, found %s.", numberOfApexModules)
          .build();
    }

    if (modules.size() > 1) {
      throw ValidationException.builder()
          .withMessage("APEX bundles must only contain one module, found %s.", modules)
          .build();
    }
  }

  @Override
  public void validateModule(BundleModule module) {
    if (!module.getApexConfig().isPresent()) {
      return;
    }

    if (!module.getEntry(APEX_MANIFEST_PATH).isPresent()) {
      throw ValidationException.builder()
          .withMessage("Missing expected file in APEX bundle: '%s'.", APEX_MANIFEST_PATH)
          .build();
    }

    ImmutableSet.Builder<String> apexImagesBuilder = ImmutableSet.builder();
    for (ModuleEntry entry : module.getEntries()) {
      ZipPath path = entry.getPath();
      if (path.startsWith(APEX_DIRECTORY)) {
        apexImagesBuilder.add(path.toString());
      } else if (!path.equals(APEX_MANIFEST_PATH)) {
        throw ValidationException.builder()
            .withMessage("Unexpected file in APEX bundle: '%s'.", entry.getPath())
            .build();
      }
    }

    ImmutableSet<String> apexImages = apexImagesBuilder.build();
    ImmutableSet<String> targetedImages =
        module.getApexConfig().get().getImageList().stream()
            .map(TargetedApexImage::getPath)
            .collect(toImmutableSet());
    ImmutableSet<String> untargetedImages =
        Sets.difference(apexImages, targetedImages).immutableCopy();
    if (!untargetedImages.isEmpty()) {
      throw ValidationException.builder()
          .withMessage("Found APEX image files that are not targeted: %s", untargetedImages)
          .build();
    }
    ImmutableSet<String> missingTargetedImages =
        Sets.difference(targetedImages, apexImages).immutableCopy();
    if (!missingTargetedImages.isEmpty()) {
      throw ValidationException.builder()
          .withMessage("Targeted APEX image files are missing: %s", missingTargetedImages)
          .build();
    }
  }
}

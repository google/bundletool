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

import static com.android.tools.build.bundletool.model.AbiName.ARM64_V8A;
import static com.android.tools.build.bundletool.model.AbiName.ARMEABI_V7A;
import static com.android.tools.build.bundletool.model.AbiName.X86;
import static com.android.tools.build.bundletool.model.AbiName.X86_64;
import static com.android.tools.build.bundletool.model.BundleModule.ABI_SPLITTER;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_MANIFEST_PATH;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.TargetedApexImage;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;

/** Validates an APEX bundle. */
public class ApexBundleValidator extends SubValidator {

  private static final ImmutableSet<ImmutableSet<AbiName>> REQUIRED_SINGLETON_ABIS =
      ImmutableList.of(X86_64, X86, ARMEABI_V7A, ARM64_V8A).stream()
          .map(ImmutableSet::of)
          .collect(toImmutableSet());

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
    ImmutableSet.Builder<String> apexFileNamesBuilder = ImmutableSet.builder();
    for (ModuleEntry entry : module.getEntries()) {
      ZipPath path = entry.getPath();
      if (path.startsWith(APEX_DIRECTORY)) {
        apexImagesBuilder.add(path.toString());
        apexFileNamesBuilder.add(path.getFileName().toString());
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

    ImmutableSet<ImmutableSet<AbiName>> allAbiNameSets =
        apexFileNamesBuilder.build().stream()
            .map(ApexBundleValidator::abiNamesFromFile)
            .collect(toImmutableSet());
    if (allAbiNameSets.size() != apexImages.size()) {
      throw ValidationException.builder()
          .withMessage(
              "Every APEX image file must target a unique set of architectures, "
                  + "but found multiple files that target the same set of architectures.")
          .build();
    }

    if (!allAbiNameSets.containsAll(REQUIRED_SINGLETON_ABIS)) {
      throw ValidationException.builder()
          .withMessage(
              "APEX bundle must contain all these singleton architectures: ."
                  + REQUIRED_SINGLETON_ABIS)
          .build();
    }
  }

  private static ImmutableSet<AbiName> abiNamesFromFile(String fileName) {
    ImmutableList<String> tokens = ImmutableList.copyOf(ABI_SPLITTER.splitToList(fileName));

    // We assume that the validity of each file name was already confirmed
    return tokens.stream()
        // Do not include the suffix "img".
        .limit(tokens.size() - 1)
        .map(AbiName::fromPlatformName)
        .map(Optional::get)
        .collect(toImmutableSet());
  }
}

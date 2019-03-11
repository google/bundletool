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

import com.android.bundle.Files.ApexImages;
import com.android.bundle.Files.TargetedApexImage;
import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Optional;

/** Validates an APEX bundle. */
public class ApexBundleValidator extends SubValidator {

  // The bundle must contain a system image for at least one of each of these sets.
  private static final ImmutableSet<ImmutableSet<ImmutableSet<AbiName>>> REQUIRED_ONE_OF_ABI_SETS =
      ImmutableSet.of(
          // These 32-bit ABIs must be present.
          ImmutableSet.of(ImmutableSet.of(X86)),
          ImmutableSet.of(ImmutableSet.of(ARMEABI_V7A)),
          // These 64-bit ABIs must be present on their own or with the corresponding 32-bit ABI.
          ImmutableSet.of(ImmutableSet.of(X86_64), ImmutableSet.of(X86_64, X86)),
          ImmutableSet.of(ImmutableSet.of(ARM64_V8A), ImmutableSet.of(ARM64_V8A, ARMEABI_V7A)));

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    long numberOfApexModules =
        modules.stream().map(BundleModule::getApexConfig).filter(Optional::isPresent).count();
    if (numberOfApexModules == 0) {
      return;
    }

    if (numberOfApexModules > 1) {
      throw ValidationException.builder()
          .withMessage("Multiple APEX modules are not allowed, found %d.", numberOfApexModules)
          .build();
    }

    if (modules.size() > 1) {
      throw ValidationException.builder()
          .withMessage("APEX bundles must only contain one module, found %d.", modules.size())
          .build();
    }
  }

  @Override
  public void validateModule(BundleModule module) {
    if (module.findEntriesUnderPath(APEX_DIRECTORY).count() == 0) {
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

    if (REQUIRED_ONE_OF_ABI_SETS.stream()
        .anyMatch(one_of -> one_of.stream().noneMatch(allAbiNameSets::contains))) {
      throw ValidationException.builder()
          .withMessage(
              "APEX bundle must contain one of %s.",
              Joiner.on(" and one of ").join(REQUIRED_ONE_OF_ABI_SETS))
          .build();
    }

    module.getApexConfig().ifPresent(targeting -> validateTargeting(apexImages, targeting));
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

  private static void validateTargeting(ImmutableSet<String> allImages, ApexImages targeting) {
    ImmutableSet<String> targetedImages =
        targeting.getImageList().stream().map(TargetedApexImage::getPath).collect(toImmutableSet());

    ImmutableSet<String> untargetedImages =
        Sets.difference(allImages, targetedImages).immutableCopy();
    if (!untargetedImages.isEmpty()) {
      throw ValidationException.builder()
          .withMessage("Found APEX image files that are not targeted: %s", untargetedImages)
          .build();
    }

    ImmutableSet<String> missingTargetedImages =
        Sets.difference(targetedImages, allImages).immutableCopy();
    if (!missingTargetedImages.isEmpty()) {
      throw ValidationException.builder()
          .withMessage("Targeted APEX image files are missing: %s", missingTargetedImages)
          .build();
    }
  }
}

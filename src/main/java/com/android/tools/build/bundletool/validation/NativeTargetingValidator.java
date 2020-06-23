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

import static com.android.tools.build.bundletool.model.BundleModule.LIB_DIRECTORY;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/** Validates targeting of native libraries. */
public class NativeTargetingValidator extends SubValidator {

  @Override
  public void validateModule(BundleModule module) {
    module.getNativeConfig().ifPresent(targeting -> validateTargeting(module, targeting));
  }

  private static void validateTargeting(BundleModule module, NativeLibraries nativeLibraries) {
    for (TargetedNativeDirectory targetedDirectory : nativeLibraries.getDirectoryList()) {
      ZipPath path = ZipPath.create(targetedDirectory.getPath());
      NativeDirectoryTargeting targeting = targetedDirectory.getTargeting();

      if (!targeting.hasAbi()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Targeted native directory '%s' does not have the ABI dimension set.",
                targetedDirectory.getPath())
            .build();
      }

      if (!path.startsWith(LIB_DIRECTORY) || path.getNameCount() != 2) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Path of targeted native directory must be in format 'lib/<directory>' but "
                    + "found '%s'.",
                path)
            .build();
      }

      if (BundleValidationUtils.directoryContainsNoFiles(module, path)) {
        throw InvalidBundleException.builder()
            .withUserMessage("Targeted directory '%s' is empty.", path)
            .build();
      }
    }

    SetView<String> libDirsWithoutTargeting =
        Sets.difference(
            module
                .findEntriesUnderPath(LIB_DIRECTORY)
                .map(libFile -> libFile.getPath().subpath(0, 2).toString())
                .collect(toImmutableSet()),
            nativeLibraries.getDirectoryList().stream()
                .map(TargetedNativeDirectory::getPath)
                .collect(toImmutableSet()));
    if (!libDirsWithoutTargeting.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Following native directories are not targeted: %s", libDirsWithoutTargeting)
          .build();
    }
  }
}

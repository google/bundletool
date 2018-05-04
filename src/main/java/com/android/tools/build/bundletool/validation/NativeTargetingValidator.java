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

import com.android.bundle.Files.NativeLibraries;
import com.android.bundle.Files.TargetedNativeDirectory;
import com.android.bundle.Targeting.NativeDirectoryTargeting;
import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ZipPath;
import java.util.ArrayList;
import java.util.List;

/** Validates targeting of native libraries. */
public class NativeTargetingValidator extends SubValidator {

  @Override
  public void validateModule(BundleModule module) {
    module.getNativeConfig().ifPresent(targeting -> validateTargeting(module, targeting));
  }

  private static void validateTargeting(BundleModule module, NativeLibraries nativeLibraries) {
    List<String> targetedDirs = new ArrayList<>();

    for (TargetedNativeDirectory targetedDirectory : nativeLibraries.getDirectoryList()) {
      ZipPath path = ZipPath.create(targetedDirectory.getPath());
      NativeDirectoryTargeting targeting = targetedDirectory.getTargeting();

      if (!targeting.hasAbi()) {
        throw ValidationException.builder()
            .withMessage(
                "Targeted native directory '%s' does not have the ABI dimension set.",
                targetedDirectory.getPath())
            .build();
      }

      if (!path.startsWith(LIB_DIRECTORY) || path.getNameCount() != 2) {
        throw ValidationException.builder()
            .withMessage(
                "Path of targeted native directory must be in format 'lib/<directory>' but "
                    + "found '%s'.",
                path)
            .build();
      }

      if (BundleValidationUtils.directoryContainsNoFiles(module, path)) {
        throw ValidationException.builder()
            .withMessage("Targeted directory '%s' is empty.", path)
            .build();
      }

      targetedDirs.add(path.toString());
    }
  }
}

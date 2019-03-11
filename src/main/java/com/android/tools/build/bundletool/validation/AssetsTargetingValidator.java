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

import com.android.bundle.Files.Assets;
import com.android.bundle.Files.TargetedAssetsDirectory;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;

/** Validates targeting of assets. */
public class AssetsTargetingValidator extends SubValidator {

  @Override
  public void validateModule(BundleModule module) {
    module.getAssetsConfig().ifPresent(targeting -> validateTargeting(module, targeting));
  }

  private void validateTargeting(BundleModule module, Assets assets) {
    for (TargetedAssetsDirectory targetedDirectory : assets.getDirectoryList()) {
      ZipPath path = ZipPath.create(targetedDirectory.getPath());

      if (!path.startsWith(ASSETS_DIRECTORY)) {
        throw ValidationException.builder()
            .withMessage(
                "Path of targeted assets directory must start with 'assets/' but found '%s'.", path)
            .build();
      }

      if (BundleValidationUtils.directoryContainsNoFiles(module, path)) {
        throw ValidationException.builder()
            .withMessage("Targeted directory '%s' is empty.", path)
            .build();
      }

      if (module.getModuleType().equals(ModuleType.ASSET_MODULE)
          && assets.getDirectoryList().stream().anyMatch(dir -> dir.getTargeting().hasLanguage())) {
        throw ValidationException.builder()
            .withMessage(
                "Language targeting for asset packs is not supported, but found in module %s.",
                module.getName().getName())
            .build();
      }
    }
  }
}

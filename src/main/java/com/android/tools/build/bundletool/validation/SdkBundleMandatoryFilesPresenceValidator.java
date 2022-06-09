/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.build.bundletool.model.utils.BundleParser.SDK_MODULES_FILE_NAME;

import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.io.Files;
import java.util.zip.ZipFile;

/** Validates the presence of mandatory files in ASB zips and ASB module zips. */
public class SdkBundleMandatoryFilesPresenceValidator extends SubValidator {

  @Override
  public void validateModuleZipFile(ZipFile moduleFile) {
    checkModuleHasAndroidManifest(moduleFile);
  }

  @Override
  public void validateBundleZipFile(ZipFile bundleFile) {
    if (bundleFile.getEntry(SDK_MODULES_FILE_NAME) == null) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The archive doesn't seem to be an SDK Bundle, it is missing required file '%s'.",
              SDK_MODULES_FILE_NAME)
          .build();
    }
  }

  private static void checkModuleHasAndroidManifest(ZipFile zipFile) {
    ZipPath moduleManifestPath = SpecialModuleEntry.ANDROID_MANIFEST.getPath();
    String moduleName = Files.getNameWithoutExtension(zipFile.getName());

    if (zipFile.getEntry(moduleManifestPath.toString()) == null) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' is missing mandatory file '%s'.",
              moduleName, SpecialModuleEntry.ANDROID_MANIFEST.getPath())
          .build();
    }
  }
}

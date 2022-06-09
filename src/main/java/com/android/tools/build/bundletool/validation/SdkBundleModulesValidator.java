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

import com.android.tools.build.bundletool.model.BundleModule;
import com.google.common.collect.ImmutableList;
import java.util.zip.ZipFile;

/** Validator for SDK Bundle module zip files. */
public class SdkBundleModulesValidator {

  /** Validators run on the raw module zip files (inputs to {@code build-sdk-bundle}). */
  static final ImmutableList<SubValidator> MODULE_FILES_SUB_VALIDATORS =
      ImmutableList.of(new SdkBundleMandatoryFilesPresenceValidator());

  /** Validators run on the internal representation of SDK bundle modules. */
  static final ImmutableList<SubValidator> MODULE_SUB_VALIDATORS =
      ImmutableList.of(
          // Fundamental file validations first.
          new BundleFilesValidator(),
          new SdkBundleModuleNameValidator(),
          // More specific file validations.
          new DexFilesValidator(),
          new SdkAndroidManifestValidator(),
          // Other.
          new ResourceTableValidator(),
          new SdkBundleModuleResourceIdValidator());

  /**
   * Validates the given input module zip files.
   *
   * <p>These are the raw module zips passed to {@code build-sdk-bundle}.
   */
  public void validateModuleZipFiles(ImmutableList<ZipFile> moduleZipFiles) {
    ValidatorRunner validatorRunner = new ValidatorRunner(MODULE_FILES_SUB_VALIDATORS);
    moduleZipFiles.forEach(validatorRunner::validateModuleZipFile);
  }

  /** Validates the given SDK Bundle modules. */
  public void validateSdkBundleModules(ImmutableList<BundleModule> modules) {
    new ValidatorRunner(MODULE_SUB_VALIDATORS).validateBundleModules(modules);
  }
}

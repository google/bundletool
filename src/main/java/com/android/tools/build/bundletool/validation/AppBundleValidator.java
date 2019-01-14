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

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.zip.ZipFile;

/** Validates the files and configuration for the bundle. */
public class AppBundleValidator {

  /** Validators run on the bundle zip file. */
  @VisibleForTesting
  static final ImmutableList<SubValidator> BUNDLE_FILE_SUB_VALIDATORS =
      // Keep order of common validators in sync with BundleModulesValidator.
      ImmutableList.of(new BundleZipValidator(), new MandatoryFilesPresenceValidator());

  /** Validators run on the internal representation of bundle and bundle modules. */
  @VisibleForTesting
  static final ImmutableList<SubValidator> BUNDLE_SUB_VALIDATORS =
      // Keep order of common validators in sync with BundleModulesValidator.
      ImmutableList.of(
          // Fundamental file validations first.
          new BundleFilesValidator(),
          new ModuleNamesValidator(),
          new AndroidManifestValidator(),
          new BundleConfigValidator(),
          // More specific file validations.
          new EntryClashValidator(),
          new AbiParityValidator(),
          new DexFilesValidator(),
          new ApexBundleValidator(),
          // Targeting validations.
          new AssetsTargetingValidator(),
          new NativeTargetingValidator(),
          // Other.
          new ModuleDependencyValidator(),
          new ModuleTitleValidator(),
          new ResourceTableValidator());

  /**
   * Validates the given App Bundle zip file.
   *
   * <p>Note that this method performs different checks than {@link #validate(AppBundle)}.
   */
  public void validateFile(ZipFile bundleFile) {
    new ValidatorRunner(BUNDLE_FILE_SUB_VALIDATORS).validateBundleZipFile(bundleFile);
  }

  /**
   * Validates the given App Bundle.
   *
   * <p>Note that this method performs different checks than {@link #validateFile(ZipFile)}.
   *
   * @throws ValidationException If the bundle is invalid.
   */
  public void validate(AppBundle bundle) {
    new ValidatorRunner(BUNDLE_SUB_VALIDATORS).validateBundle(bundle);
  }
}

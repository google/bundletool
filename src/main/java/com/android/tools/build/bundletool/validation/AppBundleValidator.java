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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.zip.ZipFile;

/** Validates the files and configuration for the bundle. */
public class AppBundleValidator {

  /** Validators run on the bundle zip file. */
  @VisibleForTesting
  static final ImmutableList<SubValidator> DEFAULT_BUNDLE_FILE_SUB_VALIDATORS =
      // Keep order of common validators in sync with BundleModulesValidator.
      ImmutableList.of(
          new BundleZipValidator(),
          new MandatoryFilesPresenceValidator(AppBundle.NON_MODULE_DIRECTORIES));

  /** Validators run on the internal representation of bundle and bundle modules. */
  @VisibleForTesting
  static final ImmutableList<SubValidator> DEFAULT_BUNDLE_SUB_VALIDATORS =
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
          new TextureCompressionFormatParityValidator(),
          new DeviceTierParityValidator(),
          new CountrySetParityValidator(),
          new DexFilesValidator(),
          new ApexBundleValidator(),
          new AssetBundleValidator(),
          // Targeting validations.
          new AssetsTargetingValidator(),
          new NativeTargetingValidator(),
          // Other.
          new ArchiveEntriesValidator(),
          new ModuleDependencyValidator(),
          new ModuleTitleValidator(),
          new ResourceTableValidator(),
          new AssetModuleFilesValidator(),
          new CodeTransparencyValidator(),
          new RuntimeEnabledSdkConfigValidator());

  private final ImmutableList<SubValidator> allBundleSubValidators;
  private final ImmutableList<SubValidator> allBundleFileSubValidators;

  private AppBundleValidator(
      ImmutableList<SubValidator> allBundleSubValidators,
      ImmutableList<SubValidator> allBundleFileSubValidators) {
    this.allBundleSubValidators = allBundleSubValidators;
    this.allBundleFileSubValidators = allBundleFileSubValidators;
  }

  public static AppBundleValidator create() {
    return create(ImmutableList.of());
  }

  public static AppBundleValidator create(ImmutableList<SubValidator> extraSubValidators) {
    AppBundleValidator validator =
        new AppBundleValidator(
            ImmutableList.<SubValidator>builder()
                .addAll(DEFAULT_BUNDLE_SUB_VALIDATORS)
                .addAll(extraSubValidators)
                .build(),
            ImmutableList.<SubValidator>builder()
                .addAll(DEFAULT_BUNDLE_FILE_SUB_VALIDATORS)
                .addAll(extraSubValidators)
                .build());
    return validator;
  }

  /**
   * Validates the given App Bundle zip file.
   *
   * <p>Note that this method performs different checks than {@link #validate(AppBundle)}.
   */
  public void validateFile(ZipFile bundleFile) {
    new ValidatorRunner(allBundleFileSubValidators).validateBundleZipFile(bundleFile);
  }

  /**
   * Validates the given App Bundle.
   *
   * <p>Note that this method performs different checks than {@link #validateFile(ZipFile)}.
   *
   * @throws ValidationException If the bundle is invalid.
   */
  public void validate(AppBundle bundle) {
    new ValidatorRunner(allBundleSubValidators).validateBundle(bundle);
  }
}

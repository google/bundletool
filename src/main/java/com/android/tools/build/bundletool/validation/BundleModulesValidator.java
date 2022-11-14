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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.utils.BundleModuleParser;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.zip.ZipFile;

/**
 * Validates bundle modules that are input to the {@link
 * com.android.tools.build.bundletool.commands.BuildBundleCommand BuildBundleCommand}.
 *
 * <p>Note that these input modules have slightly different expected content from the modules in an
 * app bundle.
 */
public class BundleModulesValidator {

  /** Validators run on the bundle module zip files. */
  @VisibleForTesting
  static final ImmutableList<SubValidator> MODULE_FILE_SUB_VALIDATORS =
      // Keep order of common validators in sync with AppBundleValidator.
      ImmutableList.of(new MandatoryFilesPresenceValidator(AppBundle.NON_MODULE_DIRECTORIES));

  /** Validators run on the internal representation of bundle modules. */
  @VisibleForTesting
  static final ImmutableList<SubValidator> MODULES_SUB_VALIDATORS =
      // Keep order of common validators in sync with AppBundleValidator.
      ImmutableList.of(
          // Fundamental file validations first.
          new BundleFilesValidator(),
          new ModuleNamesValidator(),
          new AndroidManifestValidator(),
          // More specific file validations.
          new EntryClashValidator(),
          new AbiParityValidator(),
          new TextureCompressionFormatParityValidator(),
          new DeviceTierParityValidator(),
          new CountrySetParityValidator(),
          new DexFilesValidator(),
          new ApexBundleValidator(),
          new AssetBundleValidator(),
          // Other.
          new ModuleDependencyValidator(),
          new ModuleTitleValidator(),
          new ResourceTableValidator(),
          new AssetModuleFilesValidator());

  public ImmutableList<BundleModule> validate(
      ImmutableList<ZipFile> moduleZips, BundleConfig bundleConfig) {
    for (ZipFile moduleZip : moduleZips) {
      new ValidatorRunner(MODULE_FILE_SUB_VALIDATORS).validateModuleZipFile(moduleZip);
    }

    ImmutableList<BundleModule> modules =
        moduleZips.stream()
            .map(module -> BundleModuleParser.parseAppBundleModule(module, bundleConfig))
            .collect(toImmutableList());

    new ValidatorRunner(MODULES_SUB_VALIDATORS).validateBundleModules(modules);

    return modules;
  }
}

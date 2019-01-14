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

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.bundle.Config.BundleConfig;
import com.android.bundle.Config.Bundletool;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleZipEntry;
import com.android.tools.build.bundletool.model.version.BundleToolVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
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
      ImmutableList.of(new MandatoryFilesPresenceValidator());

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
          new DexFilesValidator(),
          new ApexBundleValidator(),
          // Other.
          new ModuleDependencyValidator(),
          new ModuleTitleValidator(),
          new ResourceTableValidator());

  private static final BundleConfig EMPTY_CONFIG_WITH_CURRENT_VERSION =
      BundleConfig.newBuilder()
          .setBundletool(
              Bundletool.newBuilder().setVersion(BundleToolVersion.getCurrentVersion().toString()))
          .build();

  public ImmutableList<BundleModule> validate(ImmutableList<ZipFile> moduleZips) {
    for (ZipFile moduleZip : moduleZips) {
      new ValidatorRunner(MODULE_FILE_SUB_VALIDATORS).validateModuleZipFile(moduleZip);
    }

    ImmutableList<BundleModule> modules =
        moduleZips.stream().map(this::toBundleModule).collect(toImmutableList());

    new ValidatorRunner(MODULES_SUB_VALIDATORS).validateBundleModules(modules);

    return modules;
  }

  private BundleModule toBundleModule(ZipFile moduleZipFile) {
    BundleModule bundleModule;
    try {
      bundleModule =
          BundleModule.builder()
              // Assigning a temporary name because the real one will be extracted from the
              // manifest, but this requires the BundleModule to be built.
              .setName(BundleModuleName.create("TEMPORARY_MODULE_NAME"))
              .setBundleConfig(EMPTY_CONFIG_WITH_CURRENT_VERSION)
              .addEntries(
                  moduleZipFile.stream()
                      .filter(not(ZipEntry::isDirectory))
                      .map(zipEntry -> ModuleZipEntry.fromModuleZipEntry(zipEntry, moduleZipFile))
                      .collect(toImmutableList()))
              .build();
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Error reading module zip file '%s'.", moduleZipFile.getName()), e);
    }

    BundleModuleName actualModuleName =
        BundleModuleName.create(
            bundleModule
                .getAndroidManifest()
                .getSplitId()
                .orElse(BundleModuleName.BASE_MODULE_NAME));

    return bundleModule.toBuilder().setName(actualModuleName).build();
  }
}

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

import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileHasExtension;
import static com.android.tools.build.bundletool.utils.files.FilePreconditions.checkFileNamesAreUnique;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.MoreFiles.getNameWithoutExtension;

import com.android.tools.build.bundletool.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModuleName;
import com.android.tools.build.bundletool.model.ModuleZipEntry;
import com.android.tools.build.bundletool.utils.ZipUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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
          new AndroidManifestValidator(),
          // More specific file validations.
          new EntryClashValidator(),
          new AbiParityValidator(),
          new DexFilesValidator(),
          // Other.
          new ModuleDependencyValidator(),
          new ResourceTableValidator());

  public void validate(ImmutableList<Path> modulePaths) {
    // Name of the module zip file is name of the module it contains. Therefore filenames must
    // be unique.
    checkFileNamesAreUnique("Modules", modulePaths);

    final Map<Path, ZipFile> zipFileByPath = new HashMap<>();
    try {
      for (Path modulePath : modulePaths) {
        checkFileHasExtension("Module", modulePath, ".zip");
        ZipFile moduleZip = ZipUtils.openZipFile(modulePath);
        zipFileByPath.put(modulePath, moduleZip);
        new ValidatorRunner(MODULE_FILE_SUB_VALIDATORS).validateModuleZipFile(moduleZip);
      }

      ImmutableList<BundleModule> modules =
          zipFileByPath
              .entrySet()
              .stream()
              .map(entry -> toBundleModule(entry.getKey(), entry.getValue()))
              .collect(toImmutableList());

      new ValidatorRunner(MODULES_SUB_VALIDATORS).validateBundleModules(modules);
    } finally {
      if (zipFileByPath != null) {
        ZipUtils.closeZipFiles(zipFileByPath.values());
      }
    }
  }

  private BundleModule toBundleModule(Path modulePath, ZipFile moduleZipFile) {
    // Validates the module name.
    BundleModuleName moduleName = BundleModuleName.create(getNameWithoutExtension(modulePath));

    try {
      return BundleModule.builder()
          .setName(moduleName)
          .addEntries(
              moduleZipFile
                  .stream()
                  .filter(not(ZipEntry::isDirectory))
                  .map(zipEntry -> ModuleZipEntry.fromModuleZipEntry(zipEntry, moduleZipFile))
                  .collect(toImmutableList()))
          .build();
    } catch (IOException e) {
      throw ValidationException.builder()
          .withCause(e)
          .withMessage("Error reading module zip file '%s'.", modulePath)
          .build();
    }
  }
}

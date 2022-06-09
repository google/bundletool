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

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Runs given set of validators. */
public class ValidatorRunner {

  private final ImmutableList<SubValidator> subValidators;

  public ValidatorRunner(ImmutableList<SubValidator> subValidators) {
    this.subValidators = subValidators;
  }

  /** Validates the given App Bundle zip file. */
  public void validateBundleZipFile(ZipFile bundleFile) {
    subValidators.forEach(subValidator -> subValidator.validateBundleZipFile(bundleFile));

    Enumeration<? extends ZipEntry> zipEntries = bundleFile.entries();
    while (zipEntries.hasMoreElements()) {
      ZipEntry zipEntry = zipEntries.nextElement();
      subValidators.forEach(
          subValidator -> subValidator.validateBundleZipEntry(bundleFile, zipEntry));
    }
  }

  /** Validates the given App Bundle module zip file. */
  public void validateModuleZipFile(ZipFile moduleFile) {
    subValidators.forEach(subValidator -> subValidator.validateModuleZipFile(moduleFile));
  }

  /** Validates the given SDK modules zip file. */
  public void validateSdkModulesZipFile(ZipFile moduleFile) {
    subValidators.forEach(subValidator -> subValidator.validateSdkModulesZipFile(moduleFile));
  }

  /** Validates the given App Bundle. */
  public void validateBundle(AppBundle bundle) {
    subValidators.forEach(subValidator -> validateBundleUsingSubValidator(bundle, subValidator));
  }

  /** Validates the given SDK Bundle. */
  void validateSdkBundle(SdkBundle bundle) {
    subValidators.forEach(subValidator -> validateSdkBundleUsingSubValidator(bundle, subValidator));
  }

  /** Interprets given modules as a bundle and validates it. */
  public void validateBundleModules(ImmutableList<BundleModule> modules) {
    subValidators.forEach(
        subValidator -> validateBundleModulesUsingSubValidator(modules, subValidator));
  }

  private static void validateBundleUsingSubValidator(AppBundle bundle, SubValidator subValidator) {
    subValidator.validateBundle(bundle);
    validateBundleModulesUsingSubValidator(
        ImmutableList.copyOf(bundle.getModules().values()), subValidator);
  }

  private static void validateSdkBundleUsingSubValidator(
      SdkBundle bundle, SubValidator subValidator) {
    subValidator.validateSdkBundle(bundle);
    BundleModule module = bundle.getModule();
    subValidator.validateModule(module);

    for (ZipPath moduleFile : getModuleFiles(module)) {
      subValidator.validateModuleFile(moduleFile);
    }
  }

  private static void validateBundleModulesUsingSubValidator(
      ImmutableList<BundleModule> modules, SubValidator subValidator) {
    subValidator.validateAllModules(modules);

    for (BundleModule module : modules) {
      subValidator.validateModule(module);

      for (ZipPath moduleFile : getModuleFiles(module)) {
        subValidator.validateModuleFile(moduleFile);
      }
    }
  }

  private static ImmutableList<ZipPath> getModuleFiles(BundleModule module) {
    return module.getEntries().stream().map(ModuleEntry::getPath).collect(toImmutableList());
  }
}

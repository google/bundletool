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
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.SdkBundle;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Validates a particular property of the bundle.
 *
 * <p>Sub-classes override some of the methods that is/are most convenient for the particular type
 * of validation.
 */
public abstract class SubValidator {

  // Validations of the App Bundle module zip file.

  public void validateModuleZipFile(ZipFile moduleFile) {}

  // Validations of the Bundle zip file.

  public void validateBundleZipFile(ZipFile bundleFile) {}

  public void validateBundleZipEntry(ZipFile bundleFile, ZipEntry zipEntry) {}

  /** Validates the given SDK Modules zip file. */
  public void validateSdkModulesZipFile(ZipFile modulesFile) {}

  // Validations of the AppBundle object and its internals.

  /** Validates an AppBundle object. */
  public void validateBundle(AppBundle bundle) {}

  public void validateAllModules(ImmutableList<BundleModule> modules) {}

  public void validateModule(BundleModule module) {}

  /** Validates an SdkBundle object. */
  public void validateSdkBundle(SdkBundle bundle) {}

  /**
   * Validates path of the file.
   *
   * <p>The path is expected to be relative to the module directory.
   *
   * <p>This method is not invoked for special bundle files, eg. targeting metadata files like
   * {@code assets.pb} or the parsed {@code AndroidManifest.xml} file. See {@link
   * BundleModule.Builder#addEntry} for full list of special files.
   */
  public void validateModuleFile(ZipPath file) {}
}

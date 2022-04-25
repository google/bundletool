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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Validates that an SDK bundle modules file has only one module. */
public class SdkBundleHasOneModuleValidator extends SubValidator {

  @Override
  public void validateSdkModulesZipFile(ZipFile modulesFile) {
    checkSdkHasSingleModule(modulesFile);
  }

  private void checkSdkHasSingleModule(ZipFile modulesFile) {
    ImmutableSet<ZipPath> moduleDirectories = getModuleDirectories(modulesFile);
    if (moduleDirectories.size() != 1) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "SDK bundles need exactly one module, %d detected.", moduleDirectories.size())
          .build();
    }
  }

  private ImmutableSet<ZipPath> getModuleDirectories(ZipFile modulesFile) {
    return Collections.list(modulesFile.entries()).stream()
        .map(ZipEntry::getName)
        .map(ZipPath::create)
        .filter(entryPath -> entryPath.getNameCount() > 1)
        .map(entryPath -> entryPath.getName(0))
        .collect(toImmutableSet());
  }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.tools.build.bundletool.model.AppBundle;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Validates presence of mandatory bundle/module files. */
public class MandatoryFilesPresenceValidator extends SubValidator {

  private final ImmutableSet<ZipPath> nonModuleDirectories;

  MandatoryFilesPresenceValidator(ImmutableSet<ZipPath> nonModuleDirectories) {
    this.nonModuleDirectories = nonModuleDirectories;
  }

  @Override
  public void validateModuleZipFile(ZipFile moduleFile) {
    checkModuleHasAndroidManifest(
        moduleFile,
        /* moduleBaseDir= */ ZipPath.create(""),
        /* moduleName= */ Files.getNameWithoutExtension(moduleFile.getName()));
  }

  @Override
  public void validateBundleZipFile(ZipFile bundleFile) {
    ImmutableSet<ZipPath> moduleDirectories =
        Collections.list(bundleFile.entries()).stream()
            .map(ZipEntry::getName)
            .map(ZipPath::create)
            .filter(entryPath -> entryPath.getNameCount() > 1)
            .map(entryPath -> entryPath.getName(0))
            .filter(not(nonModuleDirectories::contains))
            .collect(toImmutableSet());

    checkBundleHasBundleConfig(bundleFile);

    for (ZipPath moduleDir : moduleDirectories) {
      checkModuleHasAndroidManifest(bundleFile, moduleDir, /* moduleName= */ moduleDir.toString());
    }
  }

  private static void checkBundleHasBundleConfig(ZipFile bundleFile) {
    if (bundleFile.getEntry(AppBundle.BUNDLE_CONFIG_FILE_NAME) == null) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "The archive doesn't seem to be an App Bundle, it is missing required file '%s'.",
              AppBundle.BUNDLE_CONFIG_FILE_NAME)
          .build();
    }
  }

  private static void checkModuleHasAndroidManifest(
      ZipFile zipFile, ZipPath moduleBaseDir, String moduleName) {

    ZipPath moduleManifestPath =
        moduleBaseDir.resolve(SpecialModuleEntry.ANDROID_MANIFEST.getPath());

    if (zipFile.getEntry(moduleManifestPath.toString()) == null) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' is missing mandatory file '%s'.",
              moduleName, SpecialModuleEntry.ANDROID_MANIFEST.getPath())
          .build();
    }
  }
}

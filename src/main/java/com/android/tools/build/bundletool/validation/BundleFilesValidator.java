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

import static com.android.tools.build.bundletool.model.BundleModule.ABI_SPLITTER;
import static com.android.tools.build.bundletool.model.BundleModule.APEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.ASSETS_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.LIB_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.MANIFEST_FILENAME;
import static com.android.tools.build.bundletool.model.BundleModule.RESOURCES_DIRECTORY;
import static com.android.tools.build.bundletool.model.BundleModule.ROOT_DIRECTORY;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.tools.build.bundletool.model.AbiName;
import com.android.tools.build.bundletool.model.BundleModule.SpecialModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validates files inside a bundle. */
public class BundleFilesValidator extends SubValidator {

  private static final Pattern CLASSES_DEX_PATTERN = Pattern.compile("classes[0-9]*\\.dex");

  private static final ImmutableSet<ZipPath> RESERVED_ROOT_APK_ENTRIES =
      ImmutableSet.of(
          // Special directories.
          // The below is temporarily commented and should be revisited in the future: re-enabled
          // or permanently deleted.
          // ASSETS_DIRECTORY,
          LIB_DIRECTORY,
          RESOURCES_DIRECTORY,
          // Special files in the binary APKs.
          ZipPath.create("AndroidManifest.xml"),
          ZipPath.create("resources.arsc"),
          // Special files in the proto APKs (created by the 'split-module' command).
          ZipPath.create(MANIFEST_FILENAME),
          SpecialModuleEntry.RESOURCE_TABLE.getPath());

  @Override
  public void validateModuleFile(ZipPath file) {
    String fileName = file.getFileName().toString();

    if (file.startsWith(ASSETS_DIRECTORY)) {
      // No restrictions.

    } else if (file.startsWith(DEX_DIRECTORY)) {
      if (!fileName.endsWith(".dex")) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Files under %s/ must have %s extension, found '%s'.", DEX_DIRECTORY, ".dex", file)
            .build();
      }
      if (!CLASSES_DEX_PATTERN.matcher(fileName).matches()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Files under %s/ must match the 'classes[0-9]*.dex' pattern, found '%s'.",
                DEX_DIRECTORY, file)
            .build();
      }
      if (file.getNameCount() != 2) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "The %s/ directory cannot contain directories, found '%s'.", DEX_DIRECTORY, file)
            .build();
      }
    } else if (file.startsWith(LIB_DIRECTORY)) {
      if (file.getNameCount() != 3) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Native library files need to have paths in form '%s/<single-directory>/<file>.so'"
                    + " but found '%s'.",
                LIB_DIRECTORY, file)
            .build();
      }

      // See https://developer.android.com/ndk/guides/wrap-script#packaging_wrapsh
      if (!fileName.endsWith(".so") && !fileName.equals("wrap.sh")) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Files under %s/ must have %s extension, found '%s'.", LIB_DIRECTORY, ".so", file)
            .build();
      }

      String subDirName = file.getName(1).toString();
      if (!AbiName.fromLibSubDirName(subDirName).isPresent()) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Unrecognized native architecture for directory '%s'.", file.subpath(0, 2))
            .build();
      }

    } else if (file.startsWith(MANIFEST_DIRECTORY)) {
      if (!fileName.equals("AndroidManifest.xml")) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Only '%s' is accepted under directory '%s/' but found file '%s'.",
                MANIFEST_FILENAME, MANIFEST_DIRECTORY, file)
            .build();
      }

    } else if (file.startsWith(RESOURCES_DIRECTORY)) {
      // No restrictions.

    } else if (file.startsWith(ROOT_DIRECTORY)) {
      ZipPath nameUnderRoot = file.getName(1);
      if (isReservedRootApkEntry(nameUnderRoot)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "File '%s' uses reserved file or directory name '%s'.", file, nameUnderRoot)
            .build();
      }

    } else if (file.startsWith(APEX_DIRECTORY)) {
      if (file.getNameCount() != 2) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "APEX image files need to have paths in form '%s/<file>.img' but found '%s'.",
                APEX_DIRECTORY, file)
            .build();
      }

      if (!fileName.endsWith(".img") && !fileName.endsWith(".build_info.pb")) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Files under %s/ must have %s extension, found '%s'.", APEX_DIRECTORY, ".img", file)
            .build();
      }

      validateMultiAbiFileName(file);

    } else {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module files can be only in pre-defined directories, but found '%s'.", file)
          .build();
    }
  }

  private static boolean isReservedRootApkEntry(ZipPath name) {
    return RESERVED_ROOT_APK_ENTRIES.contains(name)
        || CLASSES_DEX_PATTERN.matcher(name.toString()).matches();
  }

  private static void validateMultiAbiFileName(ZipPath file) {
    if (!file.toString().endsWith(".img")) {
      return;
    }
    ImmutableList<String> tokens =
        ImmutableList.copyOf(ABI_SPLITTER.splitToList(file.getFileName().toString()));
    int nAbis = tokens.size() - 1;

    ImmutableList<Optional<AbiName>> abis =
        // Do not include the suffix "img".
        tokens.stream().limit(nAbis).map(AbiName::fromPlatformName).collect(toImmutableList());
    if (!abis.stream().allMatch(Optional::isPresent)) {
      throw InvalidBundleException.builder()
          .withUserMessage("Unrecognized native architecture for file '%s'.", file)
          .build();
    }

    ImmutableSet<AbiName> uniqueAbis = abis.stream().map(Optional::get).collect(toImmutableSet());
    if (uniqueAbis.size() != nAbis) {
      throw InvalidBundleException.builder()
          .withUserMessage("Repeating architectures in APEX system image file '%s'.", file)
          .build();
    }
  }
}

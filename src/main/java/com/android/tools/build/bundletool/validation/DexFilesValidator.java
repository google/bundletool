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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates correct dex files usage in a bundle module.
 *
 * <p>Validated properties:
 *
 * <ul>
 *   <li>Dex files in a bundle module are named correctly, forming a sequence * "classes.dex",
 *       "classes2.dex", "classes3.dex" etc.
 *   <li>'hasCode' value in feature modules corresponds to whether there are dex files present in
 *       the module.
 *   <li>Asset modules are ignored.
 * </ul>
 */
public class DexFilesValidator extends SubValidator {

  private static final Pattern CLASSES_DEX_FILE_PATTERN =
      Pattern.compile("classes(?<number>[0-9]+)?\\.dex");

  @Override
  public void validateModule(BundleModule module) {
    if (module.getModuleType().equals(ModuleType.ASSET_MODULE)) {
      return;
    }
    ImmutableList<String> orderedDexFiles =
        module
            .findEntriesUnderPath(BundleModule.DEX_DIRECTORY)
            .map(moduleEntry -> moduleEntry.getPath().getFileName().toString())
            .filter(fileName -> CLASSES_DEX_FILE_PATTERN.matcher(fileName).matches())
            .sorted(Comparator.comparingInt(DexFilesValidator::getClassesDexIndex))
            .collect(toImmutableList());

    validateDexNames(orderedDexFiles);
    validateHasCode(module, orderedDexFiles);
  }

  private static void validateDexNames(ImmutableList<String> orderedDexFiles) {
    int dexIndex = 1;
    for (String dexFileName : orderedDexFiles) {
      // This also catches the issue of duplicate files for index 1 (valid "classes.dex" and
      // invalid "classes1.dex").
      String expectedDexFileName = dexFileNameForIndex(dexIndex);
      if (!dexFileName.equals(expectedDexFileName)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Invalid dex file indices, expecting file '%s' but found '%s'.",
                expectedDexFileName, dexFileName)
            .build();
      }
      dexIndex++;
    }
  }

  private static void validateHasCode(BundleModule module, ImmutableList<String> orderedDexFiles) {
    boolean hasCode = module.getAndroidManifest().getEffectiveHasCode();
    boolean isAssetModule = module.getModuleType().equals(ModuleType.ASSET_MODULE);
    if (orderedDexFiles.isEmpty() && hasCode && !isAssetModule) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' has no dex files but the attribute 'hasCode' is not set to false "
                  + "in the AndroidManifest.xml.",
              module.getName())
          .build();
    }
  }

  private static int getClassesDexIndex(String filename) {
    Matcher matcher = CLASSES_DEX_FILE_PATTERN.matcher(filename);
    checkState(matcher.matches(), "File name '%s' does not match the expected pattern.", filename);
    String numberStr = matcher.group("number");
    return Strings.isNullOrEmpty(numberStr) ? 1 : Integer.parseInt(numberStr);
  }

  private static String dexFileNameForIndex(int index) {
    checkArgument(index > 0, "Index must be positive, got %s.", index);
    return index == 1 ? "classes.dex" : String.format("classes%d.dex", index);
  }
}

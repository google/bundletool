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

import static com.android.tools.build.bundletool.model.BundleModule.DEX_DIRECTORY;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Validates that no two modules contain a file with the same full path and different content.
 *
 * <p>The checks don't include special files that are defined in the format and expected to be
 * inherently different (eg. AndroidManifest.xml).
 */
public class EntryClashValidator extends SubValidator {

  private static final Predicate<ZipPath> IS_EXPECTED_TO_BE_DIFFERENT =
      path -> path.startsWith(DEX_DIRECTORY);

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    Map<ZipPath, BundleModule> usedPaths = new HashMap<>();
    for (BundleModule module : modules) {
      for (ModuleEntry entry : module.getEntries()) {
        if (IS_EXPECTED_TO_BE_DIFFERENT.test(entry.getPath())) {
          continue;
        }

        BundleModule otherModuleWithPath = usedPaths.putIfAbsent(entry.getPath(), module);
        if (otherModuleWithPath != null) {
          checkEqualEntries(entry.getPath(), otherModuleWithPath, module);
        }
      }
    }
  }

  private static void checkEqualEntries(ZipPath path, BundleModule module1, BundleModule module2) {
    ModuleEntry entry1 = module1.getEntry(path).get();
    ModuleEntry entry2 = module2.getEntry(path).get();
    if (!ModuleEntry.equal(entry1, entry2)) {
      throw ValidationException.builder()
          .withMessage(
              "Modules '%s' and '%s' contain entry '%s' with different content.",
              module1.getName(), module2.getName(), path)
          .build();
    }
  }
}

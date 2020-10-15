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
import static com.google.common.base.Predicates.not;

import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.BundleModule.ModuleType;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;

/**
 * Validates that no two modules contain a file with the same full path and different content.
 *
 * <p>The checks don't include special files that are defined in the format and expected to be
 * inherently different (eg. AndroidManifest.xml).
 */
public class EntryClashValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    if (!BundleValidationUtils.isAssetOnlyBundle(modules)) {
      BundleModule baseModule = BundleValidationUtils.expectBaseModule(modules);
      boolean isolatedSplits = baseModule.getAndroidManifest().getIsolatedSplits().orElse(false);
      if (isolatedSplits) {
        // For isolated splits, resources are loaded into separate asset
        // manager instances.
        return;
      }
    }
    checkEntryClashes(modules);
  }

  @VisibleForTesting
  static void checkEntryClashes(ImmutableList<BundleModule> modules) {
    Map<ZipPath, BundleModule> usedPaths = new HashMap<>();
    for (BundleModule module : modules) {
      module.getEntries().stream()
          .filter(not(EntryClashValidator::isExpectedToBeDifferent))
          .forEach(
              entry -> {
                BundleModule otherModuleWithPath = usedPaths.putIfAbsent(entry.getPath(), module);
                if (otherModuleWithPath != null) {
                  checkEntryClash(entry.getPath(), otherModuleWithPath, module);
                }
              });
    }
  }

  private static boolean isExpectedToBeDifferent(ModuleEntry entry) {
    return entry.getPath().startsWith(DEX_DIRECTORY);
  }

  private static void checkEntryClash(ZipPath path, BundleModule module1, BundleModule module2) {
    // For entries from asset modules we don't allow to have entry with the same name in any other
    // module of different type even with equal content. This is because entries in asset modules
    // are uncompressed and during standalone/universal apk generation we have a collision: should
    // we compress this entry or not.
    if (ModuleType.ASSET_MODULE.equals(module1.getModuleType())
        ^ ModuleType.ASSET_MODULE.equals(module2.getModuleType())) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Both modules '%s' and '%s' contain asset entry '%s'.",
              module1.getName(), module2.getName(), path)
          .build();
    }
    checkEqualEntries(path, module1, module2);
  }

  private static void checkEqualEntries(ZipPath path, BundleModule module1, BundleModule module2) {
    ModuleEntry entry1 = module1.getEntry(path).get();
    ModuleEntry entry2 = module2.getEntry(path).get();
    if (!entry1.equals(entry2)) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Modules '%s' and '%s' contain entry '%s' with different content.",
              module1.getName(), module2.getName(), path)
          .build();
    }
  }
}

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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.build.bundletool.archive.ArchivedResourcesUtils;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/** Validates that base module entry names do not clash with existing archiving resources */
public class ArchiveEntriesValidator extends SubValidator {

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    checkModuleEntryNames(modules);
  }

  private static void checkModuleEntryNames(ImmutableList<BundleModule> modules) {
    if (BundleValidationUtils.isAssetOnlyBundle(modules)) {
      return;
    }

    BundleModule baseModule = BundleValidationUtils.expectBaseModule(modules);
    ImmutableSet<String> reservedResourcePaths =
        ImmutableSet.of(
            BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                .resolve(
                    String.format("%s.xml", ArchivedResourcesUtils.OPACITY_LAYER_DRAWABLE_NAME))
                .toString(),
            BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                .resolve(String.format("%s.xml", ArchivedResourcesUtils.CLOUD_SYMBOL_DRAWABLE_NAME))
                .toString(),
            BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                .resolve(
                    String.format("%s.xml", ArchivedResourcesUtils.ARCHIVED_ICON_DRAWABLE_NAME))
                .toString(),
            BundleModule.DRAWABLE_RESOURCE_DIRECTORY
                .resolve(
                    String.format(
                        "%s.xml", ArchivedResourcesUtils.ARCHIVED_ROUND_ICON_DRAWABLE_NAME))
                .toString());
    ImmutableList<String> nameConflictingEntries =
        baseModule.getEntries().stream()
            .filter(entry -> reservedResourcePaths.contains(entry.getPath().toString()))
            .map(entry -> "'" + entry.getPath() + "'")
            .collect(toImmutableList());
    if (!nameConflictingEntries.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Following entry(s) name(s) in base module are reserved and must be changed: "
                  + Joiner.on(",").join(nameConflictingEntries))
          .build();
    }
  }
}

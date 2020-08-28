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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.newHashSet;

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.InvalidBundleException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;

/**
 * Validates the resource table.
 *
 * <p>Validated properties:
 *
 * <ul>
 *   <li>All files under the {@code res/} directory are referenced from the resource table.
 *   <li>All files referenced from the resource table are located under the {@code res/} directory.
 *   <li>All referenced files exist.
 * </ul>
 */
public class ResourceTableValidator extends SubValidator {

  @Override
  public void validateModule(BundleModule module) {
    // If module has no resource table, treat it as if the resource table were empty.
    ResourceTable resourceTable =
        module.getResourceTable().orElse(ResourceTable.getDefaultInstance());

    String moduleName = module.getName().getName();

    ImmutableSet<ZipPath> resFiles =
        module
            .findEntriesUnderPath(BundleModule.RESOURCES_DIRECTORY)
            .map(ModuleEntry::getPath)
            .collect(toImmutableSet());

    if (!resFiles.isEmpty() && !module.getResourceTable().isPresent()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' contains resource files but is missing a resource table.", moduleName)
          .build();
    }

    ImmutableSet<ZipPath> referencedFiles = ResourcesUtils.getAllFileReferences(resourceTable);

    for (ZipPath referencedFile : referencedFiles) {
      if (!referencedFile.startsWith(BundleModule.RESOURCES_DIRECTORY)) {
        throw InvalidBundleException.builder()
            .withUserMessage(
                "Resource table of module '%s' references file '%s' outside of the '%s/'"
                    + " directory.",
                moduleName, referencedFile, BundleModule.RESOURCES_DIRECTORY)
            .build();
      }
    }

    ImmutableSet<ZipPath> nonReferencedFiles =
        ImmutableSet.copyOf(difference(resFiles, referencedFiles));
    if (!nonReferencedFiles.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Module '%s' contains resource files that are not referenced from the resource"
                  + " table: %s",
              moduleName, nonReferencedFiles)
          .build();
    }

    ImmutableSet<ZipPath> nonExistingFiles =
        ImmutableSet.copyOf(difference(referencedFiles, resFiles));
    if (!nonExistingFiles.isEmpty()) {
      throw InvalidBundleException.builder()
          .withUserMessage(
              "Resource table of module '%s' contains references to non-existing files: %s",
              moduleName, nonExistingFiles)
          .build();
    }
  }

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    if (!BundleValidationUtils.isAssetOnlyBundle(modules)) {
      checkResourceIds(modules);
    }
  }

  @VisibleForTesting
  void checkResourceIds(ImmutableList<BundleModule> modules) {
    BundleModule baseModule = BundleValidationUtils.expectBaseModule(modules);
    if (baseModule.getAndroidManifest().getIsolatedSplits().orElse(false)) {
      // If splits are isolated, resource IDs cannot be duplicated between modules included in
      // fusing. It's ok to have inter-module duplication for modules not included in fusing, but we
      // still need to check for uniqueness within each module.
      ImmutableList<BundleModule> modulesIncludedInFusing =
          modules.stream().filter(BundleModule::isIncludedInFusing).collect(toImmutableList());
      checkResourceIdsAreUnique(modulesIncludedInFusing);
      for (BundleModule module : modules) {
        if (!module.isIncludedInFusing()) {
          checkResourceIdsAreUnique(ImmutableList.of(module));
        }
      }
    } else {
      checkResourceIdsAreUnique(modules);
    }
  }

  private static void checkResourceIdsAreUnique(ImmutableList<BundleModule> modules) {
    HashSet<ResourceId> usedResourceIds = newHashSet();
    for (BundleModule module : modules) {
      ResourceTable resourceTable =
          module.getResourceTable().orElse(ResourceTable.getDefaultInstance());
      ResourcesUtils.entries(resourceTable)
          .forEach(
              resourceTableEntry -> {
                boolean foundDuplicate = !usedResourceIds.add(resourceTableEntry.getResourceId());
                if (foundDuplicate) {
                  throw InvalidBundleException.builder()
                      .withUserMessage(
                          "Duplicate resource id (%s).",
                          resourceTableEntry.getResourceId().toString())
                      .build();
                }
              });
    }
  }
}

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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.newHashSet;
import static com.android.tools.build.bundletool.model.BundleModule.RESOURCES_DIRECTORIES;
import static com.android.tools.build.bundletool.model.BundleModule.RESOURCES_DIRECTORY;

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ResourceId;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ResouceTableException.ReferencesFileOutsideOfResException;
import com.android.tools.build.bundletool.model.exceptions.ResouceTableException.ReferencesMissingFilesException;
import com.android.tools.build.bundletool.model.exceptions.ResouceTableException.ResourceTableMissingException;
import com.android.tools.build.bundletool.model.exceptions.ResouceTableException.UnreferencedResourcesException;
import com.android.tools.build.bundletool.model.exceptions.ValidationException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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
        Stream.of(RESOURCES_DIRECTORIES)
            .flatMap(Collection::stream)
            .filter(path -> path.getNameCount()>0)
            .map(path -> module.findEntriesUnderPath(path)
                .collect(Collectors.toSet())
            ).flatMap(Collection::stream)
            .map(ModuleEntry::getPath)
            .collect(toImmutableSet());

    if (!resFiles.isEmpty() && !module.getResourceTable().isPresent()) {
      throw new ResourceTableMissingException(moduleName);
    }

    ImmutableSet<ZipPath> referencedFiles = ResourcesUtils.getAllFileReferences(resourceTable);

    OUT:
    for (ZipPath referencedFile : referencedFiles) {
      for (ZipPath dir : RESOURCES_DIRECTORIES) {
        if (referencedFile.startsWith(dir)) {
          continue OUT;
        }
      }
      throw new ReferencesFileOutsideOfResException(
          moduleName, referencedFile, RESOURCES_DIRECTORY);
    }

    ImmutableSet<ZipPath> nonReferencedFiles =
        ImmutableSet.copyOf(difference(resFiles, referencedFiles));
    if (!nonReferencedFiles.isEmpty()) {
      throw new UnreferencedResourcesException(moduleName, nonReferencedFiles);
    }

    ImmutableSet<ZipPath> nonExistingFiles =
        ImmutableSet.copyOf(difference(referencedFiles, resFiles));
    if (!nonExistingFiles.isEmpty()) {
      throw new ReferencesMissingFilesException(moduleName, nonExistingFiles);
    }
  }

  @Override
  public void validateAllModules(ImmutableList<BundleModule> modules) {
    checkResourceIdsAreUnique(modules);
  }

  @VisibleForTesting
  void checkResourceIdsAreUnique(ImmutableList<BundleModule> modules) {
    HashSet<ResourceId> usedResourceIds = newHashSet();
    for (BundleModule module : modules) {
      ResourceTable resourceTable =
          module.getResourceTable().orElse(ResourceTable.getDefaultInstance());
      ResourcesUtils.entries(resourceTable)
          .forEach(
              resourceTableEntry -> {
                boolean foundDuplicate = !usedResourceIds.add(resourceTableEntry.getResourceId());
                if (foundDuplicate) {
                  throw ValidationException.builder()
                      .withMessage(
                          "Duplicate resource id (%s).",
                          resourceTableEntry.getResourceId().toString())
                      .build();
                }
              });
    }
  }
}

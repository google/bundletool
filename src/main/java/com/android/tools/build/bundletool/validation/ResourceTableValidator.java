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

import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.BundleModule;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.android.tools.build.bundletool.model.exceptions.ResouceTableException.ReferencesFileOutsideOfResException;
import com.android.tools.build.bundletool.model.exceptions.ResouceTableException.ReferencesMissingFilesException;
import com.android.tools.build.bundletool.model.exceptions.ResouceTableException.ResourceTableMissingException;
import com.android.tools.build.bundletool.model.exceptions.ResouceTableException.UnreferencedResourcesException;
import com.android.tools.build.bundletool.model.utils.ResourcesUtils;
import com.google.common.collect.ImmutableSet;

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
      throw new ResourceTableMissingException(moduleName);
    }

    ImmutableSet<ZipPath> referencedFiles = ResourcesUtils.getAllFileReferences(resourceTable);

    for (ZipPath referencedFile : referencedFiles) {
      if (!referencedFile.startsWith(BundleModule.RESOURCES_DIRECTORY)) {
        throw new ReferencesFileOutsideOfResException(
            moduleName, referencedFile, BundleModule.RESOURCES_DIRECTORY);
      }
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
}

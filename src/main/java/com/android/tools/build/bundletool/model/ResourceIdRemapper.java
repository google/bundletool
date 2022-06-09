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
package com.android.tools.build.bundletool.model;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.ResourceTable;
import com.google.common.collect.Iterables;

/** Remaps resource IDs in a {@link BundleModule} with a new package ID. */
final class ResourceIdRemapper {

  /**
   * Updates package IDs in the resource table and XML resources of the given {@link BundleModule}
   * with the given {@code newResourcesPackageId}.
   */
  static BundleModule remapResourceIds(BundleModule module, int newResourcesPackageId) {
    return remapInResourceTable(module, newResourcesPackageId);
  }

  private static BundleModule remapInResourceTable(BundleModule module, int newResourcesPackageId) {
    if (!module.getResourceTable().isPresent()) {
      return module;
    }
    ResourceTable resourceTable = module.getResourceTable().get();
    checkArgument(
        resourceTable.getPackageCount() == 1,
        "Module '%s' contains resource table with %s 'package' entries, but only 1 entry is"
            + " allowed.",
        module.getName().getName(),
        resourceTable.getPackageCount());

    Package remappedPackage =
        Iterables.getOnlyElement(resourceTable.getPackageList()).toBuilder()
            .setPackageId(PackageId.newBuilder().setId(newResourcesPackageId))
            .build();
    return module.toBuilder()
        .setResourceTable(
            resourceTable.toBuilder().clearPackage().addPackage(remappedPackage).build())
        .build();
  }

  private ResourceIdRemapper() {}
}

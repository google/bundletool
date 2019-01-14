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

package com.android.tools.build.bundletool.model;

import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.EntryId;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Type;
import com.android.aapt.Resources.TypeId;
import com.android.tools.build.bundletool.model.exceptions.CommandExecutionException;
import java.util.List;

/**
 * Injects additional resource entries into {@link ResourceTable}.
 *
 * <p>It creates new ResourceTable if necessary.
 *
 * <p>Injects in the first package or creates a new package with id 0x7f.
 *
 * <p>Injects in the first suitable type or creates a new type if there is none.
 *
 * <p>Will throw exception if there is no free id's left.
 */
public class ResourceInjector {

  private static final int BASE_RESOURCE_TABLE_PACKAGE_ID = 0x7f;

  private final ResourceTable.Builder resourceTable;
  private final String packageName;

  ResourceInjector(ResourceTable.Builder resourceTable, String packageName) {
    this.resourceTable = resourceTable;
    this.packageName = packageName;
  }

  public static ResourceInjector fromModuleSplit(ModuleSplit moduleSplit) {
    return new ResourceInjector(
        moduleSplit
            .getResourceTable()
            .map(ResourceTable::toBuilder)
            .orElseGet(ResourceTable::newBuilder),
        moduleSplit.getAndroidManifest().getPackageName());
  }

  public ResourceId addResource(String entryType, Entry entry) {
    ResourceId.Builder resourceIdBuilder = ResourceId.builder();

    Package.Builder packageBuilder;
    if (resourceTable.getPackageCount() == 0) {
      packageBuilder =
          resourceTable
              .addPackageBuilder()
              .setPackageName(packageName)
              .setPackageId(PackageId.newBuilder().setId(BASE_RESOURCE_TABLE_PACKAGE_ID));
    } else {
      packageBuilder = resourceTable.getPackageBuilder(0);
    }

    resourceIdBuilder.setPackageId(packageBuilder.getPackageId().getId());
    addResourceToPackage(packageBuilder, resourceIdBuilder, entryType, entry);
    return resourceIdBuilder.build();
  }

  public ResourceTable build() {
    return resourceTable.build();
  }

  private static void addResourceToPackage(
      Package.Builder resourcePackage,
      ResourceId.Builder resourceId,
      String entryType,
      Entry entry) {
    Type.Builder type =
        resourcePackage.getTypeBuilderList().stream()
            .filter(t -> t.getName().equals(entryType))
            .findFirst()
            .orElseGet(
                () ->
                    resourcePackage
                        .addTypeBuilder()
                        .setName(entryType)
                        .setTypeId(
                            TypeId.newBuilder()
                                .setId(getNextTypeId(resourcePackage.getTypeList()))));
    resourceId.setTypeId(type.getTypeId().getId());
    addResourceToType(type, resourceId, entry);
  }

  private static void addResourceToType(
      Type.Builder type, ResourceId.Builder resourceId, Entry entry) {
    int nextEntryId = getNextEntryId(type.getEntryList());
    resourceId.setEntryId(nextEntryId);
    type.addEntry(entry.toBuilder().setEntryId(EntryId.newBuilder().setId(nextEntryId)));
  }

  private static int getNextEntryId(List<Entry> entryList) {
    int highestEntryId =
        entryList.stream().mapToInt(entry -> entry.getEntryId().getId()).max().orElse(-1);
    if (highestEntryId >= ResourceId.MAX_ENTRY_ID) {
      throw new CommandExecutionException("No free entry id left in the resource table.");
    }
    return highestEntryId + 1;
  }

  private static int getNextTypeId(List<Type> typeList) {
    int highestTypeId =
        typeList.stream().mapToInt(type -> type.getTypeId().getId()).max().orElse(0);
    if (highestTypeId >= ResourceId.MAX_TYPE_ID) {
      throw new CommandExecutionException("No free type id left in the resource table.");
    }
    return highestTypeId + 1;
  }
}

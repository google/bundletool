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

import com.android.aapt.Resources;
import com.android.aapt.Resources.CompoundValue;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.EntryId;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.PackageId;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.Type;
import com.android.aapt.Resources.TypeId;
import com.android.aapt.Resources.Value;
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
  private static final String STRING_ENTRY_TYPE = "string";
  private static final String DRAWABLE_ENTRY_TYPE = "drawable";
  private static final String LAYOUT_ENTRY_TYPE = "layout";
  private static final String STYLE_ENTRY_TYPE = "style";

  private final ResourceTable.Builder resourceTable;
  private final String packageName;

  public ResourceInjector(ResourceTable.Builder resourceTable, String packageName) {
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

  public ResourceId addStringResource(String name, String value) {
    Entry resourceEntry =
        Entry.newBuilder()
            .setName(name)
            .addConfigValue(
                ConfigValue.newBuilder()
                    .setValue(
                        Value.newBuilder()
                            .setItem(
                                Item.newBuilder()
                                    .setStr(Resources.String.newBuilder().setValue(value)))))
            .build();
    return addResource(STRING_ENTRY_TYPE, resourceEntry);
  }

  public ResourceId addXmlDrawableResource(String drawableName, String fileReference) {
    Entry drawableEntity =
        Entry.newBuilder()
            .setName(drawableName)
            .addConfigValue(
                ConfigValue.newBuilder()
                    .setValue(
                        Value.newBuilder()
                            .setItem(
                                Item.newBuilder()
                                    .setFile(
                                        FileReference.newBuilder()
                                            .setPath(fileReference)
                                            .setType(FileReference.Type.PROTO_XML)))))
            .build();
    return addResource(DRAWABLE_ENTRY_TYPE, drawableEntity);
  }

  public ResourceId addLayoutResource(String layoutName, String fileReference) {
    Entry layoutEntry =
        Entry.newBuilder()
            .setName(layoutName)
            .addConfigValue(
                ConfigValue.newBuilder()
                    .setValue(
                        Value.newBuilder()
                            .setItem(
                                Item.newBuilder()
                                    .setFile(
                                        FileReference.newBuilder()
                                            .setPath(fileReference)
                                            .setType(FileReference.Type.PROTO_XML)))))
            .build();
    return addResource(LAYOUT_ENTRY_TYPE, layoutEntry);
  }

  public ResourceId addStyleResource(String styleName, Style style) {
    Entry layoutEntry =
        Entry.newBuilder()
            .setName(styleName)
            .addConfigValue(
                ConfigValue.newBuilder()
                    .setValue(
                        Value.newBuilder()
                            .setCompoundValue(CompoundValue.newBuilder().setStyle(style))))
            .build();
    return addResource(STYLE_ENTRY_TYPE, layoutEntry);
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
      throw CommandExecutionException.builder()
          .withInternalMessage("No free entry id left in the resource table.")
          .build();
    }
    return highestEntryId + 1;
  }

  private static int getNextTypeId(List<Type> typeList) {
    int highestTypeId =
        typeList.stream().mapToInt(type -> type.getTypeId().getId()).max().orElse(0);
    if (highestTypeId >= ResourceId.MAX_TYPE_ID) {
      throw CommandExecutionException.builder()
          .withInternalMessage("No free type id left in the resource table.")
          .build();
    }
    return highestTypeId + 1;
  }
}

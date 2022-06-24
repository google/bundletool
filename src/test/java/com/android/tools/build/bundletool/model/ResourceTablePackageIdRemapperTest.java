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

import static com.android.tools.build.bundletool.testing.ManifestProtoUtils.androidManifest;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.USER_PACKAGE_OFFSET;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.Array;
import com.android.aapt.Resources.Attribute;
import com.android.aapt.Resources.Attribute.Symbol;
import com.android.aapt.Resources.CompoundValue;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Plural;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.Styleable;
import com.android.aapt.Resources.Value;
import com.android.tools.build.bundletool.testing.BundleModuleBuilder;
import com.android.tools.build.bundletool.testing.ResourcesTableFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ResourceTablePackageIdRemapper}. */
@RunWith(JUnit4.class)
public final class ResourceTablePackageIdRemapperTest {

  private static final int NEW_PACKAGE_ID = 0x82;
  private static final ResourceTablePackageIdRemapper resourceTablePackageIdRemapper =
      new ResourceTablePackageIdRemapper(NEW_PACKAGE_ID);

  @Test
  public void moduleHasNoResourceTable_noChange() {
    BundleModule module =
        new BundleModuleBuilder("comTestSdk").setManifest(androidManifest("comTestSdk")).build();

    BundleModule remappedModule = resourceTablePackageIdRemapper.remap(module);
    assertThat(remappedModule).isEqualTo(module);
  }

  @Test
  public void resourceTableSpecifiesMultiplePackages_throws() {
    BundleModule module =
        new BundleModuleBuilder("comTestSdk")
            .setManifest(androidManifest("comTestSdk"))
            .setResourceTable(
                ResourcesTableFactory.resourceTable(
                    pkg(USER_PACKAGE_OFFSET, "pkg1"), pkg(USER_PACKAGE_OFFSET, "pkg2")))
            .build();

    Throwable e =
        assertThrows(
            IllegalArgumentException.class, () -> resourceTablePackageIdRemapper.remap(module));
    assertThat(e)
        .hasMessageThat()
        .contains(
            "Module 'comTestSdk' contains resource table with 2 'package' entries, but only 1 entry"
                + " is allowed.");
  }

  @Test
  public void remapInPackageIdAndAllReferences() {
    BundleModule module =
        new BundleModuleBuilder("comTestSdk")
            .setManifest(androidManifest("comTestSdk"))
            .setResourceTable(getFullResourceTable(USER_PACKAGE_OFFSET))
            .build();

    module = resourceTablePackageIdRemapper.remap(module);

    assertThat(module.getResourceTable()).hasValue(getFullResourceTable(NEW_PACKAGE_ID));
  }

  /** Returns resource table with all possible fields of type Reference set. */
  private static ResourceTable getFullResourceTable(int packageId) {
    int typeId = 1;
    int entryId = 1;
    return resourceTable(
        pkg(
            packageId,
            "pkg",
            type(
                typeId++,
                "type" + typeId,
                entry(
                    entryId++,
                    "entry" + entryId,
                    ConfigValue.newBuilder()
                        .setValue(Value.newBuilder().setItem(item(packageId, typeId, entryId)))
                        .build(),
                    ConfigValue.newBuilder()
                        .setValue(
                            Value.newBuilder()
                                .setCompoundValue(
                                    CompoundValue.newBuilder()
                                        .setAttr(
                                            Attribute.newBuilder()
                                                .addSymbol(
                                                    Symbol.newBuilder()
                                                        .setName(
                                                            reference(
                                                                packageId, typeId, entryId))))))
                        .build()),
                entry(
                    entryId++,
                    "entry" + entryId,
                    ConfigValue.newBuilder()
                        .setValue(
                            Value.newBuilder()
                                .setCompoundValue(
                                    CompoundValue.newBuilder()
                                        .setStyle(
                                            Style.newBuilder()
                                                .setParent(reference(packageId, typeId, entryId))
                                                .addEntry(
                                                    Style.Entry.newBuilder()
                                                        .setKey(
                                                            reference(
                                                                packageId, typeId, entryId))))))
                        .build(),
                    ConfigValue.newBuilder()
                        .setValue(
                            Value.newBuilder()
                                .setCompoundValue(
                                    CompoundValue.newBuilder()
                                        .setStyleable(
                                            Styleable.newBuilder()
                                                .addEntry(
                                                    Styleable.Entry.newBuilder()
                                                        .setAttr(
                                                            reference(
                                                                packageId, typeId, entryId))))))
                        .build())),
            type(
                typeId++,
                "type" + typeId,
                entry(
                    entryId++,
                    "entry" + entryId,
                    ConfigValue.newBuilder()
                        .setValue(
                            Value.newBuilder()
                                .setCompoundValue(
                                    CompoundValue.newBuilder()
                                        .setArray(
                                            Array.newBuilder()
                                                .addElement(
                                                    Array.Element.newBuilder()
                                                        .setItem(item(packageId, typeId, entryId)))
                                                .addElement(
                                                    Array.Element.newBuilder()
                                                        .setItem(
                                                            item(packageId, typeId, entryId))))))
                        .build(),
                    ConfigValue.newBuilder()
                        .setValue(
                            Value.newBuilder()
                                .setCompoundValue(
                                    CompoundValue.newBuilder()
                                        .setPlural(
                                            Plural.newBuilder()
                                                .addEntry(
                                                    Plural.Entry.newBuilder()
                                                        .setItem(
                                                            item(packageId, typeId, entryId))))))
                        .build()))));
  }

  private static Item item(int packageId, int typeId, int entryId) {
    return Item.newBuilder().setRef(reference(packageId, typeId, entryId)).build();
  }

  private static Reference reference(int packageId, int typeId, int entryId) {
    return Reference.newBuilder().setId(0x1000000 * packageId + 0x10000 * typeId + entryId).build();
  }
}

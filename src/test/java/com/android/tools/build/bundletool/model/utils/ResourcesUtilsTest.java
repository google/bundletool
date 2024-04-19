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

package com.android.tools.build.bundletool.model.utils;

import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.fileReference;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.ResourceTable;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.testing.ResourceTableBuilder;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResourcesUtilsTest {

  @Test
  public void filter_noFiltering_returnsIdentity() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                0x7f,
                "package.without.density.resources",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x00,
                        "image",
                        fileReference(
                            "res/drawable/image.png", Configuration.getDefaultInstance())))));

    ResourceTable filteredTable =
        ResourcesUtils.filterResourceTable(
            table, Predicates.alwaysFalse(), ResourceTableEntry::getEntry);

    assertThat(filteredTable).ignoringRepeatedFieldOrder().isEqualTo(table);
  }

  @Test
  public void filter_withConfigValuesFilterFn() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                0x7f,
                "package.without.density.resources",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x00,
                        "image",
                        fileReference("res/drawable/image.png", Configuration.getDefaultInstance()),
                        fileReference(
                            "res/drawable/image-2.png", Configuration.getDefaultInstance())))));

    ResourceTable filteredTable =
        ResourcesUtils.filterResourceTable(
            table,
            Predicates.alwaysFalse(),
            resourceTableEntry ->
                resourceTableEntry.getEntry().toBuilder().removeConfigValue(1).build());

    assertThat(filteredTable)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(
                pkg(
                    0x7f,
                    "package.without.density.resources",
                    type(
                        0x01,
                        "drawable",
                        entry(
                            0x00,
                            "image",
                            fileReference(
                                "res/drawable/image.png", Configuration.getDefaultInstance()))))));
  }

  @Test
  public void filter_withRemoveTypePredicate() throws Exception {
    ResourceTable table =
        resourceTable(
            pkg(
                0x7f,
                "package.without.density.resources",
                type(
                    0x01,
                    "drawable",
                    entry(
                        0x00,
                        "layout_main",
                        fileReference(
                            "res/drawable/image.png", Configuration.getDefaultInstance()))),
                type(
                    0x02,
                    "layout",
                    entry(
                        0x00,
                        "layout_menu",
                        fileReference(
                            "res/layout/menu.xml", Configuration.getDefaultInstance())))));

    ResourceTable filteredTable =
        ResourcesUtils.filterResourceTable(
            table,
            resource -> resource.getType().getTypeId().getId() == 0x02,
            ResourceTableEntry::getEntry);

    assertThat(filteredTable)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(
                pkg(
                    0x7f,
                    "package.without.density.resources",
                    type(
                        0x01,
                        "drawable",
                        entry(
                            0x00,
                            "layout_main",
                            fileReference(
                                "res/drawable/image.png", Configuration.getDefaultInstance()))))));
  }

  @Test
  public void filter_withEntryPredicate() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app")
            .addDrawableResource("image1", "drawable/image1.jpg")
            .addDrawableResource("image2", "drawable/image2.jpg")
            .addStringResource("label_hello", "Hello world")
            .build();

    ResourceTable filteredTable =
        ResourcesUtils.filterResourceTable(
            resourceTable,
            /* removeEntryPredicate= */ resourceTableEntry ->
                resourceTableEntry.getResourceId().getFullResourceId()
                    == 0x7f010001 /* image2.jpg */,
            ResourceTableEntry::getEntry);

    assertThat(filteredTable)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            new ResourceTableBuilder()
                .addPackage("com.test.app")
                .addDrawableResource("image1", "drawable/image1.jpg")
                .addStringResource("label_hello", "Hello world")
                .build());
  }

  @Test
  public void filter_removesEmptyPackagesTypesAndEntries() throws Exception {
    ResourceTable table =
        resourceTable(
            // Package should be filtered out.
            pkg(
                0x7f,
                "package.without.density.resources",
                type(
                    0x01,
                    "layout",
                    entry(
                        0x00,
                        "layout_main",
                        fileReference("res/layout/main.xml", Configuration.getDefaultInstance())))),
            pkg(
                0x80,
                "package.with.density.resources",
                // Type should be filtered out.
                type(
                    0x01,
                    "layout",
                    entry(
                        0x00,
                        "layout_menu",
                        fileReference("res/layout/menu.xml", Configuration.getDefaultInstance()))),
                type(
                    0x02,
                    "image",
                    // Entry should be filtered out.
                    entry(
                        0x00,
                        "icon1",
                        fileReference(
                            "res/drawable/icon1.png", Configuration.getDefaultInstance())),
                    entry(
                        0x01,
                        "icon2",
                        // ConfigValue should be filtered out.
                        fileReference("res/drawable/icon2.png", Configuration.getDefaultInstance()),
                        fileReference("res/drawable-mdpi/icon2.png", MDPI)))));

    ResourceTable filteredTable =
        ResourcesUtils.filterResourceTable(
            table, Predicates.alwaysFalse(), ResourcesUtilsTest::removeDefaultResources);

    assertThat(filteredTable)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(
                pkg(
                    0x80,
                    "package.with.density.resources",
                    type(
                        0x02,
                        "image",
                        entry(
                            0x01, "icon2", fileReference("res/drawable-mdpi/icon2.png", MDPI))))));
  }

  @Test
  public void resourcesLocaleConversions_oldLanguageCodes() {
    // This documents that our converter will use old ISO-639 language codes for backward
    // compatibility.

    assertThat(ResourcesUtils.convertLocaleToLanguage("he")).isEqualTo("iw");
    assertThat(ResourcesUtils.convertLocaleToLanguage("yi")).isEqualTo("ji");
    assertThat(ResourcesUtils.convertLocaleToLanguage("id")).isEqualTo("in");
  }

  @Test
  public void lookupEntryByResourceId_positiveId() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app", 0x7F)
            // 0x7F010000
            .addXmlResource("layout", "res/xml/layout.xml")
            // 0x7F020000
            .addStringResource("hello", "res/string/hello.xml")
            // 0x7F020001
            .addStringResource("world", "res/string/world.xml")
            .build();

    Optional<Entry> layoutResource =
        ResourcesUtils.lookupEntryByResourceId(resourceTable, 0x7F010000);
    assertThat(layoutResource).isPresent();
    assertThat(extractFilePathValues(layoutResource.get())).containsExactly("res/xml/layout.xml");

    Optional<Entry> helloResource =
        ResourcesUtils.lookupEntryByResourceId(resourceTable, 0x7F020000);
    assertThat(helloResource).isPresent();
    assertThat(extractStringValues(helloResource.get())).containsExactly("res/string/hello.xml");

    Optional<Entry> worldResource =
        ResourcesUtils.lookupEntryByResourceId(resourceTable, 0x7F020001);
    assertThat(worldResource).isPresent();
    assertThat(extractStringValues(worldResource.get())).containsExactly("res/string/world.xml");
  }

  @Test
  public void lookupEntryByResourceId_negativeId() {
    ResourceTable resourceTable =
        new ResourceTableBuilder()
            .addPackage("com.test.app", 0x7F)
            // 0x7F010000
            .addXmlResource("layout", "res/xml/not-the-layout-we-want.xml")
            .addPackage("com.test.app.split", 0x80)
            // 0x80010000
            .addXmlResource("layout", "res/xml/layout.xml")
            // 0x80020000
            .addStringResource("hello", "res/string/hello.xml")
            // 0x80020001
            .addStringResource("world", "res/string/world.xml")
            .build();

    Optional<Entry> layoutResource =
        ResourcesUtils.lookupEntryByResourceId(resourceTable, 0x80010000);
    assertThat(layoutResource).isPresent();
    assertThat(extractFilePathValues(layoutResource.get())).containsExactly("res/xml/layout.xml");

    Optional<Entry> helloResource =
        ResourcesUtils.lookupEntryByResourceId(resourceTable, 0x80020000);
    assertThat(helloResource).isPresent();
    assertThat(extractStringValues(helloResource.get())).containsExactly("res/string/hello.xml");

    Optional<Entry> worldResource =
        ResourcesUtils.lookupEntryByResourceId(resourceTable, 0x80020001);
    assertThat(worldResource).isPresent();
    assertThat(extractStringValues(worldResource.get())).containsExactly("res/string/world.xml");
  }

  private static Entry removeDefaultResources(ResourceTableEntry entry) {
    return entry
        .getEntry()
        .toBuilder()
        .clearConfigValue()
        .addAllConfigValue(
            Iterables.filter(
                entry.getEntry().getConfigValueList(),
                configValue -> !configValue.getConfig().equals(Configuration.getDefaultInstance())))
        .build();
  }

  private static ImmutableList<String> extractFilePathValues(Entry entry) {
    return entry
        .getConfigValueList()
        .stream()
        .map(configValue -> configValue.getValue().getItem().getFile().getPath())
        .collect(toImmutableList());
  }

  private static ImmutableList<String> extractStringValues(Entry entry) {
    return entry
        .getConfigValueList()
        .stream()
        .map(configValue -> configValue.getValue().getItem().getStr().getValue())
        .collect(toImmutableList());
  }
}

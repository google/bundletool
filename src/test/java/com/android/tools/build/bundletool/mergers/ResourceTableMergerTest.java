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

package com.android.tools.build.bundletool.mergers;

import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.LDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.MDPI;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.entry;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.pkg;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.resourceTable;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.type;
import static com.android.tools.build.bundletool.testing.ResourcesTableFactory.value;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.android.aapt.Resources.AllowNew;
import com.android.aapt.Resources.CompoundValue;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Overlayable;
import com.android.aapt.Resources.OverlayableItem;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Source;
import com.android.aapt.Resources.StringPool;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.Visibility;
import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResourceTableMergerTest {

  private static final byte[] TEST_BYTES = new byte[1];

  @Test
  public void packagesWithMatchingIds_merged() throws Exception {
    ResourceTable table1 = resourceTable(pkg(0x01, "package"));
    ResourceTable table2 = resourceTable(pkg(0x01, "package"));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged).ignoringRepeatedFieldOrder().isEqualTo(resourceTable(pkg(0x01, "package")));
  }

  @Test
  public void packagesWithDifferentIds_concatenated() throws Exception {
    ResourceTable table1 = resourceTable(pkg(0x01, "package01"));
    ResourceTable table2 = resourceTable(pkg(0x02, "package02"));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(resourceTable(pkg(0x01, "package01"), pkg(0x02, "package02")));
  }

  @Test
  public void typesWithMatchingIds_merged() throws Exception {
    ResourceTable table1 = resourceTable(pkg(0x01, "package", type(0x11, "type")));
    ResourceTable table2 = resourceTable(pkg(0x01, "package", type(0x11, "type")));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(resourceTable(pkg(0x01, "package", type(0x11, "type"))));
  }

  @Test
  public void typesWithDifferentIds_concatenated() throws Exception {
    ResourceTable table1 = resourceTable(pkg(0x01, "package", type(0x11, "type1")));
    ResourceTable table2 = resourceTable(pkg(0x01, "package", type(0x12, "type2")));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(resourceTable(pkg(0x01, "package", type(0x11, "type1"), type(0x12, "type2"))));
  }

  @Test
  public void entriesWithMatchingIds_merged() throws Exception {
    ResourceTable table1 =
        resourceTable(pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry"))));
    ResourceTable table2 =
        resourceTable(pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry"))));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(resourceTable(pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry")))));
  }

  @Test
  public void entriesWithDifferentIds_concatenated() throws Exception {
    ResourceTable table1 =
        resourceTable(pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry1"))));
    ResourceTable table2 =
        resourceTable(pkg(0x01, "package", type(0x11, "type", entry(0x22, "entry2"))));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(
                pkg(
                    0x01,
                    "package",
                    type(0x11, "type", entry(0x21, "entry1"), entry(0x22, "entry2")))));
  }

  @Test
  public void uniqueConfigValues_concatenated() throws Exception {
    ResourceTable table1 =
        resourceTable(
            pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI)))));
    ResourceTable table2 =
        resourceTable(
            pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry", value("mdpi", MDPI)))));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(
                pkg(
                    0x01,
                    "package",
                    type(
                        0x11,
                        "type",
                        entry(0x21, "entry", value("ldpi", LDPI), value("mdpi", MDPI))))));
  }

  @Test
  public void duplicateConfigValues_deduplicated() throws Exception {
    ResourceTable table1 =
        resourceTable(
            pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI)))));
    ResourceTable table2 =
        resourceTable(
            pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI)))));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(
                pkg(
                    0x01,
                    "package",
                    type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI))))));
  }

  @Test
  public void sourcePools_absent_okAndPreserved() throws Exception {
    ResourceTable table = resourceTable();
    assertThat(table.hasSourcePool()).isFalse();

    ResourceTable merged = new ResourceTableMerger().merge(table, table);

    assertThat(merged.hasSourcePool()).isFalse();
  }

  @Test
  public void sourcePools_presentAndEqual_okAndPreserved() throws Exception {
    StringPool sourcePool =
        StringPool.newBuilder()
            .setData(ByteString.copyFrom("hello", StandardCharsets.UTF_8))
            .build();
    ResourceTable table = resourceTable().toBuilder().setSourcePool(sourcePool).build();
    assertThat(table.hasSourcePool()).isTrue();

    ResourceTable merged = new ResourceTableMerger().merge(table, table);

    assertThat(merged.hasSourcePool()).isTrue();
    assertThat(merged.getSourcePool()).isEqualTo(sourcePool);
  }

  @Test
  public void sourcePools_different_okAndSourcePoolOfFirstTablePreserved() throws Exception {
    ResourceTable table1 = ResourceTable.getDefaultInstance();
    ResourceTable table2 =
        table1.toBuilder()
            .setSourcePool(StringPool.newBuilder().setData(ByteString.copyFrom(TEST_BYTES)))
            .build();

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged.getSourcePool()).isEqualTo(table1.getSourcePool());
  }

  @Test
  public void sourcePools_different_sourceReferencesInSecondTableStripped() throws Exception {
    Source source1 = Source.newBuilder().setPathIdx(42).build();
    Source source2 = Source.newBuilder().setPathIdx(24).build();
    ResourceTable table1 =
        resourceTable(
            pkg(
                0x01,
                "package",
                type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI, source1)))));
    ResourceTable table2 =
        resourceTable(
            StringPool.newBuilder().setData(ByteString.copyFrom(TEST_BYTES)).build(),
            pkg(
                0x02,
                "package",
                type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI, source2)))));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(
                pkg(
                    0x01,
                    "package",
                    type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI, source1)))),
                pkg(
                    0x02,
                    "package",
                    type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI))))));
  }

  @Test
  public void sourcePools_equal_sourceReferencesPreserved() throws Exception {
    StringPool commonSourcePool =
        StringPool.newBuilder().setData(ByteString.copyFrom(TEST_BYTES)).build();
    Source source1 = Source.newBuilder().setPathIdx(42).build();
    Source source2 = Source.newBuilder().setPathIdx(24).build();
    ResourceTable table1 =
        resourceTable(
            commonSourcePool,
            pkg(
                0x01,
                "package",
                type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI, source1)))));
    ResourceTable table2 =
        resourceTable(
            commonSourcePool,
            pkg(
                0x02,
                "package",
                type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI, source2)))));

    ResourceTable merged = new ResourceTableMerger().merge(table1, table2);

    assertThat(merged)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            resourceTable(
                commonSourcePool,
                pkg(
                    0x01,
                    "package",
                    type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI, source1)))),
                pkg(
                    0x02,
                    "package",
                    type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI, source2))))));
  }

  @Test
  public void packageNames_different_throws() throws Exception {
    ResourceTable table1 = resourceTable(pkg(0x01, "nameA"));
    ResourceTable table2 = resourceTable(pkg(0x01, "nameB"));

    Throwable exception =
        assertThrows(
            IllegalStateException.class, () -> new ResourceTableMerger().merge(table1, table2));

    assertThat(exception).hasMessageThat().contains("Expected same values of field 'package_name'");
  }

  @Test
  public void typeNames_different_throws() throws Exception {
    ResourceTable table1 = resourceTable(pkg(0x01, "com.test.app", type(0x11, "nameA")));
    ResourceTable table2 = resourceTable(pkg(0x01, "com.test.app", type(0x11, "nameB")));

    Throwable exception =
        assertThrows(
            IllegalStateException.class, () -> new ResourceTableMerger().merge(table1, table2));

    assertThat(exception).hasMessageThat().contains("Expected same values of field 'name'");
  }

  @Test
  public void entryNames_different_throws() throws Exception {
    ResourceTable table1 =
        resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entry(0x21, "nameA"))));
    ResourceTable table2 =
        resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entry(0x21, "nameB"))));

    Throwable exception =
        assertThrows(
            IllegalStateException.class, () -> new ResourceTableMerger().merge(table1, table2));

    assertThat(exception).hasMessageThat().contains("Expected same values of field 'name'");
  }

  @Test
  public void entryVisibilities_absent_okAndPreserved() throws Exception {
    Entry entry = entry(0x21, "name").toBuilder().clearVisibility().build();
    ResourceTable table = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entry)));

    ResourceTable merged = new ResourceTableMerger().merge(table, table);

    assertThat(merged.getPackage(0).getType(0).getEntry(0).hasVisibility()).isFalse();
  }

  @Test
  public void entryVisibilities_presentAndEqual_okAndPreserved() throws Exception {
    Visibility visibility = Visibility.newBuilder().setComment("comment").build();
    Entry entry = entry(0x21, "name").toBuilder().setVisibility(visibility).build();
    ResourceTable table = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entry)));

    ResourceTable merged = new ResourceTableMerger().merge(table, table);

    assertThat(merged.getPackage(0).getType(0).getEntry(0).hasVisibility()).isTrue();
    assertThat(merged.getPackage(0).getType(0).getEntry(0).getVisibility()).isEqualTo(visibility);
  }

  @Test
  public void entryVisibilities_different_throws() throws Exception {
    Entry entryA = entry(0x21, "name");
    Entry entryB =
        entryA.toBuilder().setVisibility(Visibility.newBuilder().setComment("difference")).build();
    ResourceTable table1 = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entryA)));
    ResourceTable table2 = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entryB)));

    Throwable exception =
        assertThrows(
            IllegalStateException.class, () -> new ResourceTableMerger().merge(table1, table2));

    assertThat(exception).hasMessageThat().contains("Expected same values of field 'visibility'");
  }

  @Test
  public void entryAllowNew_absent_okAndPreserved() throws Exception {
    Entry entry = entry(0x21, "name").toBuilder().clearAllowNew().build();
    ResourceTable table = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entry)));

    ResourceTable merged = new ResourceTableMerger().merge(table, table);

    assertThat(merged.getPackage(0).getType(0).getEntry(0).hasAllowNew()).isFalse();
  }

  @Test
  public void entryAllowNew_presentAndEqual_okAndPreserved() throws Exception {
    AllowNew allowNew = AllowNew.newBuilder().setComment("comment").build();
    Entry entry = entry(0x21, "name").toBuilder().setAllowNew(allowNew).build();
    ResourceTable table = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entry)));

    ResourceTable merged = new ResourceTableMerger().merge(table, table);

    assertThat(merged.getPackage(0).getType(0).getEntry(0).hasAllowNew()).isTrue();
    assertThat(merged.getPackage(0).getType(0).getEntry(0).getAllowNew()).isEqualTo(allowNew);
  }

  @Test
  public void entryAllowNew_different_throws() throws Exception {
    Entry entryA = entry(0x21, "name");
    Entry entryB =
        entryA.toBuilder().setAllowNew(AllowNew.newBuilder().setComment("difference")).build();
    ResourceTable table1 = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entryA)));
    ResourceTable table2 = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entryB)));

    Throwable exception =
        assertThrows(
            IllegalStateException.class, () -> new ResourceTableMerger().merge(table1, table2));

    assertThat(exception).hasMessageThat().contains("Expected same values of field 'allow_new'");
  }

  @Test
  public void entryOverlayable_absent_okAndPreserved() throws Exception {
    Entry entry = entry(0x21, "name").toBuilder().clearOverlayableItem().build();
    ResourceTable table = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entry)));

    ResourceTable merged = new ResourceTableMerger().merge(table, table);

    assertThat(merged.getPackage(0).getType(0).getEntry(0).hasOverlayableItem()).isFalse();
  }

  @Test
  public void entryOverlayable_presentAndEqual_okAndPreserved() throws Exception {
    OverlayableItem overlayable = OverlayableItem.newBuilder().setComment("comment").build();
    Entry entry = entry(0x21, "name").toBuilder().setOverlayableItem(overlayable).build();
    ResourceTable table = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entry)));

    ResourceTable merged = new ResourceTableMerger().merge(table, table);

    assertThat(merged.getPackage(0).getType(0).getEntry(0).hasOverlayableItem()).isTrue();
    assertThat(merged.getPackage(0).getType(0).getEntry(0).getOverlayableItem())
        .isEqualTo(overlayable);
  }

  @Test
  public void entryOverlayable_different_throws() throws Exception {
    Entry entryA = entry(0x21, "name");
    Entry entryB =
        entryA
            .toBuilder()
            .setOverlayableItem(OverlayableItem.newBuilder().setComment("comment"))
            .build();
    ResourceTable table1 = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entryA)));
    ResourceTable table2 = resourceTable(pkg(0x01, "com.test.app", type(0x11, "strings", entryB)));

    Throwable exception =
        assertThrows(
            IllegalStateException.class, () -> new ResourceTableMerger().merge(table1, table2));

    assertThat(exception)
        .hasMessageThat()
        .contains("Expected same values of field 'overlayable_item'");
  }

  @Test
  public void stripSourceReferences_noSourceReferences_unchanged() throws Exception {
    ResourceTable withoutSource =
        resourceTable(
            pkg(0x01, "package", type(0x11, "type", entry(0x21, "entry", value("ldpi", LDPI)))));

    ResourceTable.Builder stripped = withoutSource.toBuilder();
    ResourceTableMerger.stripSourceReferences(stripped);

    assertThat(stripped.build()).isEqualTo(withoutSource);
  }

  @Test
  public void stripSourceReferences_inTopLevelField_strips() throws Exception {
    Style withoutSource =
        Style.newBuilder().setParent(Reference.newBuilder().setName("dad")).build();
    Style withSource =
        withoutSource.toBuilder().setParentSource(Source.newBuilder().setPathIdx(42)).build();
    assertThat(withSource).isNotEqualTo(withoutSource);

    Style.Builder stripped = withSource.toBuilder();
    ResourceTableMerger.stripSourceReferences(stripped);

    assertThat(stripped.build()).isEqualTo(withoutSource);
  }

  @Test
  public void stripSourceReferences_inRepeatedField_strips() throws Exception {
    Style withoutSource =
        Style.newBuilder()
            .setParent(Reference.newBuilder().setName("dad"))
            .addEntry(Style.Entry.newBuilder().setComment("comment"))
            .build();
    Style.Builder withSourceBuilder = withoutSource.toBuilder();
    withSourceBuilder.getEntryBuilder(0).setSource(Source.newBuilder().setPathIdx(42));
    Style withSource = withSourceBuilder.build();
    assertThat(withSource).isNotEqualTo(withoutSource);

    Style.Builder stripped = withSource.toBuilder();
    ResourceTableMerger.stripSourceReferences(stripped);

    assertThat(stripped.build()).isEqualTo(withoutSource);
  }

  @Test
  public void stripSourceReferences_inOneofField_strips() throws Exception {
    CompoundValue withoutSource =
        CompoundValue.newBuilder()
            .setStyle(
                Style.newBuilder()
                    .setParent(Reference.newBuilder().setName("dad"))
                    .addEntry(Style.Entry.newBuilder().setComment("comment")))
            .build();
    CompoundValue.Builder withSourceBuilder = withoutSource.toBuilder();
    withSourceBuilder.getStyleBuilder().setParentSource(Source.newBuilder().setPathIdx(42));
    CompoundValue withSource = withSourceBuilder.build();
    assertThat(withSource).isNotEqualTo(withoutSource);

    CompoundValue.Builder stripped = withSource.toBuilder();
    ResourceTableMerger.stripSourceReferences(stripped);

    assertThat(stripped.build()).isEqualTo(withoutSource);
  }

  @Test
  public void mergeOverlayables_sameOverlayables() throws Exception {
    ResourceTable table =
        resourceTable(
                pkg(
                    0x7f,
                    "com.app",
                    type(
                        0x01,
                        "xml",
                        entry(0x00, "layout1", ConfigValue.getDefaultInstance())
                            .toBuilder()
                            .setOverlayableItem(newOverlayableItem(/* idx= */ 0))
                            .build(),
                        entry(0x01, "layout2", ConfigValue.getDefaultInstance())
                            .toBuilder()
                            .setOverlayableItem(newOverlayableItem(/* idx= */ 1))
                            .build())))
            .toBuilder()
            .addOverlayable(Overlayable.newBuilder().setName("overlayable1").setActor("actor1"))
            .addOverlayable(Overlayable.newBuilder().setName("overlayable2").setActor("actor2"))
            .build();

    ResourceTable mergedTable = new ResourceTableMerger().merge(table, table);

    assertThat(mergedTable).isEqualTo(table);
  }

  @Test
  public void mergeOverlayables_sameNameDifferentActor_throws() throws Exception {
    ResourceTable baseResourceTable =
        resourceTable(
            pkg(
                0x7f,
                "com.app",
                type(
                    0x01,
                    "xml",
                    entry(0x00, "layout1", ConfigValue.getDefaultInstance())
                        .toBuilder()
                        .setOverlayableItem(newOverlayableItem(/* idx= */ 0))
                        .build(),
                    entry(0x01, "layout2", ConfigValue.getDefaultInstance())
                        .toBuilder()
                        .setOverlayableItem(newOverlayableItem(/* idx= */ 1))
                        .build())));

    ResourceTable table1 =
        baseResourceTable
            .toBuilder()
            .addOverlayable(Overlayable.newBuilder().setName("overlayable1").setActor("actor1"))
            .addOverlayable(Overlayable.newBuilder().setName("overlayable2").setActor("actor2"))
            .build();

    ResourceTable table2 =
        baseResourceTable
            .toBuilder()
            .addOverlayable(Overlayable.newBuilder().setName("overlayable1").setActor("foo"))
            .addOverlayable(Overlayable.newBuilder().setName("overlayable2").setActor("bar"))
            .build();

    assertThrows(
        IllegalStateException.class, () -> new ResourceTableMerger().merge(table1, table2));
  }

  @Test
  public void mergeOverlayables_overlayablesReindexed() throws Exception {
    Function<Integer, ResourceTable> buildResourceTable =
        pkgId ->
            resourceTable(
                pkg(
                    pkgId,
                    "com.app",
                    type(
                        0x01,
                        "xml",
                        entry(0x00, "layout1", ConfigValue.getDefaultInstance())
                            .toBuilder()
                            .setOverlayableItem(newOverlayableItem(/* idx= */ 0))
                            .build(),
                        entry(0x01, "layout2", ConfigValue.getDefaultInstance())
                            .toBuilder()
                            .setOverlayableItem(newOverlayableItem(/* idx= */ 1))
                            .build())));

    ResourceTable table1 =
        buildResourceTable
            .apply(0x7f)
            .toBuilder()
            .addOverlayable(Overlayable.newBuilder().setName("c").setActor("actor_c"))
            .addOverlayable(Overlayable.newBuilder().setName("a").setActor("actor_a"))
            .build();

    ResourceTable table2 =
        buildResourceTable
            .apply(0x7e)
            .toBuilder()
            .addOverlayable(Overlayable.newBuilder().setName("d").setActor("actor_d"))
            .addOverlayable(Overlayable.newBuilder().setName("b").setActor("actor_b"))
            .build();

    ResourceTable mergedTable = new ResourceTableMerger().merge(table1, table2);

    assertThat(mergedTable)
        .isEqualTo(
            resourceTable(
                    pkg(
                        0x7e,
                        "com.app",
                        type(
                            0x01,
                            "xml",
                            entry(0x00, "layout1", ConfigValue.getDefaultInstance())
                                .toBuilder()
                                .setOverlayableItem(newOverlayableItem(/* idx= */ 3))
                                .build(),
                            entry(0x01, "layout2", ConfigValue.getDefaultInstance())
                                .toBuilder()
                                .setOverlayableItem(newOverlayableItem(/* idx= */ 1))
                                .build())),
                    pkg(
                        0x7f,
                        "com.app",
                        type(
                            0x01,
                            "xml",
                            entry(0x00, "layout1", ConfigValue.getDefaultInstance())
                                .toBuilder()
                                .setOverlayableItem(newOverlayableItem(/* idx= */ 2))
                                .build(),
                            entry(0x01, "layout2", ConfigValue.getDefaultInstance())
                                .toBuilder()
                                .setOverlayableItem(newOverlayableItem(/* idx= */ 0))
                                .build())))
                .toBuilder()
                .addOverlayable(Overlayable.newBuilder().setName("a").setActor("actor_a"))
                .addOverlayable(Overlayable.newBuilder().setName("b").setActor("actor_b"))
                .addOverlayable(Overlayable.newBuilder().setName("c").setActor("actor_c"))
                .addOverlayable(Overlayable.newBuilder().setName("d").setActor("actor_d"))
                .build());
  }

  private static OverlayableItem newOverlayableItem(int idx) {
    return OverlayableItem.newBuilder().setOverlayableIdx(idx).build();
  }
}

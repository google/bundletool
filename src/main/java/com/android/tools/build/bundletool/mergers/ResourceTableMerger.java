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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Overlayable;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Source;
import com.android.aapt.Resources.Type;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Message;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Recursively merges two resource tables.
 *
 * <p>Many fields are just asserted to be equal, actual merging affects only the following repeated
 * fields:
 *
 * <ul>
 *   <li>{@code ResourceTable.package}
 *   <li>{@code ResourceTable.package[*].type}
 *   <li>{@code ResourceTable.package[*].type[*].entry}
 *   <li>{@code ResourceTable.package[*].type[*].entry[*].config_value}
 * </ul>
 *
 * When merging repeated fields of {@link Package}, {@link Type} or {@link Entry}, protos in the two
 * fields are paired based on an ID field. For two paired protos, their non-repeated fields are
 * asserted to be equal (eg. ResourceTable.source_pool) and selected repeated fields are merged
 * recursively by the same-ID principle. Two repeated fields of {@link ConfigValue}s are merged just
 * based on {@link Object#equals(Object)} in the sense that if the config values are equal, the
 * merged resource table contains the config value only once (no duplicates).
 *
 * <p>Because {@code source_pool} messages cannot be easily merged yet, the implementation chooses
 * to preserve source pool of the first input table, and strip all source references in the second
 * input table unless the tables have identical source pools.
 *
 * <p>Implementation note: All of the "merge*" methods use pattern {@code .toBuilder() -> <modify>
 * -> .build()}. This way we preserve the property of whether a field is set or not set for the
 * fields that are asserted to be equal but otherwise untouched.
 */
public class ResourceTableMerger {

  public ResourceTable merge(ResourceTable table1, ResourceTable table2) {
    if (!table1.getSourcePool().equals(table2.getSourcePool())) {
      // The source_pool in ResourceTable is opaque and cannot be easily manipulated. Therefore
      // if the source pools aren't exactly the same, we choose to adopt source pool of table1 and
      // to invalidate source references in table2.
      ResourceTable.Builder table2Builder = table2.toBuilder();
      stripSourceReferences(table2Builder);
      table2 = table2Builder.build();
    }

    // Merge the overlayables using the name as an id
    ImmutableList<Overlayable> mergedOverlayables =
        mergeRepeatedValues(
            table1.getOverlayableList(),
            table2.getOverlayableList(),
            Overlayable::getName,
            this::mergeOverlayables);

    if (!mergedOverlayables.isEmpty()) {
      // The overlayables are referenced by index in the resource entries, so now that the list has
      // changed, we need to update the indices.
      ImmutableMap<Integer, String> idxToOverlayableName =
          toIndexMap(mergedOverlayables, Overlayable::getName);
      ImmutableMap<String, Integer> idxByOverlayableName =
          ImmutableBiMap.copyOf(idxToOverlayableName).inverse();
      table1 = reIndexOverlayables(table1, idxByOverlayableName);
      table2 = reIndexOverlayables(table2, idxByOverlayableName);
    }

    return table1
        .toBuilder()
        .clearOverlayable()
        .addAllOverlayable(mergedOverlayables)
        .clearPackage()
        .addAllPackage(
            mergeRepeatedValues(
                table1.getPackageList(),
                table2.getPackageList(),
                pkg -> pkg.getPackageId().getId(),
                this::mergePackages))
        .build();
  }

  @CheckReturnValue
  private static ResourceTable reIndexOverlayables(
      ResourceTable table, ImmutableMap<String, Integer> newOverlayableIdxMap) {
    ImmutableMap<Integer, Integer> oldToNewIndex =
        toIndexMap(
            ImmutableList.copyOf(table.getOverlayableList()),
            overlayable -> newOverlayableIdxMap.get(overlayable.getName()));

    ResourceTable.Builder newTable = table.toBuilder();
    for (Package.Builder pkg : newTable.getPackageBuilderList()) {
      for (Type.Builder type : pkg.getTypeBuilderList()) {
        for (Entry.Builder entry : type.getEntryBuilderList()) {
          if (entry.hasOverlayableItem()) {
            int newIdx = oldToNewIndex.get(entry.getOverlayableItem().getOverlayableIdx());
            entry.getOverlayableItemBuilder().setOverlayableIdx(newIdx);
          }
        }
      }
    }
    return newTable.build();
  }

  private static <T, R> ImmutableMap<Integer, R> toIndexMap(
      ImmutableList<T> list, Function<T, R> valueFn) {
    Map<Integer, T> map =
        Streams.mapWithIndex(
                list.stream(),
                (value, i) -> new AbstractMap.SimpleEntry<>(Ints.checkedCast(i), value))
            .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    return ImmutableMap.copyOf(Maps.transformValues(map, valueFn::apply));
  }

  private Overlayable mergeOverlayables(Overlayable overlayable1, Overlayable overlayable2) {
    assertEqualFields(overlayable1, overlayable2, Overlayable::getName, /* fieldName= */ "name");
    assertEqualFields(overlayable1, overlayable2, Overlayable::getActor, /* fieldName= */ "actor");
    return overlayable1;
  }

  private Package mergePackages(Package pkg1, Package pkg2) {
    assertEqualFields(pkg1, pkg2, Package::getPackageId, /* fieldName= */ "package_id");
    assertEqualFields(pkg1, pkg2, Package::getPackageName, /* fieldName= */ "package_name");

    return pkg1.toBuilder()
        .clearType()
        .addAllType(
            mergeRepeatedValues(
                pkg1.getTypeList(),
                pkg2.getTypeList(),
                type -> type.getTypeId().getId(),
                this::mergeTypes))
        .build();
  }

  private Type mergeTypes(Type type1, Type type2) {
    assertEqualFields(type1, type2, Type::getTypeId, /* fieldName= */ "type_id");
    assertEqualFields(type1, type2, Type::getName, /* fieldName= */ "name");

    return type1
        .toBuilder()
        .clearEntry()
        .addAllEntry(
            mergeRepeatedValues(
                type1.getEntryList(),
                type2.getEntryList(),
                entry -> entry.getEntryId().getId(),
                this::mergeEntries))
        .build();
  }

  private Entry mergeEntries(Entry entry1, Entry entry2) {
    assertEqualFields(entry1, entry2, Entry::getEntryId, /* fieldName= */ "entry_id");
    assertEqualFields(entry1, entry2, Entry::getName, /* fieldName= */ "name");
    assertEqualFields(entry1, entry2, Entry::getVisibility, /* fieldName= */ "visibility");
    assertEqualFields(entry1, entry2, Entry::getAllowNew, /* fieldName= */ "allow_new");
    assertEqualFields(
        entry1, entry2, Entry::getOverlayableItem, /* fieldName= */ "overlayable_item");

    return entry1
        .toBuilder()
        .clearConfigValue()
        .addAllConfigValue(
            mergeConfigValueLists(entry1.getConfigValueList(), entry2.getConfigValueList()))
        .build();
  }

  private List<ConfigValue> mergeConfigValueLists(
      List<ConfigValue> configValues1, List<ConfigValue> configValues2) {
    HashSet<ConfigValue> configValues1Set = Sets.newHashSet(configValues1);
    return ImmutableList.<ConfigValue>builder()
        .addAll(configValues1)
        .addAll(
            configValues2.stream()
                .filter(not(configValues1Set::contains))
                .collect(toImmutableList()))
        .build();
  }

  /**
   * Merges values of two 'repeated' proto fields.
   *
   * <p>Each value within a value list has an ID, computed by applying {@code getIdFn}. Values of
   * both lists are paired by matching IDs. Paired values are merged by applying {@code
   * mergeValuesFn}. Unpaired values are inserted to the result unmodified.
   */
  private <V, I extends Comparable<?>> ImmutableList<V> mergeRepeatedValues(
      List<V> values1, List<V> values2, Function<V, I> getIdFn, BiFunction<V, V, V> mergeValuesFn) {

    ImmutableList.Builder<V> result = ImmutableList.builder();

    Map<I, V> idToValue1 = Maps.uniqueIndex(values1, getIdFn::apply);
    Map<I, V> idToValue2 = Maps.uniqueIndex(values2, getIdFn::apply);

    // Order the IDs (for better debugging of merged resource tables).
    ImmutableList<I> allIds =
        Sets.union(idToValue1.keySet(), idToValue2.keySet()).stream()
            .sorted()
            .collect(toImmutableList());

    for (I id : allIds) {
      V value1 = idToValue1.get(id);
      V value2 = idToValue2.get(id);

      if (value1 != null && value2 != null) {
        result.add(mergeValuesFn.apply(value1, value2));
      } else if (value1 != null) {
        result.add(value1);
      } else {
        result.add(value2);
      }
    }

    return result.build();
  }

  private static <V, F> void assertEqualFields(
      V protoMsg1, V protoMsg2, Function<V, F> getFieldFn, String fieldName) {
    F field1 = getFieldFn.apply(protoMsg1);
    F field2 = getFieldFn.apply(protoMsg2);
    checkState(
        field1.equals(field2),
        "Expected same values of field '%s', found [%s] and [%s].",
        fieldName,
        field1,
        field2);
  }

  /** Recursively crawls the proto message while clearing each field of type {@link Source}. */
  @VisibleForTesting
  static void stripSourceReferences(Message.Builder msg) {
    for (FieldDescriptor fieldDesc : msg.getAllFields().keySet()) {
      if (!fieldDesc.getJavaType().equals(JavaType.MESSAGE)) {
        continue;
      }

      if (fieldDesc.getMessageType().getFullName().equals(Source.getDescriptor().getFullName())) {
        msg.clearField(fieldDesc);
      } else {
        if (fieldDesc.isRepeated()) {
          int repeatCount = msg.getRepeatedFieldCount(fieldDesc);
          for (int i = 0; i < repeatCount; i++) {
            stripSourceReferences(msg.getRepeatedFieldBuilder(fieldDesc, i));
          }
        } else {
          stripSourceReferences(msg.getFieldBuilder(fieldDesc));
        }
      }
    }
  }
}

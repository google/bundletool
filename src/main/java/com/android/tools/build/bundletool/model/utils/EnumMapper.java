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
package com.android.tools.build.bundletool.model.utils;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableSortedMap.toImmutableSortedMap;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import java.util.EnumSet;

/** Utility to map two different enums. */
public final class EnumMapper {

  /**
   * Builds a correspondence map between {@code enum1Type} and {@code enum2Type} based on the name
   * of the enum values.
   *
   * <p>If the values of {@code enum1Type} are not same as {@code enum2Type}, an {@link
   * IllegalArgumentException} is thrown.
   */
  public static <E1 extends Enum<E1>, E2 extends Enum<E2>> ImmutableBiMap<E1, E2> mapByName(
      Class<E1> enum1Type, Class<E2> enum2Type) {
    return mapByName(enum1Type, enum2Type, ImmutableSet.of());
  }

  /**
   * Same as {@link #mapByName(Class, Class)} but allows to ignore some values from {@code
   * enum1Type}.
   */
  public static <E1 extends Enum<E1>, E2 extends Enum<E2>> ImmutableBiMap<E1, E2> mapByName(
      Class<E1> enum1Type, Class<E2> enum2Type, ImmutableSet<E1> ignoreValues) {
    ImmutableBiMap.Builder<E1, E2> map = ImmutableBiMap.builder();

    ImmutableMap<String, E1> enum1ValuesByName = enumValuesByName(enum1Type, ignoreValues);
    ImmutableMap<String, E2> enum2ValuesByName = enumValuesByName(enum2Type, ImmutableSet.of());

    if (!enum1ValuesByName.keySet().equals(enum2ValuesByName.keySet())) {
      throw new IllegalArgumentException(
          String.format(
              "Enum %s does not have the same values as enum %s: %s vs %s",
              enum1Type.getCanonicalName(),
              enum2Type.getCanonicalName(),
              enum1ValuesByName.keySet(),
              enum2ValuesByName.keySet()));
    }

    for (String enumName : enum1ValuesByName.keySet()) {
      map.put(enum1ValuesByName.get(enumName), enum2ValuesByName.get(enumName));
    }
    return map.build();
  }

  private static <E extends Enum<E>> ImmutableSortedMap<String, E> enumValuesByName(
      Class<E> enumType, ImmutableSet<E> ignoreValues) {
    return EnumSet.allOf(enumType)
        .stream()
        .filter(not(ignoreValues::contains))
        .sorted()
        .collect(toImmutableSortedMap(Ordering.natural(), Enum::name, identity()));
  }

  private EnumMapper() {}
}

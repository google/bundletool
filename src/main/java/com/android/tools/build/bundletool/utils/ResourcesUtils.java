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

package com.android.tools.build.bundletool.utils;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.toOptional;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Type;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Helpers related to APK resources qualifiers. */
public final class ResourcesUtils {

  public static final ImmutableBiMap<String, DensityAlias> SCREEN_DENSITY_TO_PROTO_VALUE_MAP =
      ImmutableBiMap.<String, DensityAlias>builder()
          .put("nodpi", DensityAlias.NODPI)
          .put("default", DensityAlias.DENSITY_UNSPECIFIED)
          .put("ldpi", DensityAlias.LDPI)
          .put("mdpi", DensityAlias.MDPI)
          .put("tvdpi", DensityAlias.TVDPI)
          .put("hdpi", DensityAlias.HDPI)
          .put("xhdpi", DensityAlias.XHDPI)
          .put("xxhdpi", DensityAlias.XXHDPI)
          .put("xxxhdpi", DensityAlias.XXXHDPI)
          .build();

  // Based on android/configuration.h.
  public static final int DEFAULT_DENSITY_VALUE = 0;
  public static final int LDPI_VALUE = 120;
  public static final int MDPI_VALUE = 160;
  public static final int TVDPI_VALUE = 213;
  public static final int HDPI_VALUE = 240;
  public static final int XHDPI_VALUE = 320;
  public static final int XXHDPI_VALUE = 480;
  public static final int XXXHDPI_VALUE = 640;
  public static final int ANY_DENSITY_VALUE = 0xfffe;
  public static final int NONE_DENSITY_VALUE = 0xffff;

  // Type names used in aapt2 (Resource.cpp).
  public static final String MIPMAP_TYPE = "mipmap";

  public static final ImmutableMap<DensityAlias, Integer> DENSITY_ALIAS_TO_DPI_MAP =
      ImmutableMap.<DensityAlias, Integer>builder()
          .put(DensityAlias.NODPI, NONE_DENSITY_VALUE)
          .put(DensityAlias.DENSITY_UNSPECIFIED, DEFAULT_DENSITY_VALUE)
          .put(DensityAlias.LDPI, LDPI_VALUE)
          .put(DensityAlias.MDPI, MDPI_VALUE)
          .put(DensityAlias.TVDPI, TVDPI_VALUE)
          .put(DensityAlias.HDPI, HDPI_VALUE)
          .put(DensityAlias.XHDPI, XHDPI_VALUE)
          .put(DensityAlias.XXHDPI, XXHDPI_VALUE)
          .put(DensityAlias.XXXHDPI, XXXHDPI_VALUE)
          .build();

  /**
   * Filters the given resource table according to the specified criteria.
   *
   * <p>If any of {@link Package}, {@link Type} or {@link Entry} is empty after the filtering, it
   * gets removed from the table altogether.
   *
   * @param originalTable the original resource table
   * @param removeTypePredicate determines whether a type should be completely removed, regardless
   *     of its contents
   * @param configValuesFilterFn computes a new {@link Entry} with filtered {@link ConfigValue} list
   *     (possibly empty)
   * @return filtered resource table
   */
  public static ResourceTable filterResourceTable(
      ResourceTable originalTable,
      Predicate<Type> removeTypePredicate,
      Function<Entry, Entry> configValuesFilterFn) {

    ResourceTable.Builder filteredTable = originalTable.toBuilder();

    for (int pkgIdx = filteredTable.getPackageCount() - 1; pkgIdx >= 0; pkgIdx--) {
      Package.Builder pkg = filteredTable.getPackageBuilder(pkgIdx);

      for (int typeIdx = pkg.getTypeCount() - 1; typeIdx >= 0; typeIdx--) {
        if (removeTypePredicate.test(pkg.getType(typeIdx))) {
          pkg.removeType(typeIdx);
          continue;
        }
        Type.Builder type = pkg.getTypeBuilder(typeIdx);

        for (int entryIdx = type.getEntryCount() - 1; entryIdx >= 0; entryIdx--) {
          Entry entry = type.getEntry(entryIdx);
          Entry filteredEntry = configValuesFilterFn.apply(entry);
          if (filteredEntry.getConfigValueCount() > 0) {
            type.setEntry(entryIdx, filteredEntry);
          } else {
            type.removeEntry(entryIdx);
          }
        } // entries

        if (type.getEntryCount() == 0) {
          pkg.removeType(typeIdx);
        }
      } // types

      if (pkg.getTypeCount() == 0) {
        filteredTable.removePackage(pkgIdx);
      }
    } // packages

    return filteredTable.build();
  }

  public static Set<Integer> resourceIds(
      ResourceTable table, Function<Type, Boolean> typeFilterFn) {
    Set<Integer> resourceIds = new HashSet<>();
    for (Package pkg : table.getPackageList()) {
      for (Type type : pkg.getTypeList()) {
        if (typeFilterFn.apply(type)) {
          for (Entry entry : type.getEntryList()) {
            resourceIds.add(
                makeResourceIdentifier(
                    pkg.getPackageId().getId(),
                    type.getTypeId().getId(),
                    entry.getEntryId().getId()));
          }
        }
      }
    }
    return resourceIds;
  }

  public static int makeResourceIdentifier(
      int packageIdentifier, int typeIdentifier, int entryIdentifier) {
    return packageIdentifier << 24 | typeIdentifier << 16 | entryIdentifier;
  }

  public static Stream<Entry> entries(ResourceTable resourceTable) {
    return resourceTable
        .getPackageList()
        .stream()
        .map(Package::getTypeList)
        .flatMap(Collection::stream)
        .map(Type::getEntryList)
        .flatMap(Collection::stream);
  }

  public static Stream<ConfigValue> configValues(ResourceTable resourceTable) {
    return entries(resourceTable).map(Entry::getConfigValueList).flatMap(Collection::stream);
  }

  public static ImmutableSet<ZipPath> getAllFileReferences(ResourceTable resourceTable) {
    return configValues(resourceTable)
        .filter(configValue -> configValue.getValue().getItem().hasFile())
        .map(configValue -> ZipPath.create(configValue.getValue().getItem().getFile().getPath()))
        .collect(toImmutableSet());
  }

  public static Integer convertToDpi(ScreenDensity screenDensity) {
    switch (screenDensity.getDensityOneofCase()) {
      case DENSITY_ALIAS:
        return DENSITY_ALIAS_TO_DPI_MAP.get(screenDensity.getDensityAlias());
      case DENSITY_DPI:
        return screenDensity.getDensityDpi();
      case DENSITYONEOF_NOT_SET:
        throw new IllegalArgumentException("ScreenDensity proto is not set properly.");
      default:
        throw new IllegalArgumentException("ScreenDensity value is not recognized.");
    }
  }

  /**
   * Converts the given BCP-47 compliant locale string to the language portion of it.
   *
   * <p>Even though BCP 47 is more permissive, we assume that the language portion is given by two
   * character country code (ISO-639-1) and the BCP-47 string contains optionally the region code
   * and nothing else. This is mandated by the resource naming in Android.
   *
   * @param locale, a BCP 47 compliant string and valid in the context of Android resources.
   * @return a two character language code conforming to ISO-639-1.
   */
  public static String convertLocaleToLanguage(String locale) {
    return Locale.forLanguageTag(locale).getLanguage();
  }

  public static Optional<Entry> lookupEntryByResourceId(
      ResourceTable resourceTable, int resourceId) {
    int packageId = (resourceId >> 24) & 0xFF;
    int typeId = (resourceId >> 16) & 0xFF;
    int entryId = (resourceId >> 0) & 0xFFFF;
    return resourceTable
        .getPackageList()
        .stream()
        .filter(pkg -> pkg.getPackageId().getId() == packageId)
        .flatMap(pkg -> pkg.getTypeList().stream())
        .filter(type -> type.getTypeId().getId() == typeId)
        .flatMap(type -> type.getEntryList().stream())
        .filter(entry -> entry.getEntryId().getId() == entryId)
        .collect(toOptional());
  }

  /** Returns all languages present in given resource table. */
  public static ImmutableSet<String> getAllLanguages(ResourceTable table) {
    return configValues(table)
        .map(configValue -> configValue.getConfig().getLocale())
        .map(ResourcesUtils::convertLocaleToLanguage)
        .collect(toImmutableSet());
  }

  // Not meant to be instantiated.
  private ResourcesUtils() {}
}

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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.MoreCollectors.toOptional;
import static java.util.Comparator.comparing;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Type;
import com.android.bundle.Targeting.ScreenDensity;
import com.android.bundle.Targeting.ScreenDensity.DensityAlias;
import com.android.tools.build.bundletool.model.ResourceTableEntry;
import com.android.tools.build.bundletool.model.ZipPath;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Helpers related to APK resources qualifiers. */
public final class ResourcesUtils {

  /** Package IDs of system shared libraries and android resources. */
  private static final ImmutableSet<Integer> ANDROID_PACKAGE_IDS = ImmutableSet.of(0x00, 0x01);

  private static final LoadingCache<String, String> localeToLanguageCache =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<String, String>() {
                @Override
                public String load(String locale) {
                  return Locale.forLanguageTag(locale).getLanguage();
                }
              });

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
   * @param removeEntryPredicate determines whether an entry should be completely removed with all
   *     their configurations
   * @param configValuesFilterFn computes a new {@link Entry} with filtered {@link ConfigValue} list
   *     (possibly empty)
   * @return filtered resource table
   */
  public static ResourceTable filterResourceTable(
      ResourceTable originalTable,
      Predicate<ResourceTableEntry> removeEntryPredicate,
      Function<ResourceTableEntry, Entry> configValuesFilterFn) {

    ResourceTable.Builder filteredTable = originalTable.toBuilder();

    for (int pkgIdx = filteredTable.getPackageCount() - 1; pkgIdx >= 0; pkgIdx--) {
      Package.Builder pkg = filteredTable.getPackageBuilder(pkgIdx);

      for (int typeIdx = pkg.getTypeCount() - 1; typeIdx >= 0; typeIdx--) {
        Type.Builder type = pkg.getTypeBuilder(typeIdx);

        List<Entry> unfilteredEntries = type.getEntryList();
        type.clearEntry();

        for (Entry unfilteredEntry : unfilteredEntries) {
          ResourceTableEntry entry =
              ResourceTableEntry.create(
                  filteredTable.getPackage(pkgIdx), pkg.getType(typeIdx), unfilteredEntry);
          if (removeEntryPredicate.test(entry)) {
            continue;
          }
          Entry filteredEntry = configValuesFilterFn.apply(entry);
          if (filteredEntry.getConfigValueCount() > 0) {
            type.addEntry(filteredEntry);
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

  public static Stream<ResourceTableEntry> entries(ResourceTable resourceTable) {
    Stream.Builder<ResourceTableEntry> stream = Stream.builder();
    for (Package pkg : resourceTable.getPackageList()) {
      for (Type type : pkg.getTypeList()) {
        for (Entry entry : type.getEntryList()) {
          stream.add(ResourceTableEntry.create(pkg, type, entry));
        }
      }
    }
    return stream.build();
  }

  public static Stream<ConfigValue> configValues(ResourceTable resourceTable) {
    return entries(resourceTable)
        .map(ResourceTableEntry::getEntry)
        .map(Entry::getConfigValueList)
        .flatMap(Collection::stream);
  }

  public static ImmutableSet<ZipPath> getAllFileReferences(ResourceTable resourceTable) {
    return getAllFileReferencesInternal(resourceTable)
        .map(fileReference -> ZipPath.create(fileReference.getPath()))
        .collect(toImmutableSet());
  }

  public static ImmutableSet<ZipPath> getAllProtoXmlFileReferences(ResourceTable resourceTable) {
    return getAllFileReferencesInternal(resourceTable)
        .filter(fileReference -> fileReference.getType().equals(FileReference.Type.PROTO_XML))
        .map(fileReference -> ZipPath.create(fileReference.getPath()))
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
    return localeToLanguageCache.getUnchecked(locale);
  }

  public static Optional<Entry> lookupEntryByResourceId(
      ResourceTable resourceTable, int resourceId) {
    return entries(resourceTable)
        .filter(entry -> entry.getResourceId().getFullResourceId() == resourceId)
        .map(ResourceTableEntry::getEntry)
        .collect(toOptional());
  }

  public static Optional<Entry> lookupEntryByResourceTypeAndName(
      ResourceTable resourceTable, String resourceType, String resourceName) {
    return entries(resourceTable)
        .filter(
            entry ->
                entry.getType().getName().equals(resourceType)
                    && entry.getEntry().getName().equals(resourceName))
        .map(ResourceTableEntry::getEntry)
        .collect(toOptional());
  }

  /** Returns all languages present in given resource table. */
  public static ImmutableSet<String> getAllLanguages(ResourceTable table) {
    return configValues(table)
        .map(configValue -> configValue.getConfig().getLocale())
        .distinct()
        .map(ResourcesUtils::convertLocaleToLanguage)
        .collect(toImmutableSet());
  }

  /** Returns all locales present in a given resource table. */
  public static ImmutableSet<String> getAllLocales(ResourceTable table) {
    return configValues(table)
        .map(configValue -> configValue.getConfig().getLocale())
        .collect(toImmutableSet());
  }

  /** Returns the smallest screen density from the ones given. */
  public static DensityAlias getLowestDensity(ImmutableCollection<DensityAlias> densities) {
    return densities.stream().min(comparing(DENSITY_ALIAS_TO_DPI_MAP::get)).get();
  }

  /**
   * Returns new resource ID which is obtained from setting package ID part of {@code resourceId} to
   * {@code newPackageId}.
   *
   * <p>First 8 bits of {@code resourceId} represent the package ID.
   */
  public static int remapPackageIdInResourceId(int resourceId, int newPackageId) {
    return (newPackageId << 24) | (resourceId & 0xffffff);
  }

  /** Returns `true` if the given resource ID belongs to an Android Framework resource. */
  public static boolean isAndroidResourceId(int resourceId) {
    return ANDROID_PACKAGE_IDS.contains(getPackageId(resourceId));
  }
  /** Extracts package ID from the given {@code resourceId} (first 8 bits). */
  private static int getPackageId(int resourceId) {
    return resourceId >> 24;
  }

  private static Stream<FileReference> getAllFileReferencesInternal(ResourceTable resourceTable) {
    return configValues(resourceTable)
        .filter(configValue -> configValue.getValue().getItem().hasFile())
        .map(configValue -> configValue.getValue().getItem().getFile());
  }

  // Not meant to be instantiated.
  private ResourcesUtils() {}
}

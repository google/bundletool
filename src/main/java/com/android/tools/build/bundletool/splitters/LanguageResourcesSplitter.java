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

package com.android.tools.build.bundletool.splitters;

import static com.android.tools.build.bundletool.model.BundleModule.RESOURCES_DIRECTORY;
import static com.android.tools.build.bundletool.utils.ResourcesUtils.convertLocaleToLanguage;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.ResourceTable;
import com.android.bundle.Targeting.LanguageTargeting;
import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.android.tools.build.bundletool.utils.ResourcesUtils;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Splits the module resources by languages.
 *
 * <p>The languages are detected from the configurations in the resource table.
 *
 * <p>This splitter may generate one split that doesn't target any language if it is non-empty. It
 * contains all non-resource entries if they were given to the splitter and any non-language
 * specific resources.
 *
 * <p>The regions are grouped together in the language split containing them.
 *
 * <p>The implementation assumes the canonical format of the languages in the Resource Table, i.e.
 * without ambiguities using ISO-639-1 two character variant. For example: Tagalog and Filipino
 * languages are using the same value 'tl'.
 */
public class LanguageResourcesSplitter extends SplitterForOneTargetingDimension {

  @Override
  public ImmutableCollection<ModuleSplit> splitInternal(ModuleSplit split) {
    if (!split.getResourceTable().isPresent()) {
      return ImmutableList.of(split);
    }

    ResourceTable resourceTable = split.getResourceTable().get();
    ImmutableMap<String, ResourceTable> byLanguage =
        groupByLanguage(resourceTable, hasNonResourceEntries(split));

    ImmutableList.Builder<ModuleSplit> result = new ImmutableList.Builder<>();

    for (String language : byLanguage.keySet()) {
      ModuleSplit moduleSplit =
          split
              .toBuilder()
              .setEntries(
                  getEntriesForSplit(split.getEntries(), language, byLanguage.get(language)))
              .setResourceTable(byLanguage.get(language))
              .setApkTargeting(
                  // Grouping by language may produce a special value "" for non-language specific
                  // resources. This won't end up in any language split hence we exclude it for the
                  // generation of language split targeting.
                  language.isEmpty()
                      ? split.getApkTargeting()
                      : split
                          .getApkTargeting()
                          .toBuilder()
                          .setLanguageTargeting(
                              // Don't set alternatives for language targeting.
                              // Language alternatives are not being used for APK selection, and
                              // would prevent merging of splits with identical language across
                              // resources and assets.
                              LanguageTargeting.newBuilder().addValue(language))
                          .build())
              .setMasterSplit(split.isMasterSplit() && language.isEmpty())
              .build();
      result.add(moduleSplit);
    }
    return result.build();
  }

  private static boolean hasNonResourceEntries(ModuleSplit split) {
    return split
        .getEntries()
        .stream()
        .anyMatch(entry -> !entry.getPath().startsWith(RESOURCES_DIRECTORY));
  }

  private static ImmutableList<ModuleEntry> getEntriesForSplit(
      ImmutableList<ModuleEntry> inputEntries, String language, ResourceTable resourceTable) {
    ImmutableList<ModuleEntry> entriesFromResourceTable =
        ModuleSplit.filterResourceEntries(inputEntries, resourceTable);
    if (language.isEmpty()) { // The split with no specific language targeting.
      ImmutableList.Builder<ModuleEntry> result = new Builder<>();
      result.addAll(entriesFromResourceTable);
      // Add non-resource entries.
      result.addAll(
          inputEntries
              .stream()
              .filter(entry -> !entry.getPath().startsWith(RESOURCES_DIRECTORY))
              .collect(toImmutableList()));
      return result.build();
    } else {
      return entriesFromResourceTable;
    }
  }

  private static ImmutableMap<String, ResourceTable> groupByLanguage(
      ResourceTable table, boolean hasNonResourceEntries) {
    ImmutableSet<String> languages = ResourcesUtils.getAllLanguages(table);

    ImmutableMap.Builder<String, ResourceTable> resourceTableByLanguage =
        new ImmutableMap.Builder<>();
    for (String language : languages) {
      resourceTableByLanguage.put(language, filterByLanguage(table, language));
    }

    // If there are no resources with the default language (rare and not recommended) create an
    // empty resource table.
    // This semantic is desired here because we need a default language split to contain all non
    // resource related entries.
    if (!languages.contains("") && hasNonResourceEntries) {
      resourceTableByLanguage.put("", ResourceTable.getDefaultInstance());
    }

    return resourceTableByLanguage.build();
  }

  private static ResourceTable filterByLanguage(ResourceTable input, String language) {
    return ResourcesUtils.filterResourceTable(
        input,
        /* removeTypePredicate= */ Predicates.alwaysFalse(),
        /* configValuesFilterFn= */ entry -> filterEntryForLanguage(entry, language));
  }

  /**
   * Only leaves the language specific config values relevant for the given language.
   *
   * @param initialEntry the entry to be updated
   * @param targetLanguage the desired language to match
   * @return the entry containing only config values specific to the given language
   */
  private static Entry filterEntryForLanguage(Entry initialEntry, String targetLanguage) {
    Iterable<ConfigValue> filteredConfigValues =
        Iterables.filter(
            initialEntry.getConfigValueList(),
            configValue ->
                convertLocaleToLanguage(configValue.getConfig().getLocale())
                    .equals(targetLanguage));
    return initialEntry
        .toBuilder()
        .clearConfigValue()
        .addAllConfigValue(filteredConfigValues)
        .build();
  }
}

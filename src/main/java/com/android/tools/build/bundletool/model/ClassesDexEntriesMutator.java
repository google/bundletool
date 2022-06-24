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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.Math.max;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.partitioningBy;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mutator of the dex entries of a module. */
public class ClassesDexEntriesMutator {

  private static final Pattern CLASSES_DEX_REGEX_PATTERN =
      Pattern.compile("dex/classes(\\d*)\\.dex");

  private static final Predicate<ModuleEntry> IS_DEX_FILE =
      entry -> entry.getPath().toString().matches(CLASSES_DEX_REGEX_PATTERN.pattern());

  /**
   * Mutator of the name of dex files to workaround a Gradle plugin bug that creates a bundle with a
   * dex file named "classes1.dex" instead of "classes2.dex".
   *
   * <p>This will be removed as soon as Gradle plugin bug is fixed.
   */
  public static final Function<ImmutableList<ModuleEntry>, ImmutableList<ModuleEntry>>
      CLASSES_DEX_NAME_SANITIZER =
          (dexEntries) ->
              dexEntries.stream()
                  .map(ClassesDexEntriesMutator::sanitizeDexEntryName)
                  .collect(toImmutableList());

  /**
   * Mutator that removes dex file with the highest index from the list of entries. This is used
   * when converting Android SDK Bundle to Android App Bundle module, where the last dex file of ASB
   * should not be included. This is because it contains the RPackage class for the SDK, which
   * should instead be inherited from the app's base module.
   */
  public static final Function<ImmutableList<ModuleEntry>, ImmutableList<ModuleEntry>>
      R_PACKAGE_DEX_ENTRY_REMOVER =
          (dexEntries) ->
              dexEntries.stream()
                  .sorted(
                      Comparator.comparing(
                          dexEntry -> getClassesIndexForDexPath(dexEntry.getPath())))
                  .limit(max(dexEntries.size() - 1, 0))
                  .collect(toImmutableList());

  @CheckReturnValue
  public BundleModule applyMutation(
      BundleModule module,
      Function<ImmutableList<ModuleEntry>, ImmutableList<ModuleEntry>> mutator) {
    if (!shouldApplyMutation(module, mutator)) {
      return module;
    }

    // Separate the dex entries from the other entries
    Map<Boolean, ImmutableList<ModuleEntry>> partitionedEntries =
        module.getEntries().stream().collect(partitioningBy(IS_DEX_FILE, toImmutableList()));
    ImmutableList<ModuleEntry> dexEntries = partitionedEntries.get(true);
    ImmutableList<ModuleEntry> nonDexEntries = partitionedEntries.get(false);

    // Build the new list of entries by keeping all non-dex entries unchanged and mutating the dex
    // entries.
    ImmutableList<ModuleEntry> newEntries =
        ImmutableList.<ModuleEntry>builder()
            .addAll(nonDexEntries)
            .addAll(mutator.apply(dexEntries))
            .build();

    return module.toBuilder()
        .setEntryMap(newEntries.stream().collect(toImmutableMap(ModuleEntry::getPath, identity())))
        .build();
  }

  private static ModuleEntry sanitizeDexEntryName(ModuleEntry dexEntry) {
    ZipPath sanitizedEntryPath = incrementClassesDexNumber(dexEntry.getPath());
    return dexEntry.toBuilder().setPath(sanitizedEntryPath).build();
  }

  /**
   * Increment the suffix of multidex files.
   *
   * <pre>
   * dex/classes.dex -- dex/classes.dex
   * dex/classes1.dex -- dex/classes2.dex
   * dex/classes2.dex -- dex/classes3.dex
   * </pre>
   */
  private static ZipPath incrementClassesDexNumber(ZipPath entryPath) {
    int num = getClassesIndexForDexPath(entryPath);
    if (num == 0) {
      return entryPath; // dex/classes.dex
    }
    return ZipPath.create("dex/classes" + (num + 1) + ".dex");
  }

  private static int getClassesIndexForDexPath(ZipPath entryPath) {
    String fileName = entryPath.toString();

    Matcher matcher = CLASSES_DEX_REGEX_PATTERN.matcher(fileName);
    checkState(matcher.matches());

    String num = matcher.group(1);
    if (num.isEmpty()) {
      return 0; // dex/classes.dex
    }
    return Integer.parseInt(num);
  }

  private static boolean shouldApplyMutation(
      BundleModule module,
      Function<ImmutableList<ModuleEntry>, ImmutableList<ModuleEntry>> mutator) {
    if (mutator.equals(CLASSES_DEX_NAME_SANITIZER)) {
      return module.getEntry(ZipPath.create("dex/classes1.dex")).isPresent();
    }
    return true;
  }
}

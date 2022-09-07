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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mutator of the name of dex files to workaround a Gradle plugin bug that creates a bundle with a
 * dex file named "classes1.dex" instead of "classes2.dex".
 *
 * <p>This will be removed as soon as Gradle plugin bug is fixed.
 */
public class ClassesDexSanitizer extends ModuleEntriesMutator {

  private static final Pattern CLASSES_DEX_REGEX_PATTERN =
      Pattern.compile("dex/classes(\\d*)\\.dex");

  @Override
  public Predicate<ModuleEntry> getFilter() {
    return entry -> entry.getPath().toString().matches(CLASSES_DEX_REGEX_PATTERN.pattern());
  }

  @Override
  public Function<ImmutableList<ModuleEntry>, ImmutableList<ModuleEntry>> getMutator() {
    return (dexEntries) ->
        dexEntries.stream()
            .map(ClassesDexSanitizer::sanitizeDexEntryName)
            .collect(toImmutableList());
  }

  @Override
  public boolean shouldApplyMutation(BundleModule module) {
    return module.getEntry(ZipPath.create("dex/classes1.dex")).isPresent();
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
}

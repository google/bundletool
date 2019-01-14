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
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.partitioningBy;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckReturnValue;

/**
 * Sanitizer of the name of dex files to workaround a Gradle plugin bug that creates a bundle with a
 * dex file named "classes1.dex" instead of "classes2.dex".
 *
 * <p>This will be removed as soon as Gradle plugin bug is fixed.
 */
public class ClassesDexNameSanitizer {

  private static final Pattern CLASSES_DEX_REGEX_PATTERN =
      Pattern.compile("dex/classes(\\d*)\\.dex");

  private static final Predicate<ModuleEntry> IS_DEX_FILE =
      entry -> entry.getPath().toString().matches(CLASSES_DEX_REGEX_PATTERN.pattern());

  @CheckReturnValue
  public BundleModule sanitize(BundleModule module) {
    if (!module.getEntry(ZipPath.create("dex/classes1.dex")).isPresent()) {
      return module;
    }

    // Separate the dex entries from the other entries
    Map<Boolean, ImmutableList<ModuleEntry>> partitionedEntries =
        module.getEntries().stream().collect(partitioningBy(IS_DEX_FILE, toImmutableList()));
    ImmutableList<ModuleEntry> dexEntries = partitionedEntries.get(true);
    ImmutableList<ModuleEntry> nonDexEntries = partitionedEntries.get(false);

    // Build the new list of entries by keeping all non-dex entries unchanged and renaming the dex
    // entries.
    ImmutableList<ModuleEntry> newEntries =
        ImmutableList.<ModuleEntry>builder()
            .addAll(nonDexEntries)
            .addAll(
                dexEntries.stream()
                    .map(entry -> new RenamedDexEntry(entry))
                    .collect(toImmutableList()))
            .build();

    return module
        .toBuilder()
        .setEntryMap(newEntries.stream().collect(toImmutableMap(ModuleEntry::getPath, identity())))
        .build();
  }

  /** A ModuleEntry that has the same content as an other ModuleEntry but uses a different name. */
  @Immutable
  private static class RenamedDexEntry implements ModuleEntry {

    private final ModuleEntry moduleEntry;

    RenamedDexEntry(ModuleEntry moduleEntry) {
      this.moduleEntry = moduleEntry;
    }

    @MustBeClosed
    @Override
    public InputStream getContent() {
      return moduleEntry.getContent();
    }

    @Override
    public ZipPath getPath() {
      return incrementClassesDexNumber(moduleEntry.getPath());
    }

    @Override
    public boolean isDirectory() {
      return moduleEntry.isDirectory();
    }

    @Override
    public boolean shouldCompress() {
      return moduleEntry.shouldCompress();
    }

    @Override
    public ModuleEntry setCompression(boolean shouldCompress) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ModuleEntry)) {
        return false;
      }
      return ModuleEntry.equal(this, (ModuleEntry) obj);
    }

    @Override
    public int hashCode() {
      return moduleEntry.hashCode();
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
    private ZipPath incrementClassesDexNumber(ZipPath entryPath) {
      String fileName = entryPath.toString();

      Matcher matcher = CLASSES_DEX_REGEX_PATTERN.matcher(fileName);
      checkState(matcher.matches());

      String num = matcher.group(1);
      if (num.isEmpty()) {
        return entryPath; // dex/classes.dex
      }
      return ZipPath.create("dex/classes" + (Integer.parseInt(num) + 1) + ".dex");
    }
  }
}

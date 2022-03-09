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
package com.android.tools.build.bundletool.io;

import static com.google.common.base.Preconditions.checkArgument;

import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.zipflinger.Entry;
import com.android.zipflinger.ZipArchive;
import com.android.zipflinger.ZipMap;
import com.android.zipflinger.ZipSource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Set of module entries whose content is stored inside zip archive.
 *
 * <p>Inside zip archive module entries are stored with names that were assigned by {@link
 * ModuleEntriesPacker}.
 */
class ModuleEntriesPack {

  /**
   * Prefixes of entry names that are in use by this pack.
   *
   * <p>Each entry name in zip archive has the following format: {prefix}{generatedPart}.
   *
   * <p>Two packs are mergeable if they use different name prefixes.
   */
  private final ImmutableSet<String> namePrefixes;

  /** Zip archive that stores content of module entries. */
  private final ZipMap zipMap;

  /**
   * Map that stores mappings between ModuleEntry and zip entry name.
   *
   * <p>This map should be read-only in this class, it uses mutable IdentityHashMap because there is
   * no immutable analogue for it.
   */
  private final IdentityHashMap<ModuleEntry, String> entryNameByModuleEntry;

  ModuleEntriesPack(
      ImmutableSet<String> namePrefixes,
      ZipMap zipMap,
      IdentityHashMap<ModuleEntry, String> entryNameByModuleEntry) {
    this.namePrefixes = namePrefixes;
    this.zipMap = zipMap;
    this.entryNameByModuleEntry = entryNameByModuleEntry;
  }

  Entry getZipEntry(ModuleEntry entry) {
    checkArgument(
        entryNameByModuleEntry.containsKey(entry),
        "Module entry %s is not available in pack",
        entry);
    return zipMap.getEntries().get(entryNameByModuleEntry.get(entry));
  }

  boolean hasEntry(ModuleEntry entry) {
    return entryNameByModuleEntry.containsKey(entry);
  }

  /**
   * Selects module entries as a {@link ZipSource} which next can be added into a new {@link
   * ZipArchive}.
   *
   * <p>Requires to provide {@code nameFunction} which assigns final names for each {@link
   * ModuleEntry}.
   */
  ZipSource select(
      ImmutableList<ModuleEntry> moduleEntries, Function<ModuleEntry, String> nameFunction) {
    return select(moduleEntries, nameFunction, /* alignmentFunction= */ entry -> 0L);
  }

  /**
   * Selects module entries as a {@link ZipSource} which next can be added into a new {@link
   * ZipArchive}.
   *
   * <p>Requires to provide {@code nameFunction} which assigns final names for each {@link
   * ModuleEntry} and {@code alignmentFunction} which assigns alignment in the final {@link
   * ZipSource}.
   */
  ZipSource select(
      ImmutableList<ModuleEntry> moduleEntries,
      Function<ModuleEntry, String> nameFunction,
      ToLongFunction<ModuleEntry> alignmentFunction) {
    ZipSource source = new ZipSource(zipMap);
    for (ModuleEntry entry : moduleEntries) {
      checkArgument(
          entryNameByModuleEntry.containsKey(entry),
          "Module entry %s is not available in the pack.",
          entry);
      source.select(
          entryNameByModuleEntry.get(entry),
          nameFunction.apply(entry),
          ZipSource.COMPRESSION_NO_CHANGE,
          alignmentFunction.applyAsLong(entry));
    }
    return source;
  }

  /**
   * Merges two packs into a single one.
   *
   * <p>Prefers to merge smaller pack into bigger one. Removes zip file of unused pack.
   */
  public ModuleEntriesPack mergeWith(ModuleEntriesPack anotherPack) {
    checkArgument(
        Collections.disjoint(namePrefixes, anotherPack.namePrefixes),
        "Both packs contain the same name prefix.");

    try {
      long thisSize = Files.size(zipMap.getPath());
      long anotherSize = Files.size(anotherPack.zipMap.getPath());
      ModuleEntriesPack to = thisSize > anotherSize ? this : anotherPack;
      ModuleEntriesPack from = thisSize > anotherSize ? anotherPack : this;
      try (ZipArchive archive = new ZipArchive(to.zipMap.getPath())) {
        archive.add(ZipSource.selectAll(from.zipMap.getPath()));
      }
      Files.delete(from.zipMap.getPath());
      IdentityHashMap<ModuleEntry, String> mergedNames =
          Streams.concat(
                  entryNameByModuleEntry.entrySet().stream(),
                  anotherPack.entryNameByModuleEntry.entrySet().stream())
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      Map.Entry::getValue,
                      (a, b) -> b,
                      Maps::newIdentityHashMap));
      return new ModuleEntriesPack(
          Sets.union(to.namePrefixes, from.namePrefixes).immutableCopy(),
          ZipMap.from(to.zipMap.getPath()),
          mergedNames);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

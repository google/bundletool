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

import com.android.tools.build.bundletool.model.ModuleEntry;
import com.android.tools.build.bundletool.model.ModuleEntry.ModuleEntryLocationInZipSource;
import com.android.zipflinger.ZipMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

/** Class to build {@link ModuleEntriesPack}. */
class ModuleEntriesPacker {
  private final String namePrefix;
  private final Path outputZip;
  private final IdentityHashMap<ModuleEntry, String> entryNameByModuleEntry;
  private final Map<String, ByteSource> contentByEntryName;
  private final Map<ModuleEntryLocationInZipSource, String> assignedEntryNameByBundleLocation;

  private final NameAssigner nameAssigner;

  public ModuleEntriesPacker(Path outputZip, String namePrefix) {
    this.namePrefix = namePrefix;
    this.outputZip = outputZip;
    nameAssigner = new NameAssigner(namePrefix);
    entryNameByModuleEntry = Maps.newIdentityHashMap();
    contentByEntryName = Maps.newHashMap();
    assignedEntryNameByBundleLocation = Maps.newHashMap();
  }

  /**
   * Adds {@link ModuleEntry} to the pack.
   *
   * <p>At this point entry is assigned a name and is added into a list of entries to be packed.
   */
  ModuleEntriesPacker add(ModuleEntry entry) {
    if (entryNameByModuleEntry.containsKey(entry)) {
      return this;
    }
    String entryName =
        entry.getFileLocation().isPresent()
            ? assignedByBundleLocation(entry)
            : nameAssigner.nextName();
    entryNameByModuleEntry.put(entry, entryName);
    contentByEntryName.putIfAbsent(entryName, entry.getContent());
    return this;
  }

  /**
   * Packs all entries which were previously added with provided {@link Zipper} and returns {@link
   * ModuleEntriesPack} pointing to this pack.
   */
  public ModuleEntriesPack pack(Zipper zipper) {
    try {
      zipper.zip(outputZip, ImmutableMap.copyOf(contentByEntryName));

      IdentityHashMap<ModuleEntry, String> copyOfEntryNameByModuleEntry = Maps.newIdentityHashMap();
      copyOfEntryNameByModuleEntry.putAll(entryNameByModuleEntry);
      return new ModuleEntriesPack(
          ImmutableSet.of(namePrefix), ZipMap.from(outputZip), copyOfEntryNameByModuleEntry);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String assignedByBundleLocation(ModuleEntry entry) {
    return assignedEntryNameByBundleLocation.computeIfAbsent(
        entry.getFileLocation().get(), (location) -> nameAssigner.nextName());
  }

  /** Helper class that allows to generate zip entry names. */
  private static class NameAssigner {
    private final String prefix;

    private int counter = 0;

    NameAssigner(String prefix) {
      this.prefix = prefix;
    }

    String nextName() {
      return prefix + String.valueOf(counter++);
    }
  }
}

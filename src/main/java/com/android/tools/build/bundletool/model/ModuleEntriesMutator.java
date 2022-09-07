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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.partitioningBy;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Mutator of the module entries that match a given predicate. */
public abstract class ModuleEntriesMutator {

  @CheckReturnValue
  public BundleModule applyMutation(BundleModule module) {
    if (!shouldApplyMutation(module)) {
      return module;
    }

    // Separate the matched entries from the other entries
    Map<Boolean, ImmutableList<ModuleEntry>> partitionedEntries =
        module.getEntries().stream().collect(partitioningBy(getFilter(), toImmutableList()));
    ImmutableList<ModuleEntry> matchedEntries = partitionedEntries.get(true);
    ImmutableList<ModuleEntry> otherEntries = partitionedEntries.get(false);

    // Build the new list of entries by keeping all non-matched entries unchanged and mutating the
    // matched entries.
    ImmutableList<ModuleEntry> newEntries =
        ImmutableList.<ModuleEntry>builder()
            .addAll(otherEntries)
            .addAll(getMutator().apply(matchedEntries))
            .build();

    return module.toBuilder()
        .setEntryMap(newEntries.stream().collect(toImmutableMap(ModuleEntry::getPath, identity())))
        .build();
  }

  /** Predicate that determines which entries will the mutation be applied to. */
  public abstract Predicate<ModuleEntry> getFilter();

  /** Mutation that will be applied to the entries matching {@link #getFilter()}. */
  public abstract Function<ImmutableList<ModuleEntry>, ImmutableList<ModuleEntry>> getMutator();

  /** Whether the mutation should be applied to the given module or not. */
  public abstract boolean shouldApplyMutation(BundleModule module);
}

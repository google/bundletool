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

import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingByDeterministic;
import static com.android.tools.build.bundletool.model.utils.CollectorUtils.groupingBySortedKeys;

import com.android.tools.build.bundletool.model.ModuleSplit.SplitType;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/** Represents generated APKs and groups them into standalone, split, and instant ones. */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class GeneratedApks {

  public abstract ImmutableList<ModuleSplit> getInstantApks();

  public abstract ImmutableList<ModuleSplit> getSplitApks();

  public abstract ImmutableList<ModuleSplit> getStandaloneApks();

  public abstract ImmutableList<ModuleSplit> getSystemApks();

  // There is alsways a single archived APK. List type is used for consistency.
  public abstract ImmutableList<ModuleSplit> getArchivedApks();

  public int size() {
    return getInstantApks().size()
        + getSplitApks().size()
        + getStandaloneApks().size()
        + getSystemApks().size()
        + getArchivedApks().size();
  }

  public Stream<ModuleSplit> getAllApksStream() {
    return Stream.of(
            getStandaloneApks(),
            getInstantApks(),
            getSplitApks(),
            getSystemApks(),
            getArchivedApks())
        .flatMap(List::stream);
  }

  /** Returns all apks grouped and ordered by {@link VariantKey}. */
  public ImmutableListMultimap<VariantKey, ModuleSplit> getAllApksGroupedByOrderedVariants() {
    return getAllApksStream()
        .collect(groupingBySortedKeys(VariantKey::create, Function.identity()));
  }

  public static Builder builder() {
    return new AutoValue_GeneratedApks.Builder()
        .setInstantApks(ImmutableList.of())
        .setSplitApks(ImmutableList.of())
        .setStandaloneApks(ImmutableList.of())
        .setSystemApks(ImmutableList.of())
        .setArchivedApks(ImmutableList.of());
  }

  /** Creates a GeneratedApk instance from a list of module splits. */
  public static GeneratedApks fromModuleSplits(ImmutableList<ModuleSplit> moduleSplits) {
    ImmutableMap<SplitType, ImmutableList<ModuleSplit>> groups =
        moduleSplits.stream().collect(groupingByDeterministic(ModuleSplit::getSplitType));
    return builder()
        .setInstantApks(groups.getOrDefault(SplitType.INSTANT, ImmutableList.of()))
        .setSplitApks(groups.getOrDefault(SplitType.SPLIT, ImmutableList.of()))
        .setStandaloneApks(groups.getOrDefault(SplitType.STANDALONE, ImmutableList.of()))
        .setSystemApks(groups.getOrDefault(SplitType.SYSTEM, ImmutableList.of()))
        .setArchivedApks(groups.getOrDefault(SplitType.ARCHIVE, ImmutableList.of()))
        .build();
  }

  /** Builder for {@link GeneratedApks}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setInstantApks(ImmutableList<ModuleSplit> instantApks);

    public abstract Builder setSplitApks(ImmutableList<ModuleSplit> splitApks);

    public abstract Builder setStandaloneApks(ImmutableList<ModuleSplit> standaloneApks);

    public abstract Builder setSystemApks(ImmutableList<ModuleSplit> systemApks);

    public abstract Builder setArchivedApks(ImmutableList<ModuleSplit> archivedApks);

    public abstract GeneratedApks build();
  }
}

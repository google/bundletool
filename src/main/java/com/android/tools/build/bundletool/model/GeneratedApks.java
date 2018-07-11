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

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/** Represents generated APKs and groups them into standalone and split ones. */
@AutoValue
public abstract class GeneratedApks {

  public abstract ImmutableList<ModuleSplit> getSplitApks();

  public abstract ImmutableList<ModuleSplit> getStandaloneApks();

  public int size() {
    return getSplitApks().size() + getStandaloneApks().size();
  }

  public static Builder builder() {
    return new AutoValue_GeneratedApks.Builder()
        .setSplitApks(ImmutableList.of())
        .setStandaloneApks(ImmutableList.of());
  }

  public static GeneratedApks fromModuleSplits(ImmutableList<ModuleSplit> moduleSplits) {
    return builder()
        .setSplitApks(
            moduleSplits.stream().filter(not(ModuleSplit::isStandalone)).collect(toImmutableList()))
        .setStandaloneApks(
            moduleSplits.stream().filter(ModuleSplit::isStandalone).collect(toImmutableList()))
        .build();
  }
  /** Builder for {@link GeneratedApks}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract GeneratedApks.Builder setSplitApks(ImmutableList<ModuleSplit> splitApks);

    public abstract GeneratedApks.Builder setStandaloneApks(
        ImmutableList<ModuleSplit> standaloneApks);

    public abstract GeneratedApks build();
  }
}

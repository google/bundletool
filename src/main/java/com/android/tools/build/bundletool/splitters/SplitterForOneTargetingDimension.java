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

package com.android.tools.build.bundletool.splitters;

import com.android.bundle.Targeting.ApkTargeting;
import com.android.tools.build.bundletool.model.ModuleSplit;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

/**
 * Superclass for {@link ModuleSplitSplitter} that restricts the output splits to target only one
 * dimension.
 */
public abstract class SplitterForOneTargetingDimension implements ModuleSplitSplitter {

  @Override
  public ImmutableCollection<ModuleSplit> split(ModuleSplit split) {
    if (!split.getApkTargeting().equals(ApkTargeting.getDefaultInstance())) {
      return ImmutableList.of(split);
    }
    return splitInternal(split);
  }

  public abstract ImmutableCollection<ModuleSplit> splitInternal(ModuleSplit split);
}
